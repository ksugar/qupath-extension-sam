package org.elephant.sam;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.elephant.sam.entities.SAMOutput;
import org.elephant.sam.http.MultipartBodyBuilder;
import org.locationtech.jts.geom.Coordinate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.awt.common.AwtTools;
import qupath.lib.common.GeneralTools;
import qupath.lib.geom.Point2;
import qupath.lib.gui.images.servers.RenderedImageServer;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.io.GsonTools;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.regions.ImagePlane;
import qupath.lib.regions.ImageRegion;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.RectangleROI;
import qupath.lib.roi.interfaces.ROI;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Utility functions to help with Segment Anything detections.
 */
public class Utils {

    private static final Logger logger = LoggerFactory.getLogger(Utils.class);

    static final String SAM_QUALITY_MEASUREMENT = "SAM Quality";

    /**
     * Parse path objects from a JSON string.
     * Use this rather than the 'usual' JSON deserialization so that we can extract the quality
     * value from the object properties.
     * 
     * @param json
     * @return a list of PathObjects, or empty list if none can be parsed
     */
    public static List<PathObject> parsePathObjects(String json) {
        Gson gson = GsonTools.getInstance();
        JsonElement element = gson.fromJson(json, JsonElement.class);
        if (element.isJsonObject() && element.getAsJsonObject().has("features"))
            // Handle the case where the response is a GeoJSON FeatureCollection
            element = element.getAsJsonObject().get("features");
        if (element.isJsonArray()) {
            // Handle an array of GeoJSON Features
            return element.getAsJsonArray().asList().stream()
                    .map(e -> parsePathObject(gson, e))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } else if (element.isJsonObject()) {
            // Handle a single GeoJSON Feature
            PathObject pathObject = parsePathObject(gson, element);
            if (pathObject != null)
                return Collections.singletonList(pathObject);
        }
        logger.debug("Unable to parse PathObject from {}", json);
        return Collections.emptyList();
    }

    /**
     * Parse a single PathObject from a JsonElement.
     * 
     * @param gson
     * @param element
     * @return the PathObject, or null if it cannot be parsed
     */
    private static PathObject parsePathObject(Gson gson, JsonElement element) {
        if (!element.isJsonObject()) {
            logger.warn("Cannot parse PathObject from {}", element);
            return null;
        }
        JsonObject jsonObj = element.getAsJsonObject();
        PathObject pathObject = gson.fromJson(jsonObj, PathObject.class);
        if (jsonObj.has("properties")) {
            // Set quality from properties if we can
            JsonObject properties = jsonObj.get("properties").getAsJsonObject();
            JsonElement quality = properties.get("quality");
            if (quality != null && quality.isJsonPrimitive()) {
                pathObject.getMeasurementList().put(SAM_QUALITY_MEASUREMENT, quality.getAsDouble());
            }
            JsonElement objectId = properties.get("object_idx");
            if (objectId != null && objectId.isJsonPrimitive()) {
                pathObject.setPathClass(PathClass.getInstance(objectId.getAsString()));
            }
        }
        return pathObject;
    }

    /**
     * Extract coordinates for a region of interest vertices, transformed to be in
     * the coordinate system of a downsampled region.
     * Note that coordinates that are outside the image bounds are skipped.
     *
     * @param roi
     *            the ROI whose coordinates are of interest
     * @param region
     *            the region request used to request pixels
     * @param maxX
     *            the maximum X coordinate in the downsampled region (typically the
     *            requested image width)
     * @param maxY
     *            the maximum Y coordinate in the downsampled region (typically the
     *            requested image height)
     * @return
     */
    public static List<Coordinate> getCoordinates(ROI roi, RegionRequest region, int maxX, int maxY) {
        List<Coordinate> coords = new ArrayList<>();
        double downsample = region.getDownsample();
        double xOffset = region.getMinX();
        double yOffset = region.getMinY();
        for (Point2 p : roi.getAllPoints()) {
            int x = (int) Math.floor((p.getX() - xOffset) / downsample);
            int y = (int) Math.floor((p.getY() - yOffset) / downsample);
            if (x >= 0 && y >= 0 && x < maxX && y < maxY) {
                coords.add(new Coordinate(x, y));
            }
        }
        return coords;
    }

