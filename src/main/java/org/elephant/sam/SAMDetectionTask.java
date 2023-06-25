package org.elephant.sam;

import com.google.gson.Gson;
import javafx.concurrent.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.awt.common.AwtTools;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.io.GsonTools;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.regions.ImagePlane;
import qupath.lib.regions.ImageRegion;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.RectangleROI;
import qupath.lib.roi.interfaces.ROI;

import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * A task to perform SAM detection on a given image.
 * <p>
 * This task is designed to be run in a background thread, and will return a list of PathObjects
 * representing the detected objects.
 * <p>
 * The task will also add the detected objects to the hierarchy, and update the viewer to show the
 * detected objects.
 */
public class SAMDetectionTask extends Task<List<PathObject>> {

    private static final Logger logger = LoggerFactory.getLogger(SAMDetectionTask.class);

    private final ImageData<BufferedImage> imageData;
    private ImageServer<BufferedImage> renderedServer;

    /**
     * The field of view visible within the viewer at the time the detection task was created.
     */
    private RegionRequest viewerRegion;

    private double padScale = 2.0;

    private final List<PathObject> foregroundObjects;
    private final List<PathObject> backgroundObjects;

    private final boolean setRandomColor;
    private final boolean setName;
    private final boolean keepPromptObjects;
    private final SAMOutput outputType;

    private final String serverURL;

    private final SAMModel model;

    private SAMDetectionTask(Builder builder) {
        this.serverURL = builder.serverURL;
        Objects.requireNonNull(serverURL, "Server must not be null!");

        this.model = builder.model;
        Objects.requireNonNull(model, "Model must not be null!");

        QuPathViewer viewer = builder.viewer;
        Objects.requireNonNull(builder, "Viewer must not be null!");

        this.imageData = viewer.getImageData();
        Objects.requireNonNull(imageData, "ImageData must not be null!");
        if (builder.server != null) {
            if (builder.server.isRGB())
                this.renderedServer = builder.server;
            else
                logger.warn("Cannot use non-RGB image server for detection!");
        }
        if (this.renderedServer == null) {
            this.renderedServer = Utils.createRenderedServer(viewer);
        }

        // Find the region and downsample currently used within the viewer
        ImageRegion region = AwtTools.getImageRegion(viewer.getDisplayedRegionShape(), viewer.getZPosition(), viewer.getTPosition());
        this.viewerRegion = RegionRequest.createInstance(renderedServer.getPath(), viewer.getDownsampleFactor(), region);
        this.viewerRegion = viewerRegion.intersect2D(0, 0, renderedServer.getWidth(), renderedServer.getHeight());

        this.foregroundObjects = new ArrayList<>(builder.foregroundObjects);
        this.backgroundObjects = new ArrayList<>(builder.backgroundObjects);

        this.keepPromptObjects = builder.keepPromptObjects;
        this.outputType = builder.outputType;
        this.setName = builder.setName;
        this.setRandomColor = builder.setRandomColor;
    }

    @Override
    protected List<PathObject> call() throws Exception {
        try {
            List<PathObject> detected = detectObjects();
            if (!detected.isEmpty()) {
                PathObjectHierarchy hierarchy = imageData.getHierarchy();
                if (!keepPromptObjects) {
                    // Remove prompt objects in one step
                    hierarchy.getSelectionModel().clearSelection();
                    hierarchy.removeObjects(foregroundObjects, true);
//                    List<PathObject> toRemove = new ArrayList<>(foregroundObjects);
//                    toRemove.addAll(backgroundObjects);
//                    hierarchy.getSelectionModel().clearSelection();
//                    hierarchy.removeObjects(toRemove, true);
                }
                hierarchy.addObjects(detected);
                hierarchy.getSelectionModel().setSelectedObjects(detected, detected.get(0));
            } else {
                logger.warn("No objects detected");
            }
            return detected;
        } catch (InterruptedException e) {
            logger.warn("Interrupted while detecting objects", e);
            return Collections.emptyList();
        }
    }


