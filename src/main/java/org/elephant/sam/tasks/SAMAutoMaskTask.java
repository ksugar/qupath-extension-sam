package org.elephant.sam.tasks;

import com.google.gson.Gson;
import javafx.concurrent.Task;

import org.elephant.sam.Utils;
import org.elephant.sam.entities.SAMType;
import org.elephant.sam.entities.SAMOutput;
import org.elephant.sam.parameters.SAMAutoMaskParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.awt.common.AwtTools;
import qupath.lib.gui.prefs.PathPrefs;
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
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

/**
 * A task to perform SAM detection on a given image.
 * <p>
 * This task is designed to be run in a background thread, and will return a
 * list of PathObjects
 * representing the detected objects.
 * <p>
 * The task will also add the detected objects to the hierarchy, and update the
 * viewer to show the
 * detected objects.
 */
public class SAMAutoMaskTask extends Task<List<PathObject>> {

    private static final Logger logger = LoggerFactory.getLogger(SAMAutoMaskTask.class);

    private final ImageData<BufferedImage> imageData;
    private ImageServer<BufferedImage> renderedServer;

    /**
     * The field of view visible within the viewer at the time the detection task
     * was created.
     */
    private RegionRequest viewerRegion;

    private final boolean setRandomColor;
    private final boolean setName;
    private final boolean clearCurrentObjects;
    private final SAMOutput outputType;

    private final String serverURL;

    private final SAMType model;

    private final int pointsPerSide;

    private final int pointsPerBatch;

    private final double predIoUThresh;

    private final double stabilityScoreThresh;

    private final double stabilityScoreOffset;

    private final double boxNmsThresh;

    private final int cropNLayers;

    private final double cropNmsThresh;

    private final double cropOverlapRatio;

    private final int cropNPointsDownscaleFactor;

    private final int minMaskRegionArea;

    private final boolean includeImageEdge;

    private final String checkpointUrl;

    private SAMAutoMaskTask(Builder builder) {
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
                logger.warn("Cannot use non-RGB image server for SAM auto mask!");
        }
        if (this.renderedServer == null) {
            try {
                this.renderedServer = Utils.createRenderedServer(viewer);
            } catch (IOException e) {
                logger.error("Failed to create rendered server", e);
            }
        }

        // Find the region and downsample currently used within the viewer
        ImageRegion region = AwtTools.getImageRegion(viewer.getDisplayedRegionShape(), viewer.getZPosition(),
                viewer.getTPosition());
        this.viewerRegion = RegionRequest.createInstance(renderedServer.getPath(), viewer.getDownsampleFactor(),
                region);
        this.viewerRegion = viewerRegion.intersect2D(0, 0, renderedServer.getWidth(), renderedServer.getHeight());

