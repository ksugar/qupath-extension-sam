package org.elephant.sam.tasks;

import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.core5.http.ContentType;
import org.elephant.sam.Utils;
import org.elephant.sam.entities.SAMType;
import org.elephant.sam.http.HttpUtils;
import org.elephant.sam.entities.SAMOutput;
import org.elephant.sam.entities.SAMPromptMode;
import org.elephant.sam.parameters.SAM2VideoPromptObject;
import org.elephant.sam.parameters.SAM2VideoPromptParameters;
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

import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

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
    private final List<RegionRequest> regionRequests = new ArrayList<>();

    private final Map<Integer, List<SAM2VideoPromptObject>> objs;

    private final boolean setRandomColor;

    private final boolean setName;

    private final String serverURL;

    private final boolean verifySSL;

    private final SAMType model;

    private final SAMPromptMode promptMode;

    private final String checkpointUrl;

    private final int planePosition;

    private final int indexOffset;

    private final Map<Integer, PathClass> indexToPathClass;

    private SAMSequenceTask(Builder builder) {
        this.serverURL = builder.serverURL;
        Objects.requireNonNull(serverURL, "Server must not be null!");

        this.verifySSL = builder.verifySSL;
        Objects.requireNonNull(verifySSL, "Verify SSL must not be null!");

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

        this.regionRequests.clear();
        this.regionRequests.addAll(builder.regionRequests);

        this.planePosition = builder.planePosition;

        this.indexOffset = builder.indexOffset;
        this.objs = builder.objs;

        this.setName = builder.setName;
        this.setRandomColor = builder.setRandomColor;
        this.checkpointUrl = builder.checkpointUrl;
        this.indexToPathClass = builder.indexToPathClass;
    }

    private boolean uploadImages(String dirname) throws IOException, InterruptedException {
        int paddingWidth = String.valueOf(regionRequests.size()).length();
        String filenameFormat = String.format("%%0%dd.jpg", paddingWidth);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        AtomicInteger progress = new AtomicInteger(0);
        final int total = regionRequests.size();
        IntStream.range(0, total).parallel().forEach(i -> {
            if (cancelled.get())
                return;
            final String boundary = "----------------" + System.currentTimeMillis();
            try {
                final BufferedImage img = renderedServer.readRegion(regionRequests.get(i));
                final String endpointURL = String.format("%supload/", Utils.ensureTrailingSlash(serverURL));
                MultipartEntityBuilder entityBuilder = MultipartEntityBuilder.create()
                        .setBoundary(boundary)
                        .addTextBody("dirname", dirname, ContentType.TEXT_PLAIN)
                        .addBinaryBody("file", Utils.bufferedImageToJpegBytes(img), ContentType.create("image/jpeg"),
                                String.format(filenameFormat, i));
                HttpResponse<String> response = HttpUtils.postMultipartRequest(endpointURL, verifySSL, entityBuilder);
                if (response.statusCode() != HttpURLConnection.HTTP_OK) {
                    logger.error("HTTP response: {}, {}", response.statusCode(), response.body());
                    cancelled.set(true);
                } else {
                    updateMessage(String.format("%d/%d images uploaded", progress.incrementAndGet(), total));
                    logger.info("Uploaded image {}", response.body());
                }
            } catch (IOException e) {
                logger.error("Failed to upload image", e);
                cancelled.set(true);
            }
        });
        if (cancelled.get()) {
            return false;
        }
        return true;
    }

    @Override
    protected List<PathObject> call() throws Exception {
        try {
            String dirname = UUID.randomUUID().toString();
            if (uploadImages(dirname)) {
                return detectObjects(dirname);
            } else {
                cancel();
                return Collections.emptyList();
            }
        } catch (InterruptedException e) {
            logger.warn("Interrupted while detecting objects", e);
            return Collections.emptyList();
        }
    }

    private List<PathObject> detectObjects(String dirname)
            throws InterruptedException, IOException {
        final SAM2VideoPromptParameters prompt = SAM2VideoPromptParameters.builder(model)
                .objs(objs)
                .dirname(dirname)
                .axes(promptMode.toString())
                .planePosition(planePosition)
                .checkpointUrl(checkpointUrl)
                .build();

        if (isCancelled())
            return Collections.emptyList();

        updateMessage("Processing images...");
        final String endpointURL = String.format("%svideo/", Utils.ensureTrailingSlash(serverURL));
        HttpResponse<String> response = HttpUtils.postRequest(endpointURL, verifySSL,
                GsonTools.getInstance().toJson(prompt));

        if (isCancelled())
            return Collections.emptyList();

        if (response.statusCode() == HttpURLConnection.HTTP_OK) {
            updateMessage("Processing done.");
            return parseResponse(response, regionRequests.get(0));
        } else {
            logger.error("HTTP response: {}, {}", response.statusCode(), response.body());
            return Collections.emptyList();
        }
    }

    private List<PathObject> parseResponse(HttpResponse<String> response, RegionRequest regionRequest) {
        List<PathObject> samObjects = Utils.parsePathObjects(response.body());
        AffineTransform transform = new AffineTransform();
        transform.translate(regionRequest.getMinX(), regionRequest.getMinY());
        transform.scale(regionRequest.getDownsample(), regionRequest.getDownsample());
        // Retain the original classification, and set names/colors if required
        List<PathObject> updatedObjects = new ArrayList<>();
        for (PathObject pathObject : samObjects) {
            ImagePlane plane = pathObject.getROI().getImagePlane();
            if (promptMode == SAMPromptMode.XYZ) {
                plane = ImagePlane.getPlane(indexOffset + plane.getZ(), plane.getT());
            } else if (promptMode == SAMPromptMode.XYT) {
                plane = ImagePlane.getPlane(plane.getZ(), indexOffset + plane.getT());
            }
            PathClass pathClass = indexToPathClass.get(Integer.valueOf(pathObject.getPathClass().getName()));
            pathObject = Utils.applyTransformAndClassification(pathObject, transform, pathClass, plane);
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
        private List<RegionRequest> regionRequests = new ArrayList<>();

        private String serverURL;
        private boolean verifySSL;
        private SAMType model = SAMType.VIT_L;
        private SAMPromptMode promptMode = SAMPromptMode.XYZ;
        private boolean setRandomColor = true;
        private boolean setName = true;
        private String checkpointUrl;
        private int indexOffset;
        private Map<Integer, PathClass> indexToPathClass;
        private int planePosition;

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
         * Set the region requests to use.
         * This is used to determine the region of interest for the objects created.
         * 
         * @param regionRequests
         * @return this builder
         */
        public Builder regionRequests(final List<RegionRequest> regionRequests) {
            this.regionRequests.clear();
            this.regionRequests.addAll(regionRequests);
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
         * Specify the index to start from.
         * 
         * @param indexOffset
         * @return this builder
         */
        public Builder indexOffset(final int indexOffset) {
            this.indexOffset = indexOffset;
            return this;
        }

        /**
         * Specify the mapping from index to PathClass.
         * 
         * @param indexToPathClass
         * @return this builder
         */
        public Builder indexToPathClass(final Map<Integer, PathClass> indexToPathClass) {
            this.indexToPathClass = indexToPathClass;
            return this;
        }

        /**
         * Specify the plane position.
         * This is used to determine the plane for the objects created.
         * 
         * @param planePosition
         * @return this builder
         */
        public Builder planePosition(final int planePosition) {
            this.planePosition = planePosition;
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