    private List<PathObject> detectObjects() throws InterruptedException, IOException {
        List<PathObject> detected = new ArrayList<>();
        for (PathObject foreground : foregroundObjects) {
            if (isCancelled())
                break;
            detected.addAll(detectObjects(foreground, backgroundObjects));
        }
        return detected;
    }


    private List<PathObject> detectObjects(PathObject foregroundObject, List<? extends PathObject> backgroundObjects)
            throws InterruptedException, IOException {

        SAMPrompt.Builder promptBuilder = SAMPrompt.builder(model)
                .multimaskOutput(outputType != SAMOutput.SINGLE_MASK);

        // Determine which part of the image we need & set foreground prompts
        ROI roi = foregroundObject.getROI();
        RegionRequest regionRequest;
        BufferedImage img;
        double downsample = this.viewerRegion.getDownsample();
        if (roi instanceof RectangleROI) {
            // For rectangular prompts, add some extra context from nearby
            RegionRequest roiRegion = RegionRequest.createInstance(renderedServer.getPath(), downsample, roi);

            regionRequest = roiRegion;
            double minPadding = 128 / downsample; // We need to handle tiny input prompts (including single pixels)
            if (padScale > 0.0)
                regionRequest = regionRequest.pad2D(
                        (int)Math.max(minPadding, Math.round(roiRegion.getWidth() * padScale)),
                        (int)Math.max(minPadding, Math.round(roiRegion.getHeight() * padScale)));
            regionRequest = regionRequest.intersect2D(0, 0, renderedServer.getWidth(), renderedServer.getHeight());
            img = renderedServer.readRegion(regionRequest);

            promptBuilder = promptBuilder.bbox(
                    (int)((roiRegion.getMinX() - regionRequest.getMinX()) / downsample),
                    (int)((roiRegion.getMinY() - regionRequest.getMinY()) / downsample),
                    (int)Math.round((roiRegion.getMaxX() - regionRequest.getMinX()) / downsample),
                    (int)Math.round((roiRegion.getMaxY() - regionRequest.getMinY()) / downsample)
            );
        } else {
            // For point prompts (including line vertices), use the current viewer region
            regionRequest = this.viewerRegion;
            img = renderedServer.readRegion(regionRequest);
            promptBuilder = promptBuilder.addToForeground(
                    Utils.getCoordinates(roi, regionRequest, img.getWidth(), img.getHeight())
            );
        }

        // Add any background prompts
        for (PathObject background : backgroundObjects) {
            promptBuilder = promptBuilder.addToBackground(
                    Utils.getCoordinates(background.getROI(), regionRequest, img.getWidth(), img.getHeight())
            );
        }

        final SAMPrompt prompt = promptBuilder
                .b64img(Utils.base64EncodePNG(img))
                .build();

        if (isCancelled())
            return Collections.emptyList();

        HttpResponse<String> response = sendRequest(serverURL, prompt);

        if (isCancelled())
            return Collections.emptyList();

        if (response.statusCode() == HttpURLConnection.HTTP_OK) {
            return parseResponse(response, regionRequest, foregroundObject.getPathClass());
        } else {
            logger.error("HTTP response: {}, {}", response.statusCode(), response.body());
            return Collections.emptyList();
        }
    }

