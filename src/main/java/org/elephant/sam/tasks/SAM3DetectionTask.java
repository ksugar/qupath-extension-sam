package org.elephant.sam.tasks;

import org.elephant.sam.Utils;
import org.elephant.sam.entities.SAMType;
import org.elephant.sam.http.HttpUtils;
import org.elephant.sam.entities.SAMOutput;
import org.elephant.sam.parameters.SAM3PromptParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.concurrent.Task;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.io.GsonTools;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.regions.ImagePlane;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.RectangleROI;
import qupath.lib.roi.interfaces.ROI;

import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.HttpURLConnection;
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
 * This task is designed to be run in a background thread, and will return a list of PathObjects representing the
 * detected objects.
 * <p>
 * The task will also add the detected objects to the hierarchy, and update the viewer to show the detected objects.
 */
public class SAM3DetectionTask extends Task<List<PathObject>> {

    private static final Logger logger = LoggerFactory.getLogger(SAM3DetectionTask.class);

    private final ImageData<BufferedImage> imageData;
    private ImageServer<BufferedImage> renderedServer;

    /**
     * In the GUI mode, the field of view visible within the viewer at the time the detection task
     * was created.
     */
    private RegionRequest regionRequest;

    private final String textPrompt;
    private final List<PathObject> positiveBboxes;
    private final List<PathObject> negativeBboxes;

    private final boolean setRandomColor;
    private final boolean setName;
    private final SAMOutput outputType;

    private final String serverURL;

    private final boolean verifySSL;

    private final SAMType model;

    private final String checkpointUrl;

    private final boolean resetPrompts;

    private final double confidenceThresh;

    private SAM3DetectionTask(Builder builder) {
        this.serverURL = builder.serverURL;
        Objects.requireNonNull(serverURL, "Server must not be null!");

        this.verifySSL = builder.verifySSL;
        Objects.requireNonNull(serverURL, "Verify SSL must not be null!");

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
            try {
                this.renderedServer = Utils.createRenderedServer(viewer);
            } catch (IOException e) {
                logger.error("Failed to create rendered server", e);
            }
        }

        this.regionRequest = builder.regionRequest;
        if (this.regionRequest == null) {
            this.regionRequest = Utils.getViewerRegion(viewer, renderedServer);
        }

        this.textPrompt = builder.textPrompt;
        this.positiveBboxes = new ArrayList<>(builder.positiveBboxes);
        this.negativeBboxes = new ArrayList<>(builder.negativeBboxes);

        this.outputType = builder.outputType;
        this.setName = builder.setName;
        this.setRandomColor = builder.setRandomColor;
        this.checkpointUrl = builder.checkpointUrl;
        this.resetPrompts = builder.resetPrompts;
        this.confidenceThresh = builder.confidenceThresh;
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

        SAM3PromptParameters.Builder promptBuilder = SAM3PromptParameters.builder(model)
                .checkpointUrl(checkpointUrl);
        double downsample = regionRequest.getDownsample();

        if (textPrompt != null && !textPrompt.isEmpty()) {
            promptBuilder = promptBuilder.textPrompt(textPrompt);
        }

        for (PathObject positiveBbox : positiveBboxes) {
            ROI roi = positiveBbox.getROI();
            if (!(roi instanceof RectangleROI)) {
                logger.warn("Only rectangular ROIs are supported for positive bboxes; skipping non-rectangular ROI");
                continue;
            }
            RegionRequest roiRegion = RegionRequest.createInstance(renderedServer.getPath(), downsample, roi);
            promptBuilder = promptBuilder.addPositiveBbox(
                    (int) ((roiRegion.getMinX() - regionRequest.getMinX()) / downsample),
                    (int) ((roiRegion.getMinY() - regionRequest.getMinY()) / downsample),
                    (int) Math.round((roiRegion.getMaxX() - roiRegion.getMinX()) / downsample),
                    (int) Math.round((roiRegion.getMaxY() - roiRegion.getMinY()) / downsample));
        }