    /**
     * Apply an affine transform to a PathObject, updating its plane and classification if necessary.
     * 
     * @param pathObject
     *            the PathObject to transform
     * @param transform
     *            the transform to apply
     * @param pathClass
     *            the classification to assign
     * @param plane
     *            the plane to assign
     * @return the transformed PathObject. This may be the original object if no transforms were needed.
     * @implSpec This method is not applied recursively; it is assumed that the PathObject has no parent/child
     *           relationships that need to be preserved, but no check is made to confirm this.
     */
    public static PathObject applyTransformAndClassification(PathObject pathObject, AffineTransform transform,
            PathClass pathClass, ImagePlane plane) {
        if (transform != null && !transform.isIdentity()) {
            pathObject = PathObjectTools.transformObject(pathObject, transform, true);
        }
        if (plane != null && !Objects.equals(plane, pathObject.getROI().getImagePlane()))
            pathObject = PathObjectTools.updatePlane(pathObject, plane, true, false);
        if (pathClass == PathClass.NULL_CLASS) {
            pathObject.resetPathClass();
        } else if (pathClass != null) {
            pathObject.setPathClass(pathClass);
        }
        return pathObject;
    }

    /**
     * Assign a name to an object, incorporating the quality measurement if available
     * 
     * @param pathObject
     */
    public static void setNameForSAM(PathObject pathObject) {
        // If no classification is available, assign a name based upon the annotation quality
        Double quality = getSAMQuality(pathObject);
        String name;
        if (quality != null && pathObject.getName() == null) {
            name = "SAM (Quality=" + GeneralTools.formatNumber(quality, 3) + ")";
        } else {
            name = "SAM";
        }
        pathObject.setName(name);
    }

    /**
     * Get the quality score from a path object, if possible.
     * If available, this is stored in the object's measurement list.
     * 
     * @param pathObject
     * @return the quality score, or null if no score could be found
     */
    static Double getSAMQuality(PathObject pathObject) {
        return (Double) pathObject.getMeasurements()
                .getOrDefault(SAM_QUALITY_MEASUREMENT, null);
    }

    /**
     * Assign a random color to an object
     * 
     * @param pathObject
     */
    public static void setRandomColor(PathObject pathObject) {
        double hue = Math.random();
        double saturation = 0.5 + Math.random() / 2.0;
        double brightness = 0.5 + Math.random() / 2.0;
        Integer rgb = Color.HSBtoRGB((float) hue, (float) saturation, (float) brightness);
        pathObject.setColor(rgb);
    }

    /**
     * Create a rendered (RGB) imageserver from a QuPath viewer.
     * 
     * @param viewer
     * @return the image server
     * @throws IOException
     */
    public static ImageServer<BufferedImage> createRenderedServer(QuPathViewer viewer) throws IOException {
        return new RenderedImageServer.Builder(viewer.getImageData())
                .store(viewer.getImageRegionStore())
                .renderer(viewer.getImageDisplay())
                .build();
    }

    /**
     * Get a region request for the viewer, intersected with the rendered server.
     * 
     * @param viewer
     * @param renderedServer
     * @return the region request
     */
    public static RegionRequest getViewerRegion(QuPathViewer viewer, ImageServer<BufferedImage> renderedServer) {
        return getViewerRegion(viewer, renderedServer, viewer.getZPosition(), viewer.getTPosition());
    }

    /**
     * Get a region request for the viewer, intersected with the rendered server.
     * 
     * @param viewer
     * @param renderedServer
     * @param z
     * @param t
     * @return the region request
     */
    public static RegionRequest getViewerRegion(QuPathViewer viewer, ImageServer<BufferedImage> renderedServer, int z,
            int t) {
        if (renderedServer == null) {
            try {
                renderedServer = Utils.createRenderedServer(viewer);
            } catch (IOException e) {
                logger.error("Failed to create rendered server", e);
            }
        }
        // Find the region and downsample currently used within the viewer
        ImageRegion region = AwtTools.getImageRegion(viewer.getDisplayedRegionShape(), z, t);
        RegionRequest viewerRegion = RegionRequest.createInstance(renderedServer.getPath(),
                viewer.getDownsampleFactor(), region);
        viewerRegion = viewerRegion.intersect2D(0, 0, renderedServer.getWidth(), renderedServer.getHeight());
        return viewerRegion;
    }

    /**
     * Encode a BufferedImage as a base64-encoded PNG.
     * 
     * @param img
     *            the input image (must be compatible with PNG export using ImageIO)
     * @return the base64-encoded string
     * @throws IOException
     */
    public static String base64EncodePNG(BufferedImage img) throws IOException {
        return base64Encode(img, "png");
    }