    private static HttpResponse<String> sendRequest(String serverURL, SAMPrompt prompt) throws IOException, InterruptedException {
        final Gson gson = GsonTools.getInstance();
        final String body = gson.toJson(prompt);
        final HttpRequest request = HttpRequest.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .uri(URI.create(serverURL))
                .header("accept", "application/json")
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpClient client = HttpClient.newHttpClient();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private List<PathObject> parseResponse(HttpResponse<String> response, RegionRequest regionRequest, PathClass pathClass) {
        List<PathObject> samObjects = Utils.parsePathObjects(response.body());
        AffineTransform transform = new AffineTransform();
        transform.translate(regionRequest.getMinX(), regionRequest.getMinY());
        transform.scale(regionRequest.getDownsample(), regionRequest.getDownsample());
        ImagePlane plane = regionRequest.getImagePlane();
        // Retain the original classification, and set names/colors if required
        List<PathObject> updatedObjects = new ArrayList<>();
        for (PathObject pathObject : samObjects) {
            pathObject = Utils.applyTransformAndClassification(pathObject, transform, pathClass, plane);
            if (setName)
                Utils.setNameForSAM(pathObject);
            if (setRandomColor && pathObject.getPathClass() == null)
                Utils.setRandomColor(pathObject);
            updatedObjects.add(pathObject);
        }
        return Utils.selectByOutputType(updatedObjects, outputType);
    }




    /**
     * New builder for a SAM detection class.
     * @param viewer the viewer containing the image to be processed
     * @return the builder
     */
    public static Builder builder(QuPathViewer viewer) {
        return new Builder(viewer);
    }

    static class Builder {

        private QuPathViewer viewer;

        private Collection<PathObject> foregroundObjects = new LinkedHashSet<>();
        private Collection<PathObject> backgroundObjects = new LinkedHashSet<>();

        private ImageServer<BufferedImage> server;

        private String serverURL;
        private SAMModel model = SAMModel.VIT_L;
        private boolean keepPromptObjects = false;
        private SAMOutput outputType = SAMOutput.SINGLE_MASK;
        private boolean setRandomColor = true;
        private boolean setName = true;

        private Builder(QuPathViewer viewer) {
            this.viewer = viewer;
        }

        /**
         * Specify the server URL (required).
         * @param serverURL
         * @return this builder
         */
        public Builder serverURL(final String serverURL) {
            this.serverURL = serverURL;
            return this;
        }

        /**
         * Specify the SAM model to use.
         * Default is SAMModel.VIT_L.
         * @param model
         * @return this builder
         */
        public Builder model(final SAMModel model) {
            this.model = model;
            return this;
        }

        /**
         * Add objects representing foreground prompts.
         * Each will be treated as a separate prompt.
         * @param foregroundObjects
         * @return this builder
         */
        public Builder addForegroundPrompts(final Collection<? extends PathObject> foregroundObjects) {
            this.foregroundObjects.addAll(foregroundObjects);
            return this;
        }

        /**
         * Add objects representing background prompts.
         * Background prompts are use with all foreground prompts.
         * @param backgroundObjects
         * @return this builder
         */
        public Builder addBackgroundPrompts(final Collection<? extends PathObject> backgroundObjects) {
            this.backgroundObjects.addAll(backgroundObjects);
            return this;
        }

        /**
         * Optionally retain prompt objects after detection.
         * Default is false.
         * @param keepPromptObjects
         * @return this builder
         */
        public Builder keepPromptObjects(final boolean keepPromptObjects) {
            this.keepPromptObjects = keepPromptObjects;
            return this;
        }

        /**
         * Optionally request the output type.
         * Default is SAMOutput.SINGLE_MASK.
         * @param outputType
         * @return this builder
         */
        public Builder outputType(final SAMOutput outputType) {
            this.outputType = outputType;
            return this;
        }

        /**
         * Optionally specify a server to provide the pixels.
         * This should be an RGB server. Otherwise, a rendered server will be created from the viewer.
         * @param server
         * @return this builder
         */
        public Builder server(final ImageServer<BufferedImage> server) {
            this.server = server;
            return this;
        }

        /**
         * Assign a random color to each unclassified object created.
         * Classified objects are not assigned a color, since their coloring comes from the classification.
         * @param setRandomColor
         * @return this builder
         */
        public Builder setRandomColor(final boolean setRandomColor) {
            this.setRandomColor = setRandomColor;
            return this;
        }

        /**
         * Set the name of each object that was created, to distinguish it as being from
         * SAM and to include the quality score.
         * @param setName
         * @return this builder
         */
        public Builder setName(final boolean setName) {
            this.setName = setName;
            return this;
        }

        /**
         * Build the detection task.
         * @return
         */
        public SAMDetectionTask build() {
            return new SAMDetectionTask(this);
        }

    }

}