        for (PathObject negativeBbox : negativeBboxes) {
            ROI roi = negativeBbox.getROI();
            if (!(roi instanceof RectangleROI)) {
                logger.warn("Only rectangular ROIs are supported for negative bboxes; skipping non-rectangular ROI");
                continue;
            }
            RegionRequest roiRegion = RegionRequest.createInstance(renderedServer.getPath(), downsample, roi);
            promptBuilder = promptBuilder.addNegativeBbox(
                    (int) ((roiRegion.getMinX() - regionRequest.getMinX()) / downsample),
                    (int) ((roiRegion.getMinY() - regionRequest.getMinY()) / downsample),
                    (int) Math.round((roiRegion.getMaxX() - roiRegion.getMinX()) / downsample),
                    (int) Math.round((roiRegion.getMaxY() - roiRegion.getMinY()) / downsample));
        }

        promptBuilder = promptBuilder.textPrompt(textPrompt)
                .resetPrompts(resetPrompts)
                .confidenceThresh(confidenceThresh);

        BufferedImage img = renderedServer.readRegion(regionRequest);

        final SAM3PromptParameters prompt = promptBuilder
                .b64img(Utils.base64EncodePNG(img))
                .build();

        if (isCancelled())
            return Collections.emptyList();

        final String endpointURL = String.format("%ssam3/", Utils.ensureTrailingSlash(serverURL));
        HttpResponse<String> response = HttpUtils.postRequest(endpointURL, verifySSL,
                GsonTools.getInstance().toJson(prompt));

        if (isCancelled())
            return Collections.emptyList();

        if (response.statusCode() == HttpURLConnection.HTTP_OK) {
            return parseResponse(response, regionRequest, PathClass.NULL_CLASS);
        } else {
            logger.error("HTTP response: {}, {}", response.statusCode(), response.body());
            return Collections.emptyList();
        }
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
        return Utils.selectByOutputType(updatedObjects, outputType);
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
     * Builder for a SAMDetectionTask class.
     */
    public static class Builder {

        private QuPathViewer viewer;

        private String textPrompt;
        private Collection<PathObject> positiveBboxes = new LinkedHashSet<>();
        private Collection<PathObject> negativeBboxes = new LinkedHashSet<>();

        private ImageServer<BufferedImage> server;
        private RegionRequest regionRequest;

        private String serverURL;
        private boolean verifySSL = false;
        private SAMType model = SAMType.VIT_L;
        private SAMOutput outputType = SAMOutput.SINGLE_MASK;
        private boolean setRandomColor = true;
        private boolean setName = true;
        private String checkpointUrl;
        private boolean resetPrompts = false;
        private double confidenceThresh = 0.4;

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
         * Specify whether to verify SSL (required).
         * 
         * @param verifySSL
         * @return this builder
         */
        public Builder verifySSL(final boolean verifySSL) {
            this.verifySSL = verifySSL;
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
         * Set the text prompt (optional).
         * 
         * @param textPrompt
         * @return this builder
         */
        public Builder textPrompt(final String textPrompt) {
            this.textPrompt = textPrompt;
            return this;
        }

        /**
         * Add objects representing positive prompts.
         * Each will be treated as a separate prompt.
         * 
         * @param positiveObjects
         * @return this builder
         */
        public Builder addPositiveBboxes(final Collection<? extends PathObject> positiveBboxes) {
            this.positiveBboxes.addAll(positiveBboxes);
            return this;
        }

        /**
         * Add objects representing negative prompts.
         * negative prompts are use with all positive prompts.
         * 
         * @param negativeObjects
         * @return this builder
         */
        public Builder addNegativeBboxes(final Collection<? extends PathObject> negativeBboxes) {
            this.negativeBboxes.addAll(negativeBboxes);
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
         * Specify the region request (required).
         * 
         * @param regionRequest
         * @return this builder
         */
        public Builder regionRequest(final RegionRequest regionRequest) {
            this.regionRequest = regionRequest;
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
         * Specify whether to reset prompts before detection.
         * 
         * @param resetPrompts
         * @return this builder
         */
        public Builder resetPrompts(final boolean resetPrompts) {
            this.resetPrompts = resetPrompts;
            return this;
        }

        /**
         * Specify the confidence threshold for predicted masks.
         * 
         * @param confidenceThresh
         * @return this builder
         */
        public Builder confidenceThresh(final double confidenceThresh) {
            this.confidenceThresh = confidenceThresh;
            return this;
        }

        /**
         * Build the detection task.
         * 
         * @return
         */
        public SAM3DetectionTask build() {
            return new SAM3DetectionTask(this);
        }

    }

}
