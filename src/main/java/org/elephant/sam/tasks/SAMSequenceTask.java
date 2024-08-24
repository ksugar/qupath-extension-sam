package org.elephant.sam.tasks;

import com.google.gson.Gson;
import javafx.concurrent.Task;

import org.elephant.sam.Utils;
import org.elephant.sam.entities.SAMType;
import org.elephant.sam.entities.SAMOutput;
import org.elephant.sam.entities.SAMPromptMode;
import org.elephant.sam.parameters.SAM2VideoPromptObject;
import org.elephant.sam.parameters.SAM2VideoPromptParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.awt.common.AwtTools;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.io.GsonTools;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.regions.ImageRegion;
import qupath.lib.regions.RegionRequest;

import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A task to perform SAM video on a given sequence of images.
 * <p>
 * This task is designed to be run in a background thread, and will return a list of PathObjects representing the
 * detected objects.
 * <p>
 * The task will also add the detected objects to the hierarchy, and update the viewer to show the detected objects.
 */
public class SAMSequenceTask extends Task<List<PathObject>> {

    private static final Logger logger = LoggerFactory.getLogger(SAMSequenceTask.class);

    private final ImageData<BufferedImage> imageData;
    private ImageServer<BufferedImage> renderedServer;

    /**
     * The field of view visible within the viewer at the time the detection task
     * was created.
     */
    private final List<RegionRequest> viewerRegions = new ArrayList<>();

    private final Map<Integer, List<SAM2VideoPromptObject>> objs;

    private final boolean setRandomColor;
    private final boolean setName;

    private final String serverURL;

    private final SAMType model;

    private final SAMPromptMode promptMode;

    private final String checkpointUrl;

    private final int planePosition;

    private SAMSequenceTask(Builder builder) {
        this.serverURL = builder.serverURL;
        Objects.requireNonNull(serverURL, "Server must not be null!");

        this.model = builder.model;
        Objects.requireNonNull(model, "Model must not be null!");

        this.promptMode = builder.promptMode;
        Objects.requireNonNull(promptMode, "Prompt mode must not be null!");

        QuPathViewer viewer = builder.viewer;
        Objects.requireNonNull(viewer, "Viewer must not be null!");

        this.imageData = viewer.getImageData();
        Objects.requireNonNull(imageData, "ImageData must not be null!");
        if (builder.server != null) {
            if (builder.server.isRGB())
                this.renderedServer = builder.server;
            else
                logger.warn("Cannot use non-RGB image server for detection!");
        }
        if (this.renderedServer == null) {
            try {
                this.renderedServer = Utils.createRenderedServer(viewer);
            } catch (IOException e) {
                logger.error("Failed to create rendered server", e);
            }
        }

        // Find the region and downsample currently used within the viewer
        if (promptMode == SAMPromptMode.XYZ) {
            for (int z = 0; z < viewer.getServer().nZSlices(); z++) {
                ImageRegion region = AwtTools.getImageRegion(viewer.getDisplayedRegionShape(), z,
                        viewer.getTPosition());
                RegionRequest viewerRegion = RegionRequest.createInstance(renderedServer.getPath(),
                        viewer.getDownsampleFactor(),
                        region);
                viewerRegion = viewerRegion.intersect2D(0, 0, renderedServer.getWidth(), renderedServer.getHeight());
                viewerRegions.add(viewerRegion);
            }
            planePosition = viewer.getTPosition();
        } else if (promptMode == SAMPromptMode.XYT) {
            for (int t = 0; t < viewer.getServer().nTimepoints(); t++) {
                ImageRegion region = AwtTools.getImageRegion(viewer.getDisplayedRegionShape(), viewer.getZPosition(),
                        t);
                RegionRequest viewerRegion = RegionRequest.createInstance(renderedServer.getPath(),
                        viewer.getDownsampleFactor(),
                        region);
                viewerRegion = viewerRegion.intersect2D(0, 0, renderedServer.getWidth(), renderedServer.getHeight());
                viewerRegions.add(viewerRegion);
            }
            planePosition = viewer.getZPosition();
        } else {
            throw new IllegalArgumentException("Unsupported prompt mode: " + promptMode);
        }

        this.objs = builder.objs;

        this.setName = builder.setName;
        this.setRandomColor = builder.setRandomColor;
        this.checkpointUrl = builder.checkpointUrl;
    }

    @Override
    protected List<PathObject> call() throws Exception {
        try {
            return detectObjects();
        } catch (InterruptedException e) {
            logger.warn("Interrupted while detecting objects", e);
            return Collections.emptyList();
        }
    }