        this.outputType = builder.outputType;
        this.setName = builder.setName;
        this.clearCurrentObjects = builder.clearCurrentObjects;
        this.setRandomColor = builder.setRandomColor;
        this.pointsPerSide = builder.pointsPerSide;
        this.predIoUThresh = builder.predIoUThresh;
        this.stabilityScoreThresh = builder.stabilityScoreThresh;
        this.stabilityScoreOffset = builder.stabilityScoreOffset;
        this.boxNmsThresh = builder.boxNmsThresh;
        this.pointsPerBatch = builder.pointsPerBatch;
        this.cropNLayers = builder.cropNLayers;
        this.cropNmsThresh = builder.cropNmsThresh;
        this.cropOverlapRatio = builder.cropOverlapRatio;
        this.cropNPointsDownscaleFactor = builder.cropNPointsDownscaleFactor;
        this.minMaskRegionArea = builder.minMaskRegionArea;
        this.includeImageEdge = builder.includeImageEdge;
        this.checkpointUrl = builder.checkpointUrl;
    }

    @Override
    protected List<PathObject> call() throws Exception {
        try {
            List<PathObject> detected = detectObjects();
            if (!detected.isEmpty()) {
                PathObjectHierarchy hierarchy = imageData.getHierarchy();
                if (clearCurrentObjects)
                    hierarchy.clearAll();
                hierarchy.addObjects(detected);
                hierarchy.getSelectionModel().clearSelection();
            } else {
                logger.warn("No objects detected");
            }
            return detected;
        } catch (InterruptedException e) {
            logger.warn("Interrupted while detecting objects", e);
            return Collections.emptyList();
        }
    }

    private List<PathObject> detectObjects()
            throws InterruptedException, IOException {

        SAMAutoMaskParameters.Builder parametersBuilder = SAMAutoMaskParameters.builder(model);

        // For SAM auto mask, use the current viewer region
        RegionRequest regionRequest = this.viewerRegion;
        BufferedImage img = renderedServer.readRegion(regionRequest);

        final SAMAutoMaskParameters parameters = parametersBuilder
                .b64img(Utils.base64EncodePNG(img))
                .pointsPerSide(pointsPerSide)
                .pointsPerBatch(pointsPerBatch)
                .predIoUThresh(predIoUThresh)
                .stabilityScoreThresh(stabilityScoreThresh)
                .stabilityScoreOffset(stabilityScoreOffset)
                .boxNmsThresh(boxNmsThresh)
                .cropNLayers(cropNLayers)
                .cropNmsThresh(cropNmsThresh)
                .cropOverlapRatio(cropOverlapRatio)
                .cropNPointsDownscaleFactor(cropNPointsDownscaleFactor)
                .minMaskRegionArea(minMaskRegionArea)
                .outputType(outputType.toString())
                .includeImageEdge(includeImageEdge)
                .checkpointUrl(checkpointUrl)
                .build();

        if (isCancelled())
            return Collections.emptyList();

        HttpResponse<String> response = sendRequest(serverURL, parameters);

        if (isCancelled())
            return Collections.emptyList();

        if (response.statusCode() == HttpURLConnection.HTTP_OK) {
            return parseResponse(response, regionRequest, PathPrefs.autoSetAnnotationClassProperty().get());
        } else {
            logger.error("HTTP response: {}, {}", response.statusCode(), response.body());
            return Collections.emptyList();
        }
    }

    private static HttpResponse<String> sendRequest(String serverURL, SAMAutoMaskParameters parameters)
            throws IOException, InterruptedException {
        final Gson gson = GsonTools.getInstance();
        final String body = gson.toJson(parameters);
        final HttpRequest request = HttpRequest.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .uri(URI.create(String.format("%sautomask/", serverURL)))
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
        return updatedObjects;
    }

    /**
     * New builder for a SAM detection class.
     * 
     * @param viewer
     *            the viewer containing the image to be processed
     * @return the builder
     */
    public static Builder builder(QuPathViewer viewer) {
        return new Builder(viewer);
    }

    /**
     * Builder for a SAMAutoMaskTask class.
     */
    public static class Builder {

        private QuPathViewer viewer;

        private ImageServer<BufferedImage> server;

        private String serverURL;
        private SAMType model = SAMType.VIT_L;
        private SAMOutput outputType = SAMOutput.SINGLE_MASK;
        private boolean setRandomColor = true;
        private boolean setName = true;
        private boolean clearCurrentObjects = true;
        private int pointsPerSide = 32;
        private int pointsPerBatch = 64;
        private double predIoUThresh = 0.88;
        private double stabilityScoreThresh = 0.95;
        private double stabilityScoreOffset = 1.0;
        private double boxNmsThresh = 0.7;
        private int cropNLayers = 0;
        private double cropNmsThresh = 0.7;
        private double cropOverlapRatio = (double) 512 / 1500;
        private int cropNPointsDownscaleFactor = 1;
        private int minMaskRegionArea = 0;
        private boolean includeImageEdge = false;
        private String checkpointUrl = null;

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
         * Optionally request the output type.
         * Default is SAMOutput.SINGLE_MASK.
         * 
         * @param outputType
         * @return this builder
         */
        public Builder outputType(final SAMOutput outputType) {
            this.outputType = outputType;
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
         * Clear current objects after running SAM auto mask.
         * 
         * @param clearCurrentObjects
         * @return this builder
         */
        public Builder clearCurrentObjects(final boolean clearCurrentObjects) {
            this.clearCurrentObjects = clearCurrentObjects;
            return this;
        }

        /**
         * The number of points to be sampled along one side of the image.
         * The total number of points is points_per_side**2.
         * 
         * @param pointsPerSide
         * @return this builder
         */
        public Builder pointsPerSide(final int pointsPerSide) {
            this.pointsPerSide = pointsPerSide;
            return this;
        }

        /**
         * Sets the number of points run simultaneously by the model.
         * Higher numbers may be faster but use more GPU memory.
         * 
         * @param pointsPerBatch
         * @return this builder
         */
        public Builder pointsPerBatch(final int pointsPerBatch) {
            this.pointsPerBatch = pointsPerBatch;
            return this;
        }

        /**
         * A filtering threshold in [0,1], using the model's predicted mask quality.
         * 
         * @param predIoUThresh
         * @return this builder
         */
        public Builder predIoUThresh(final double predIoUThresh) {
            this.predIoUThresh = predIoUThresh;
            return this;
        }

        /**
         * A filtering threshold in [0,1], using the stability of the mask under changes
         * to the cutoff
         * used to binarize the model's mask predictions.
         * 
         * @param stabilityScoreThresh
         * @return this builder
         */
        public Builder stabilityScoreThresh(final double stabilityScoreThresh) {
            this.stabilityScoreThresh = stabilityScoreThresh;
            return this;
        }

        /**
         * The amount to shift the cutoff when calculated the stability score.
         * 
         * @param stabilityScoreOffset
         * @return this builder
         */
        public Builder stabilityScoreOffset(final double stabilityScoreOffset) {
            this.stabilityScoreOffset = stabilityScoreOffset;
            return this;
        }

        /**
         * The box IoU cutoff used by non-maximal suppression to filter duplicate masks.
         * 
         * @param boxNmsThresh
         * @return this builder
         */
        public Builder boxNmsThresh(final double boxNmsThresh) {
            this.boxNmsThresh = boxNmsThresh;
            return this;
        }

        /**
         * If >0, mask prediction will be run again on crops of the image.
         * Sets the number of layers to run, where each layer has 2**i_layer number of
         * image crops.
         * 
         * @param cropNLayers
         * @return this builder
         */
        public Builder cropNLayers(final int cropNLayers) {
            this.cropNLayers = cropNLayers;
            return this;
        }

        /**
         * The box IoU cutoff used by non-maximal suppression to filter duplicate masks
         * between different crops.
         * 
         * @param cropNmsThresh
         * @return this builder
         */
        public Builder cropNmsThresh(final double cropNmsThresh) {
            this.cropNmsThresh = cropNmsThresh;
            return this;
        }

        /**
         * Sets the degree to which crops overlap. In the first crop layer,
         * crops will overlap by this fraction of the image length.
         * Later layers with more crops scale down this overlap.
         * 
         * @param cropOverlapRatio
         * @return this builder
         */
        public Builder cropOverlapRatio(final double cropOverlapRatio) {
            this.cropOverlapRatio = cropOverlapRatio;
            return this;
        }

        /**
         * The number of points-per-side sampled in layer n is scaled down by
         * crop_n_points_downscale_factor**n.
         * 
         * @param cropNPointsDownscaleFactor
         * @return this builder
         */
        public Builder cropNPointsDownscaleFactor(final int cropNPointsDownscaleFactor) {
            this.cropNPointsDownscaleFactor = cropNPointsDownscaleFactor;
            return this;
        }

        /**
         * If >0, postprocessing will be applied to remove disconnected regions and
         * holes in masks with area
         * smaller than min_mask_region_area. Requires opencv.
         * 
         * @param minMaskRegionArea
         * @return this builder
         */
        public Builder minMaskRegionArea(final int minMaskRegionArea) {
            this.minMaskRegionArea = minMaskRegionArea;
            return this;
        }

        /**
         * If specified, include image edge in SAM auto mask generator.
         * 
         * @param includeImageEdge
         * @return this builder
         */
        public Builder includeImageEdge(final boolean includeImageEdge) {
            this.includeImageEdge = includeImageEdge;
            return this;
        }

        /**
         * If specified, use the specified checkpoint.
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
        public SAMAutoMaskTask build() {
            return new SAMAutoMaskTask(this);
        }

    }

}