    /**
     * Encode a BufferedImage as a base64-encoded image, written with ImageIO.
     * 
     * @param img
     *            the input image
     * @param format
     *            an ImageIO-friendly format string
     * @return the base64-encoded string
     * @throws IOException
     */
    static String base64Encode(BufferedImage img, String format) throws IOException {
        // Preallocate so that resizing is unlikely
        final ByteArrayOutputStream baos = new ByteArrayOutputStream(
                Math.min(1024 * 1024 * 16, img.getWidth() * img.getHeight() * 3));
        ImageIO.write(img, format, baos);
        final byte[] bytes = baos.toByteArray();
        return Base64.getEncoder().encodeToString(bytes);
    }

    /**
     * Select one object from a list based upon the requested output type.
     * 
     * @param pathObjects
     * @param outputType
     * @return the selected object or objects
     */
    public static List<PathObject> selectByOutputType(List<PathObject> pathObjects, SAMOutput outputType) {
        // It output type is SINGLE_MASK, then we should only have one object
        if (outputType == null || pathObjects.size() <= 1
                || outputType == SAMOutput.SINGLE_MASK
                || outputType == SAMOutput.MULTI_ALL)
            return pathObjects;
        Comparator<PathObject> comparator = null;
        switch (outputType) {
            case MULTI_LARGEST:
                comparator = Comparator.comparingDouble(Utils::getArea).reversed();
                break;
            case MULTI_SMALLEST:
                comparator = Comparator.comparingDouble(Utils::getArea);
                break;
            case MULTI_BEST_QUALITY:
                comparator = Comparator.comparingDouble(Utils::getQuality);
                break;
            default:
                return pathObjects;
        }
        return pathObjects.stream()
                .sorted(comparator)
                .limit(1)
                .collect(Collectors.toList());
    }

    /**
     * Get the area of a path object, or 0 if no ROI is available.
     * 
     * @param pathObject
     * @return the area
     */
    private static double getArea(PathObject pathObject) {
        return pathObject.hasROI() ? pathObject.getROI().getArea() : 0.0;
    }

    /**
     * Get the quality of a path object, or -1 if no quality is available.
     * 
     * @param pathObject
     * @return the quality
     */
    private static double getQuality(PathObject pathObject) {
        Double quality = Utils.getSAMQuality(pathObject);
        return quality == null ? -1 : quality;
    }

    /**
     * Check if a path object has a line ROI.
     * 
     * @param pathObject
     * @return true if the object has a line ROI
     */
    public static boolean hasLineROI(PathObject pathObject) {
        return pathObject.hasROI() && pathObject.getROI().getRoiType() == ROI.RoiType.LINE;
    }

    /**
     * Check if a path object has a rectangle ROI.
     * 
     * @param pathObject
     * @return true if the object has a rectangle ROI
     */
    public static boolean hasRectangleROI(PathObject pathObject) {
        return pathObject.getROI() instanceof RectangleROI;
    }

    public static boolean hasPointsROI(PathObject pathObject) {
        return pathObject.hasROI() && pathObject.getROI().getRoiType() == ROI.RoiType.POINT;
    }

    /**
     * Any annotation object with a non-empty ROI could potentially act as a prompt for detection.
     * 
     * @param pathObject
     * @return true if the object could be used as a prompt
     */
    public static boolean isPotentialPromptObject(PathObject pathObject) {
        return pathObject.isAnnotation() && pathObject.hasROI() && !pathObject.getROI().isEmpty();
    }

    /**
     * Converts a BufferedImage to a JPEG byte array.
     *
     * @param image
     *            The BufferedImage to convert.
     * @return A byte array containing the JPEG data.
     * @throws IOException
     *             If an error occurs during writing.
     */
    public static byte[] bufferedImageToJpegBytes(BufferedImage image) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "jpg", baos);
            return baos.toByteArray();
        }
    }

    public static byte[] createImageUploadMultipartBody(String boundary, String dirname, String filename,
            BufferedImage image)
            throws IOException {
        byte[] imageBytes = bufferedImageToJpegBytes(image);
        final MultipartBodyBuilder builder = new MultipartBodyBuilder(boundary);
        builder.addFormField("dirname", dirname);
        builder.addFilePart("file", filename, "image/jpeg", imageBytes);
        return builder.build();
    }

}