    private List<PathObject> detectObjects()
            throws InterruptedException, IOException {

        final List<String> b64imgs = new ArrayList<>();
        for (RegionRequest viewerRegion : viewerRegions) {
            final BufferedImage img = renderedServer.readRegion(viewerRegion);
            b64imgs.add(Utils.base64EncodePNG(img));
        }

        final SAM2VideoPromptParameters prompt = SAM2VideoPromptParameters.builder(model)
                .objs(objs)
                .b64imgs(b64imgs)
                .axes(promptMode.toString())
                .planePosition(planePosition)
                .checkpointUrl(checkpointUrl)
                .build();

        if (isCancelled())
            return Collections.emptyList();

        HttpResponse<String> response = sendRequest(serverURL, prompt);

        if (isCancelled())
            return Collections.emptyList();

        if (response.statusCode() == HttpURLConnection.HTTP_OK) {
            return parseResponse(response, viewerRegions.get(0), null);
        } else {
            logger.error("HTTP response: {}, {}", response.statusCode(), response.body());
            return Collections.emptyList();
        }
    }

    private static HttpResponse<String> sendRequest(String serverURL, SAM2VideoPromptParameters prompt)
            throws IOException, InterruptedException {
        final Gson gson = GsonTools.getInstance();
        final String body = gson.toJson(prompt);
        final HttpRequest request = HttpRequest.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .uri(URI.create(String.format("%svideo/", serverURL)))
                .header("accept", "application/json")
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpClient client = HttpClient.newHttpClient();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private List<PathObject> parseResponse(HttpResponse<String> response, RegionRequest regionRequest,
            PathClass pathClass) {
        List<PathObject> samObjects = Utils.parsePathObjects(response.body());
        AffineTransform transform = new AffineTransform();
        transform.translate(regionRequest.getMinX(), regionRequest.getMinY());
        transform.scale(regionRequest.getDownsample(), regionRequest.getDownsample());
        // Retain the original classification, and set names/colors if required
        List<PathObject> updatedObjects = new ArrayList<>();
        for (PathObject pathObject : samObjects) {
            pathObject = Utils.applyTransformAndClassification(pathObject, transform, null, null);
            if (setName)
                Utils.setNameForSAM(pathObject);
            if (setRandomColor && pathObject.getPathClass() == null)
                Utils.setRandomColor(pathObject);
            updatedObjects.add(pathObject);
        }
        return Utils.selectByOutputType(updatedObjects, SAMOutput.SINGLE_MASK);
    }

    /**
     * New builder for a SAM sequence class.
     * 
     * @param viewer
     *            the viewer containing the image to be processed
     * @return the builder
     */
    public static Builder builder(QuPathViewer viewer) {
        return new Builder(viewer);
    }

    /**
     * Builder for a SAMSequenceTask class.
     */
    public static class Builder {

        private QuPathViewer viewer;

        private Map<Integer, List<SAM2VideoPromptObject>> objs;

        private ImageServer<BufferedImage> server;

        private String serverURL;
        private SAMType model = SAMType.VIT_L;
        private SAMPromptMode promptMode = SAMPromptMode.XYZ;
        private boolean setRandomColor = true;
        private boolean setName = true;
        private String checkpointUrl;

        private Builder(QuPathViewer viewer) {
            this.viewer = viewer;
        }

        /**
         * Specify the server URL (required).
         * 
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
         * 
         * @param model
         * @return this builder
         */
        public Builder model(final SAMType model) {
            this.model = model;
            return this;
        }

        /**
         * Specify the SAM prompt mode to use.
         * Default is SAMPromptMode.XYZ.
         * 
         * @param promptMode
         * @return
         */
        public Builder promptMode(final SAMPromptMode promptMode) {
            this.promptMode = promptMode;
            return this;
        }

        /**
         * Add objects representing foreground prompts.
         * Each will be treated as a separate prompt.
         * 
         * @param foregroundObjects
         * @return this builder
         */
        public Builder objs(final Map<Integer, List<SAM2VideoPromptObject>> objs) {
            this.objs = objs;
            return this;
        }

        /**
         * Optionally specify a server to provide the pixels.
         * This should be an RGB server. Otherwise, a rendered server will be created
         * from the viewer.
         * 
         * @param server
         * @return this builder
         */
        public Builder server(final ImageServer<BufferedImage> server) {
            this.server = server;
            return this;
        }

        /**
         * Assign a random color to each unclassified object created.
         * Classified objects are not assigned a color, since their coloring comes from
         * the classification.
         * 
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
         * 
         * @param setName
         * @return this builder
         */
        public Builder setName(final boolean setName) {
            this.setName = setName;
            return this;
        }

        /**
         * Specify the checkpoint URL.
         * 
         * @param checkpointUrl
         * @return this builder
         */
        public Builder checkpointUrl(final String checkpointUrl) {
            this.checkpointUrl = checkpointUrl;
            return this;
        }

        /**
         * Build the detection task.
         * 
         * @return
         */
        public SAMSequenceTask build() {
            return new SAMSequenceTask(this);
        }

    }

}