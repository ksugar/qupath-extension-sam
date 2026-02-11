package org.elephant.sam.commands;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.elephant.sam.Utils;
import org.elephant.sam.comparators.NaturalOrderComparator;
import org.elephant.sam.entities.SAMOutput;
import org.elephant.sam.entities.SAMPromptMode;
import org.elephant.sam.entities.SAMType;
import org.elephant.sam.entities.SAMWeights;
import org.elephant.sam.parameters.SAM2VideoPromptObject;
import org.elephant.sam.parameters.SAM3VideoPromptObject;
import org.elephant.sam.parameters.SAMVideoPromptObject;
import org.elephant.sam.tasks.SAM3DetectionTask;
import org.elephant.sam.tasks.SAMAutoMaskTask;
import org.elephant.sam.tasks.SAMCancelDownloadTask;
import org.elephant.sam.tasks.SAMDetectionTask;
import org.elephant.sam.tasks.SAMFetchWeightsTask;
import org.elephant.sam.tasks.SAMProgressTask;
import org.elephant.sam.tasks.SAMRegisterWeightsTask;
import org.elephant.sam.tasks.SAMSequenceTask;
import org.elephant.sam.ui.SAMMainPane;
import org.elephant.sam.ui.SAMUIUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.common.ThreadTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassTools;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyEvent;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyListener;
import qupath.lib.plugins.workflow.DefaultScriptableWorkflowStep;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.PointsROI;
import qupath.lib.roi.RectangleROI;
import qupath.lib.roi.interfaces.ROI;

/**
 * The main command for SAM.
 */
public class SAMMainCommand implements Runnable {

    private final static Logger logger = LoggerFactory.getLogger(SAMMainCommand.class);

    private final static String TITLE = "Segment Anything Model";

    private Stage stage;

    private final QuPathGUI qupath;

    public QuPathGUI getQuPath() {
        return qupath;
    }

    /**
     * Server for SAM - see https://github.com/ksugar/samapi
     */
    private final StringProperty serverURLProperty = PathPrefs.createPersistentPreference(
            "ext.SAM.serverUrl", "http://localhost:8000/sam/");

    public StringProperty getServerURLProperty() {
        return serverURLProperty;
    }

    /**
     * Specify whether to verify SSL.
     */
    private static final boolean DEFAULT_VERIFY_SSL = false;
    private final BooleanProperty verifySSLProperty = PathPrefs.createPersistentPreference(
            "ext.SAM.verifySSL", DEFAULT_VERIFY_SSL);

    public BooleanProperty getVerifySSLProperty() {
        return verifySSLProperty;
    }

    /**
     * Selected SAM type
     */
    private final ObjectProperty<SAMType> samTypeProperty = PathPrefs.createPersistentPreference(
            "ext.SAM.model", SAMType.VIT_L, SAMType.class);

    public ObjectProperty<SAMType> getSamTypeProperty() {
        return samTypeProperty;
    }

    /**
     * Selected SAM type to register
     */
    private final ObjectProperty<SAMType> samTypeRegisterProperty = new SimpleObjectProperty<>();

    public ObjectProperty<SAMType> getSamTypeRegisterProperty() {
        return samTypeRegisterProperty;
    }

    /**
     * Selected SAM weights
     */
    private final ObjectProperty<SAMWeights> selectedWeightsProperty = new SimpleObjectProperty<>();

    public ObjectProperty<SAMWeights> getSelectedWeightsProperty() {
        return selectedWeightsProperty;
    }

    /**
     * List of available weights
     */
    private ObservableList<SAMWeights> availableWeightsList = FXCollections.observableArrayList();

    public ObservableList<SAMWeights> getAvailableWeightsList() {
        return availableWeightsList;
    }

    /**
     * Selected output type
     */
    private static final SAMOutput DEFAULT_OUTPUT_TYPE = SAMOutput.MULTI_SMALLEST;
    private final ObjectProperty<SAMOutput> outputTypeProperty = PathPrefs.createPersistentPreference(
            "ext.SAM.outputType", DEFAULT_OUTPUT_TYPE, SAMOutput.class);

    public ObjectProperty<SAMOutput> getOutputTypeProperty() {
        return outputTypeProperty;
    }

    /**
     * Set the names of new SAM detected objects.
     */
    private static final boolean DEFAULT_SET_NAMES = true;
    private final BooleanProperty setNamesProperty = PathPrefs.createPersistentPreference(
            "ext.SAM.setNames", DEFAULT_SET_NAMES);

    public BooleanProperty getSetNamesProperty() {
        return setNamesProperty;
    }

    /**
     * Assign random colors to new SAM objects.
     */
    private static final boolean DEFAULT_USE_RANDOM_COLORS = true;
    private final BooleanProperty useRandomColorsProperty = PathPrefs.createPersistentPreference(
            "ext.SAM.useRandomColors", DEFAULT_USE_RANDOM_COLORS);

    public BooleanProperty getUseRandomColorsProperty() {
        return useRandomColorsProperty;
    }

    /**
     * Clear current objects.
     */
    private static final boolean DEFAULT_CLEAR_CURRENT_OBJECTS = true;
    private final BooleanProperty clearCurrentObjectsProperty = PathPrefs.createPersistentPreference(
            "ext.SAM.autoMask.clearCurrentObjects", DEFAULT_CLEAR_CURRENT_OBJECTS);

    public BooleanProperty getClearCurrentObjectsProperty() {
        return clearCurrentObjectsProperty;
    }

    /**
     * Points per side parameter.
     */
    private static final int DEFAULT_POINTS_PER_SIDE = 16;
    private final IntegerProperty pointsPerSideProperty = PathPrefs.createPersistentPreference(
            "ext.SAM.autoMask.pointsPerSide", DEFAULT_POINTS_PER_SIDE);

    public IntegerProperty getPointsPerSideProperty() {
        return pointsPerSideProperty;
    }

    /**
     * Points per batch parameter.
     */
    private static final int DEFAULT_POINTS_PER_BATCH = 64;
    private final IntegerProperty pointsPerBatchProperty = PathPrefs.createPersistentPreference(
            "ext.SAM.autoMask.pointsPerBatch", DEFAULT_POINTS_PER_BATCH);

    public IntegerProperty getPointsPerBatchProperty() {
        return pointsPerBatchProperty;
    }

    /**
     * Pred IoU thresh parameter.
     */
    private static final double DEFAULT_PRED_IOU_THRESH = 0.88;
    private final DoubleProperty predIoUThreshProperty = PathPrefs.createPersistentPreference(
            "ext.SAM.autoMask.predIoUThresh", DEFAULT_PRED_IOU_THRESH);

    public DoubleProperty getPredIoUThreshProperty() {
        return predIoUThreshProperty;
    }

    /**
     * Stability score thresh parameter.
     */
    private static final double DEFAULT_STABILITY_SCORE_THRESH = 0.95;
    private final DoubleProperty stabilityScoreThreshProperty = PathPrefs.createPersistentPreference(
            "ext.SAM.autoMask.stabilityScoreThresh", DEFAULT_STABILITY_SCORE_THRESH);

    public DoubleProperty getStabilityScoreThreshProperty() {
        return stabilityScoreThreshProperty;
    }

    /**
     * Stability score offset parameter.
     */
    private static final double DEFAULT_STABILITY_SCORE_OFFSET = 1.0;
    private final DoubleProperty stabilityScoreOffsetProperty = PathPrefs.createPersistentPreference(
            "ext.SAM.autoMask.stabilityScoreOffset", DEFAULT_STABILITY_SCORE_OFFSET);

    public DoubleProperty getStabilityScoreOffsetProperty() {
        return stabilityScoreOffsetProperty;
    }

    /**
     * Box NMS thresh parameter.
     */
    private static final double DEFAULT_BOX_NMS_THRESH = 0.2;
    private final DoubleProperty boxNmsThreshProperty = PathPrefs.createPersistentPreference(
            "ext.SAM.autoMask.boxNmsThresh", DEFAULT_BOX_NMS_THRESH);

    public DoubleProperty getBoxNmsThreshProperty() {
        return boxNmsThreshProperty;
    }

    /**
     * Crop N layers parameter.
     */
    private static final int DEFAULT_CROP_N_LAYERS = 0;
    private final IntegerProperty cropNLayersProperty = PathPrefs.createPersistentPreference(
            "ext.SAM.autoMask.cropNLayers", DEFAULT_CROP_N_LAYERS);

    public IntegerProperty getCropNLayersProperty() {
        return cropNLayersProperty;
    }

    /**
     * Crop NMS thresh parameter.
     */
    private static final double DEFAULT_CROP_NMS_THRESH = 0.7;
    private final DoubleProperty cropNmsThreshProperty = PathPrefs.createPersistentPreference(
            "ext.SAM.autoMask.cropNmsThresh", DEFAULT_CROP_NMS_THRESH);

    public DoubleProperty getCropNmsThreshProperty() {
        return cropNmsThreshProperty;
    }

    /**
     * Crop overlap ratio parameter.
     */
    private static final double DEFAULT_CROP_OVERLAP_RATIO = (double) 512 / 1500;
    private final DoubleProperty cropOverlapRatioProperty = PathPrefs.createPersistentPreference(
            "ext.SAM.autoMask.cropOverlapRatio", DEFAULT_CROP_OVERLAP_RATIO);

    public DoubleProperty getCropOverlapRatioProperty() {
        return cropOverlapRatioProperty;
    }

    /**
     * Crop N points downscale factor parameter.
     */
    private static final int DEFAULT_CROP_N_POINTS_DOWNSCALE_FACTOR = 1;
    private final IntegerProperty cropNPointsDownscaleFactorProperty = PathPrefs.createPersistentPreference(
            "ext.SAM.autoMask.cropNPointsDownscaleFactor", DEFAULT_CROP_N_POINTS_DOWNSCALE_FACTOR);

    public IntegerProperty getCropNPointsDownscaleFactorProperty() {
        return cropNPointsDownscaleFactorProperty;
    }

    /**
     * Min mask region area parameter.
     */
    private static final int DEFAULT_MIN_MASK_REGION_AREA = 0;
    private final IntegerProperty minMaskRegionAreaProperty = PathPrefs.createPersistentPreference(
            "ext.SAM.autoMask.minMaskRegionArea", DEFAULT_MIN_MASK_REGION_AREA);

    public IntegerProperty getMinMaskRegionAreaProperty() {
        return minMaskRegionAreaProperty;
    }

    /**
     * Include image edge.
     */
    private static final boolean DEFAULT_INCLUDE_IMAGE_EDGE = false;
    private final BooleanProperty includeImageEdgeProperty = PathPrefs.createPersistentPreference(
            "ext.SAM.autoMask.includeImageEdge", DEFAULT_INCLUDE_IMAGE_EDGE);

    public BooleanProperty getIncludeImageEdgeProperty() {
        return includeImageEdgeProperty;
    }

    /**
     * Optionally allow line ROIs to be used as an alternative to points.
     * Defaults to false, as this tends to get too many points.
     */
    private final BooleanProperty useLinesForPointsProperty = PathPrefs.createPersistentPreference(
            "ext.SAM.permitLines", false);

    public BooleanProperty getUseLinesForPointsProperty() {
        return useLinesForPointsProperty;
    }

    /**
     * Optionally keep the prompt objects after detection.
     */
    private final BooleanProperty keepPromptsProperty = PathPrefs.createPersistentPreference(
            "ext.SAM.keepPrompts", false);

    public BooleanProperty getKeepPromptsProperty() {
        return keepPromptsProperty;
    }

    /**
     * Whether to reset prompts before detection.
     */
    private static final boolean DEFAULT_RESET_PROMPTS = false;
    private final BooleanProperty resetPromptsProperty = PathPrefs.createPersistentPreference(
            "ext.SAM.resetPrompts", DEFAULT_RESET_PROMPTS);

    public BooleanProperty getResetPromptsProperty() {
        return resetPromptsProperty;
    }

    /**
     * Confidence thresh parameter.
     */
    private static final double DEFAULT_CONFIDENCE_THRESH = 0.4;
    private final DoubleProperty confidenceThreshProperty = PathPrefs.createPersistentPreference(
            "ext.SAM.autoMask.confidenceThresh", DEFAULT_CONFIDENCE_THRESH);

    public DoubleProperty getConfidenceThreshProperty() {
        return confidenceThreshProperty;
    }

    /**
     * Whether live mode is turned on, to make detections as annotations are added
     */
    private final BooleanProperty liveModeProperty = new SimpleBooleanProperty(false);

    public BooleanProperty getLiveModeProperty() {
        return liveModeProperty;
    }

    /**
     * Override the classification of new point annotations to be an ignored class.
     */
    private BooleanProperty forceBackgroundPointsProperty = new SimpleBooleanProperty(false);

    public BooleanProperty getForceBackgroundPointsProperty() {
        return forceBackgroundPointsProperty;
    }

    /**
     * From index used in runVideo.
     */
    private final IntegerProperty fromIndexProperty = new SimpleIntegerProperty(0);

    public IntegerProperty getFromIndexProperty() {
        return fromIndexProperty;
    }

    /**
     * To index used in runVideo.
     */
    private final IntegerProperty toIndexProperty = new SimpleIntegerProperty(0);

    public IntegerProperty getToIndexProperty() {
        return toIndexProperty;
    }

    /**
     * Text prompt for SAM3
     */
    private final StringProperty textPromptProperty = new SimpleStringProperty("");

    public StringProperty getTextPromptProperty() {
        return textPromptProperty;
    }

    /**
     * Current image data
     */
    private final ObjectProperty<ImageData<BufferedImage>> imageDataProperty = new SimpleObjectProperty<>();

    /**
     * The task currently running
     */
    private final ObservableSet<Task<?>> currentTasks = FXCollections.observableSet();

    /**
     * Currently awaiting a detection from the server
     */
    private final BooleanBinding isTaskRunning = Bindings.isNotEmpty(currentTasks);

    public BooleanBinding getIsTaskRunning() {
        return isTaskRunning;
    }

    /**
     * Progress of the current detection.
     */
    private final DoubleProperty detectionProgressProperty = new SimpleDoubleProperty(1.0);

    public DoubleProperty getDetectionProgressProperty() {
        return detectionProgressProperty;
    }

    /**
     * Flag to indicate that running isn't possible right now
     */
    private final BooleanBinding disableRunning = imageDataProperty.isNull()
            .or(serverURLProperty.isEmpty())
            .or(samTypeProperty.isNull())
            .or(isTaskRunning);

    public BooleanBinding getDisableRunning() {
        return disableRunning;
    }

    /**
     * String to contain help text for the user
     */
    private StringProperty infoTextProperty = new SimpleStringProperty();

    public StringProperty getInfoTextProperty() {
        return infoTextProperty;
    }

    /**
     * Timestamp of the last info text representing an error. This can be used for
     * styling,
     * and/or to ensure that errors remain visible for a minimum amount of time.
     */
    private LongProperty infoTextErrorTimestampProperty = new SimpleLongProperty(0);

    public LongProperty getInfoTextErrorTimestampProperty() {
        return infoTextErrorTimestampProperty;
    }

    /**
     * Selected SAM prompt mode
     */
    private final ObjectProperty<SAMPromptMode> samPromptModeProperty = PathPrefs.createPersistentPreference(
            "ext.SAM.promptMode", SAMPromptMode.XY, SAMPromptMode.class);

    public ObjectProperty<SAMPromptMode> getSamPromptModeProperty() {
        return samPromptModeProperty;
    }

    private PathObjectHierarchyListener hierarchyListener = this::hierarchyChanged;

    private ChangeListener<ImageData<BufferedImage>> imageDataListener = this::imageDataChanged;

    // We need to turn off the multipoint tool when running live detection, but
    // restore it afterwards
    private boolean previousMultipointValue = PathPrefs.multipointToolProperty().get();

    /**
     * Task pool
     */
    private ExecutorService pool;

    /**
     * Constructor.
     * 
     * @param qupath
     *            main QuPath instance
     */
    public SAMMainCommand(QuPathGUI qupath) {
        this.qupath = qupath;
    }

    /**
     * Show the SAM dialog.
     */
    @Override
    public void run() {
        showStage();
    }

    private void showStage() {
        boolean creatingStage = stage == null;
        if (creatingStage)
            stage = createStage();
        if (stage.isShowing())
            return;
        liveModeProperty.addListener((observable, oldValue, newValue) -> {
            if (newValue)
                startLiveMode();
            else
                stopLiveMode();
        });
        if (pool == null)
            pool = Executors.newCachedThreadPool(ThreadTools.createThreadFactory("SAM-detection", true));
        // Shouldn't be required... but make sure nothing is bound
        this.imageDataProperty.unbind();
        this.imageDataProperty.removeListener(imageDataListener);
        // Add listener
        this.imageDataProperty.addListener(imageDataListener);
        this.imageDataProperty.bind(qupath.imageDataProperty());
        // Fetch weights
        submitFetchWeightsTask(samTypeProperty.get());
        stage.show();
        if (creatingStage) {
            fixStageSizeOnFirstShow(stage);
        }
    }

    /**
     * Update the info text with an error.
     * 
     * @param message
     */
    public void updateInfoTextWithError(String message) {
        infoTextProperty.set(message);
        infoTextErrorTimestampProperty.set(System.currentTimeMillis());
    }

    /**
     * Update the info text.
     * 
     * @param message
     */
    public void updateInfoText(String message) {
        // Don't overwrite an error message until 5 seconds have passed & we have
        // something worthwhile to show
        long lastErrorTimestamp = infoTextErrorTimestampProperty.get();
        boolean hasMessage = message != null && !message.isEmpty();
        if (System.currentTimeMillis() - lastErrorTimestamp < 5000 || (!hasMessage && lastErrorTimestamp > 0))
            return;
        infoTextProperty.set(message);
        infoTextErrorTimestampProperty.set(0);
    }

    private Stage createStage() {
        Stage stage = new Stage();
        Pane pane = new SAMMainPane(this);
        Scene scene = new Scene(pane);
        stage.setScene(scene);
        Platform.runLater(() -> {
            fixStageSizeOnFirstShow(stage);
        });
        stage.setTitle(TITLE);
        stage.setResizable(true);
        stage.initOwner(qupath.getStage());
        stage.setOnCloseRequest(event -> {
            hideStage();
            stopLiveMode();
            event.consume();
        });
        return stage;
    }

    private static void fixStageSizeOnFirstShow(Stage stage) {
        stage.sizeToScene();
        // Make slightly wider, as workaround for column constraints not including h-gap
        stage.setWidth(stage.getWidth() + SAMUIUtils.H_GAP * 2);
        // Set other size/position variables so they are retained after stage is hidden
        stage.setHeight(stage.getHeight());
        stage.setX(stage.getX());
        stage.setY(stage.getY());
    }

    private void hideStage() {
        this.liveModeProperty.set(false);
        this.imageDataProperty.unbind();
        this.imageDataProperty.removeListener(imageDataListener); // To be sure...
        stage.hide();
    }

    public void runPrompt2D() {
        QuPathViewer viewer = qupath.getViewer();
        ImageData<BufferedImage> imageData = imageDataProperty.get();
        if (imageData == null || viewer == null) {
            updateInfoTextWithError("No image available!");
            return;
        }
        if (imageData != viewer.getImageData()) {
            updateInfoTextWithError("ImageData doesn't match what's in the viewer!");
            return;
        }
        Collection<PathObject> selectedObjects = imageData.getHierarchy().getSelectionModel().getSelectedObjects();

        if (getSamTypeProperty().get().isSAM3Compatible()) {
            String textPrompt = getTextPromptProperty().get();
            List<PathObject> foregroundObjects = getSAM3ForegroundObjects(selectedObjects);
            List<PathObject> backgroundObjects = getSAM3BackgroundObjects(selectedObjects);
            if (textPrompt == null || textPrompt.isBlank() && foregroundObjects.isEmpty()) {
                updateInfoTextWithError("No text prompt or foreground rectangle rois to use");
                return;
            } else {
                submitSAM3DetectionTask(textPrompt, foregroundObjects, backgroundObjects);
            }
        } else {
            List<PathObject> foregroundObjects = getForegroundObjects(selectedObjects);
            List<PathObject> backgroundObjects = getBackgroundObjects(selectedObjects);
            if (!foregroundObjects.isEmpty()) {
                submitDetectionTask(foregroundObjects, backgroundObjects);
            } else {
                updateInfoText("No foreground objects to use");
            }
        }
    }

    /**
     * Run the detection with a prompt.
     */
    public void runPrompt() {
        if (samPromptModeProperty.isEqualTo(SAMPromptMode.XY).get()) {
            logger.info("Running prompt in 2D mode");
            runPrompt2D();
        } else {
            logger.info("Running prompt in video mode");
            runVideo();
        }
    }

    /**
     * Run the auto mask detection.
     */
    public void runAutoMask() {
        QuPathViewer viewer = qupath.getViewer();
        ImageData<BufferedImage> imageData = imageDataProperty.get();
        if (imageData == null || viewer == null) {
            updateInfoTextWithError("No image available!");
            return;
        }
        if (imageData != viewer.getImageData()) {
            updateInfoTextWithError("ImageData doesn't match what's in the viewer!");
            return;
        }
        ImageServer<BufferedImage> renderedServer;
        try {
            renderedServer = Utils.createRenderedServer(viewer);
        } catch (IOException e) {
            logger.error("Failed to create rendered server", e);
            updateInfoTextWithError("Failed to create rendered server: " + e.getMessage());
            return;
        }
        RegionRequest regionRequest = Utils.getViewerRegion(viewer, renderedServer);
        SAMAutoMaskTask task = SAMAutoMaskTask.builder(qupath.getViewer())
                .server(renderedServer)
                .regionRequest(regionRequest)
                .serverURL(serverURLProperty.get())
                .verifySSL(verifySSLProperty.get())
                .model(samTypeProperty.get())
                .outputType(outputTypeProperty.get())
                .setName(setNamesProperty.get())
                .clearCurrentObjects(clearCurrentObjectsProperty.get())
                .setRandomColor(useRandomColorsProperty.get())
                .pointsPerSide(pointsPerSideProperty.get())
                .pointsPerBatch(pointsPerBatchProperty.get())
                .predIoUThresh(predIoUThreshProperty.get())
                .stabilityScoreThresh(stabilityScoreThreshProperty.get())
                .stabilityScoreOffset(stabilityScoreOffsetProperty.get())
                .boxNmsThresh(boxNmsThreshProperty.get())
                .cropNLayers(cropNLayersProperty.get())
                .cropNmsThresh(cropNmsThreshProperty.get())
                .cropOverlapRatio(cropOverlapRatioProperty.get())
                .cropNPointsDownscaleFactor(cropNPointsDownscaleFactorProperty.get())
                .minMaskRegionArea(minMaskRegionAreaProperty.get())
                .includeImageEdge(includeImageEdgeProperty.get())
                .checkpointUrl(selectedWeightsProperty.get().getUrl())
                .build();
        task.setOnSucceeded(event -> {
            List<PathObject> detected = task.getValue();
            if (detected != null) {
                if (!detected.isEmpty()) {
                    Platform.runLater(() -> {
                        PathObjectHierarchy hierarchy = imageData.getHierarchy();
                        if (clearCurrentObjectsProperty.get())
                            hierarchy.clearAll();
                        hierarchy.addObjects(detected);
                        hierarchy.getSelectionModel().clearSelection();
                        hierarchy.fireHierarchyChangedEvent(this);
                    });
                } else {
                    logger.warn("No objects detected");
                }
            }
        });
        submitTask(task);

        final String cmd = String.format("""
                var clearCurrentObjects = %b
                var task = org.elephant.sam.tasks.SAMAutoMaskTask.builder(getCurrentViewer())
                    .server(org.elephant.sam.Utils.createRenderedServer(getCurrentViewer()))
                    .regionRequest(%s)
                    .serverURL("%s")
                    .verifySSL(%b)
                    .model(%s)
                    .outputType(%s)
                    .setName(%b)
                    .clearCurrentObjects(clearCurrentObjects)
                    .setRandomColor(%b)
                    .pointsPerSide(%d)
                    .pointsPerBatch(%d)
                    .predIoUThresh(%f)
                    .stabilityScoreThresh(%f)
                    .stabilityScoreOffset(%f)
                    .boxNmsThresh(%f)
                    .cropNLayers(%d)
                    .cropNmsThresh(%f)
                    .cropOverlapRatio(%f)
                    .cropNPointsDownscaleFactor(%d)
                    .minMaskRegionArea(%d)
                    .includeImageEdge(%b)
                    .checkpointUrl("%s")
                    .build()
                task.setOnSucceeded(event -> {
                    List<PathObject> detected = task.getValue()
                    if (detected != null) {
                        if (!detected.isEmpty()) {
                            Platform.runLater(() -> {
                                PathObjectHierarchy hierarchy = getCurrentHierarchy()
                                if (clearCurrentObjects)
                                    hierarchy.clearAll()
                                hierarchy.addObjects(detected)
                                hierarchy.getSelectionModel().clearSelection()
                                hierarchy.fireHierarchyChangedEvent(this)
                            });
                        } else {
                            print("No objects detected")
                        }
                    }
                });
                Platform.runLater(task)
                """,
                clearCurrentObjectsProperty.get(),
                Utils.getGroovyScriptForCreateRegionRequest(regionRequest),
                serverURLProperty.get(),
                verifySSLProperty.get(),
                samTypeProperty.get().getFullyQualifiedName(),
                outputTypeProperty.get().getFullyQualifiedName(),
                setNamesProperty.get(),
                useRandomColorsProperty.get(),
                pointsPerSideProperty.get(),
                pointsPerBatchProperty.get(),
                predIoUThreshProperty.get(),
                stabilityScoreThreshProperty.get(),
                stabilityScoreOffsetProperty.get(),
                boxNmsThreshProperty.get(),
                cropNLayersProperty.get(),
                cropNmsThreshProperty.get(),
                cropOverlapRatioProperty.get(),
                cropNPointsDownscaleFactorProperty.get(),
                minMaskRegionAreaProperty.get(),
                includeImageEdgeProperty.get(),
                selectedWeightsProperty.get().getUrl())
                .strip();
        imageData.getHistoryWorkflow().addStep(
                new DefaultScriptableWorkflowStep("SAMAutoMask", cmd));
    }

    private PathClass getNextPathClass(Collection<String> samPathClassNames) {
        int i = 0;
        while (true) {
            String name = String.format("SAM%d", i);
            if (samPathClassNames.contains(name)) {
                i++;
            } else {
                return PathClass.getInstance(name);
            }
        }
    }

    /**
     * Run the video predictor.
     */
    public void runVideo() {
        if (samPromptModeProperty.get() == SAMPromptMode.XY) {
            updateInfoTextWithError("Video prediction is not available in XY mode!");
            return;
        }
        QuPathViewer viewer = qupath.getViewer();
        ImageData<BufferedImage> imageData = imageDataProperty.get();
        if (imageData == null || viewer == null) {
            updateInfoTextWithError("No image available!");
            return;
        }
        if (imageData != viewer.getImageData()) {
            updateInfoTextWithError("ImageData doesn't match what's in the viewer!");
            return;
        }
        Collection<PathObject> selectedObjects = imageData.getHierarchy().getSelectionModel().getSelectedObjects();
        if (samPromptModeProperty.get() == SAMPromptMode.XYZ) {
            selectedObjects = selectedObjects.stream().filter((pathObject) -> {
                return ((pathObject.getROI().getT() == viewer.getTPosition()));
            }).collect(Collectors.toSet());
        } else if (samPromptModeProperty.get() == SAMPromptMode.XYT) {
            selectedObjects = selectedObjects.stream().filter((pathObject) -> {
                return ((pathObject.getROI().getZ() == viewer.getZPosition()));
            }).collect(Collectors.toSet());
        } else {
            throw new IllegalArgumentException("Unsupported prompt mode: " + samPromptModeProperty.get());
        }
        List<PathObject> topLevelObjects = getTopLevelObjects(selectedObjects);
        Map<Integer, RegionRequest> regionRequestMap = new HashMap<>();
        Map<Integer, List<SAMVideoPromptObject>> objs = new HashMap<>();
        ImageServer<BufferedImage> renderedServer = null;
        try {
            renderedServer = Utils.createRenderedServer(viewer);
        } catch (IOException e) {
            logger.error("Failed to create rendered server", e);
        }
        int objsKey = -1;
        Collection<String> samPathClassNames = qupath.getViewer().getHierarchy().getAnnotationObjects().stream()
                .filter(pathObject -> !PathClassTools.isNullClass(pathObject.getPathClass()))
                .map(pathObject -> pathObject.getPathClass().getName())
                .filter(name -> name != null && name.startsWith("SAM"))
                .collect(Collectors.toSet());
        Map<PathClass, Integer> pathClassToIndex = new HashMap<>();
        Map<Integer, PathClass> indexToPathClass = new HashMap<>();
        for (int i = 0; i < topLevelObjects.size(); i++) {
            PathObject topLevelObject = topLevelObjects.get(i);
            PathClass objectClass = topLevelObject.getPathClass();
            int objectIndex;
            if (PathClassTools.isNullClass(objectClass)) {
                objectIndex = i;
                indexToPathClass.put(objectIndex, getNextPathClass(samPathClassNames));
                samPathClassNames.add(indexToPathClass.get(objectIndex).getName());
            } else {
                objectIndex = pathClassToIndex.getOrDefault(objectClass,
                        topLevelObjects.size() + pathClassToIndex.size());
                pathClassToIndex.put(objectClass, objectIndex);
                indexToPathClass.put(objectIndex, objectClass);
            }
            final ROI roi = topLevelObject.getROI();
            RegionRequest regionRequest = null;
            if (samPromptModeProperty.get() == SAMPromptMode.XYZ) {
                objsKey = roi.getZ();
                regionRequest = regionRequestMap.getOrDefault(objsKey,
                        Utils.getViewerRegion(viewer, renderedServer, roi.getZ(), viewer.getTPosition()));
            } else if (samPromptModeProperty.get() == SAMPromptMode.XYT) {
                objsKey = roi.getT();
                regionRequest = regionRequestMap.getOrDefault(objsKey,
                        Utils.getViewerRegion(viewer, renderedServer, viewer.getZPosition(), roi.getT()));
            }
            objsKey -= fromIndexProperty.get();
            if (objsKey < 0 || objsKey > toIndexProperty.get() - fromIndexProperty.get()) {
                logger.warn("Skipping object {} with index {} as it is outside the specified range", topLevelObject,
                        objsKey);
                continue;
            }
            if (!samTypeProperty.get().isSAM3Compatible()) {
                SAM2VideoPromptObject.Builder builder = SAM2VideoPromptObject.builder(objectIndex);
                if (roi instanceof PointsROI) {
                    PointsROI pointsROI = (PointsROI) roi;
                    if (isForegroundObject(topLevelObject)) {
                        builder = builder.addToForeground(Utils.getCoordinates(pointsROI, regionRequest,
                                regionRequest.getWidth(), regionRequest.getHeight()));
                    } else {
                        builder = builder.addToBackground(Utils.getCoordinates(pointsROI, regionRequest,
                                regionRequest.getWidth(), regionRequest.getHeight()));
                    }
                } else if (roi instanceof RectangleROI) {
                    RegionRequest roiRegion = RegionRequest.createInstance(renderedServer.getPath(),
                            regionRequest.getDownsample(), roi);
                    builder = builder.bbox(
                            (int) ((roiRegion.getMinX() - regionRequest.getMinX()) / regionRequest.getDownsample()),
                            (int) ((roiRegion.getMinY() - regionRequest.getMinY()) / regionRequest.getDownsample()),
                            (int) Math
                                    .round((roiRegion.getMaxX() - regionRequest.getMinX())
                                            / regionRequest.getDownsample()),
                            (int) Math.round(
                                    (roiRegion.getMaxY() - regionRequest.getMinY()) / regionRequest.getDownsample()));
                    for (PathObject childObject : topLevelObject.getChildObjects()) {
                        final ROI childROOI = childObject.getROI();
                        if (childROOI instanceof PointsROI) {
                            PointsROI pointsChildROI = (PointsROI) childROOI;
                            if (isForegroundObject(childObject)) {
                                builder = builder.addToForeground(Utils.getCoordinates(pointsChildROI, regionRequest,
                                        regionRequest.getWidth(), regionRequest.getHeight()));
                            } else {
                                builder = builder.addToBackground(Utils.getCoordinates(pointsChildROI, regionRequest,
                                        regionRequest.getWidth(), regionRequest.getHeight()));
                            }
                        }
                    }
                } else {
                    continue;
                }
                final SAM2VideoPromptObject sam2VideoPromptObject = builder.build();

                List<SAMVideoPromptObject> objectList = objs.getOrDefault(objsKey, new ArrayList<>());
                objectList.add(sam2VideoPromptObject);
                objs.put(objsKey, objectList);
            } else {
                SAM3VideoPromptObject.Builder builder = SAM3VideoPromptObject.builder(objectIndex);
                builder.text(getTextPromptProperty().get());
                if (roi instanceof RectangleROI) {
                    RegionRequest roiRegion = RegionRequest.createInstance(renderedServer.getPath(),
                            regionRequest.getDownsample(), roi);
                    if (objectClass != null && PathClassTools.isIgnoredClass(objectClass)) {
                        builder = builder.addNegativeBbox(
                                (roiRegion.getMinX() - regionRequest.getMinX()) / regionRequest.getDownsample(),
                                (roiRegion.getMinY() - regionRequest.getMinY()) / regionRequest.getDownsample(),
                                (roiRegion.getMaxX() - roiRegion.getMinX()) / regionRequest.getDownsample(),
                                (roiRegion.getMaxY() - roiRegion.getMinY()) / regionRequest.getDownsample());
                    } else {
                        builder = builder.addPositiveBbox(
                                (roiRegion.getMinX() - regionRequest.getMinX()) / regionRequest.getDownsample(),
                                (roiRegion.getMinY() - regionRequest.getMinY()) / regionRequest.getDownsample(),
                                (roiRegion.getMaxX() - roiRegion.getMinX()) / regionRequest.getDownsample(),
                                (roiRegion.getMaxY() - roiRegion.getMinY()) / regionRequest.getDownsample());
                    }
                } else {
                    continue;
                }
                final SAM3VideoPromptObject sam3VideoPromptObject = builder.build();

                List<SAMVideoPromptObject> objectList = objs.getOrDefault(objsKey, new ArrayList<>());
                objectList.add(sam3VideoPromptObject);
                objs.put(objsKey, objectList);
            }
        }
        if (objs.isEmpty() && (!samTypeProperty.get().isSAM3Compatible() || getTextPromptProperty().get().isBlank()
                || getTextPromptProperty().get().equals("visual"))) {
            updateInfoTextWithError("No effective prompts found in the specified range!");
            return;
        }
        final List<RegionRequest> regionRequests = new ArrayList<>();
        int planePosition = -1;
        if (samPromptModeProperty.get() == SAMPromptMode.XYZ) {
            for (int z = fromIndexProperty.get(); z <= toIndexProperty.get(); z++) {
                RegionRequest viewerRegion = Utils.getViewerRegion(viewer, renderedServer, z, viewer.getTPosition());
                regionRequests.add(viewerRegion);
            }
            planePosition = viewer.getTPosition();
        } else if (samPromptModeProperty.get() == SAMPromptMode.XYT) {
            for (int t = fromIndexProperty.get(); t <= toIndexProperty.get(); t++) {
                RegionRequest viewerRegion = Utils.getViewerRegion(viewer, renderedServer, viewer.getZPosition(), t);
                regionRequests.add(viewerRegion);
            }
            planePosition = viewer.getZPosition();
        } else {
            throw new IllegalArgumentException("Unsupported prompt mode: " + samPromptModeProperty.get());
        }
        String endpointName = samTypeProperty.get().isSAM3Compatible() ? "sam3video" : "video";
        SAMSequenceTask task = SAMSequenceTask.builder(qupath.getViewer())
                .server(renderedServer)
                .regionRequests(regionRequests)
                .serverURL(serverURLProperty.get())
                .endpointName(endpointName)
                .verifySSL(verifySSLProperty.get())
                .model(samTypeProperty.get())
                .promptMode(samPromptModeProperty.get())
                .objs(objs)
                .checkpointUrl(selectedWeightsProperty.get().getUrl())
                .indexOffset(fromIndexProperty.get())
                .indexToPathClass(indexToPathClass)
                .planePosition(planePosition)
                .build();
        task.messageProperty().addListener((observable, oldValue, newValue) -> {
            updateInfoText(newValue);
        });
        task.setOnSucceeded(event -> {
            List<PathObject> detected = task.getValue();
            if (detected != null) {
                if (!detected.isEmpty()) {
                    Platform.runLater(() -> {
                        PathObjectHierarchy hierarchy = qupath.getViewer().getImageData().getHierarchy();
                        if (!keepPromptsProperty.get()) {
                            // Remove prompt objects in one step
                            hierarchy.getSelectionModel().clearSelection();
                            hierarchy.removeObjects(topLevelObjects, false);
                        }
                        indexToPathClass.values().stream()
                                .filter(pathClass -> !qupath.getAvailablePathClasses().contains(pathClass))
                                .sorted(Comparator.comparing(PathClass::getName, new NaturalOrderComparator()))
                                .forEachOrdered(pathClass -> qupath.getAvailablePathClasses().add(pathClass));
                        hierarchy.addObjects(detected);
                        hierarchy.getSelectionModel().clearSelection();
                        hierarchy.fireHierarchyChangedEvent(this);
                    });
                } else {
                    logger.warn("No objects detected");
                }
            }
        });
        submitTask(task);

        StringBuilder sbObjs = new StringBuilder();
        sbObjs.append("[\n");
        int i = 0;
        for (Map.Entry<Integer, List<SAMVideoPromptObject>> entry : objs.entrySet()) {
            if (i++ > 0)
                sbObjs.append(",\n");
            int key = entry.getKey();
            sbObjs.append(String.format("    %d: ", key));
            List<SAMVideoPromptObject> promptObjectList = entry.getValue();
            if (promptObjectList.isEmpty())
                continue;
            sbObjs.append("[");
            for (SAMVideoPromptObject promptObject : promptObjectList) {
                sbObjs.append(promptObject.getBuilderAsGroovyScript());
                sbObjs.append(".build(),");
            }
            sbObjs.append("]");
        }
        sbObjs.append("\n]");
        String objsString = sbObjs.toString();

        StringBuffer sbIndexToPathClass = new StringBuffer();
        sbIndexToPathClass.append("[\n");
        for (Map.Entry<Integer, PathClass> entry : indexToPathClass.entrySet()) {
            sbIndexToPathClass.append(
                    String.format("""
                                %d: PathClass.getInstance("%s"),
                            """,
                            entry.getKey(),
                            entry.getValue().getName()));
        }
        sbIndexToPathClass.append("]");
        String indexToPathClassString = sbIndexToPathClass.toString();

        StringBuffer sbRegionRequests = new StringBuffer();
        if (samPromptModeProperty.get() == SAMPromptMode.XYZ) {
            sbRegionRequests.append(
                    String.format("(%d..%d).collect {%s}}",
                            fromIndexProperty.get(),
                            toIndexProperty.get(),
                            Utils.getGroovyScriptForCreateRegionRequest(
                                    Utils.getViewerRegion(viewer, renderedServer, 0, viewer.getTPosition()), "Z")));
        } else if (samPromptModeProperty.get() == SAMPromptMode.XYT) {
            sbRegionRequests.append(
                    String.format("(fromIndex..toIndex).collect {%s}",
                            Utils.getGroovyScriptForCreateRegionRequest(
                                    Utils.getViewerRegion(viewer, renderedServer, viewer.getZPosition(), 0), "T")));
        } else {
            throw new IllegalArgumentException("Unsupported prompt mode: " + samPromptModeProperty.get());
        }
        String regionRequestsString = sbRegionRequests.toString();

        final String cmd = String
                .format("""
                        def fromIndex = %d
                        def toIndex = %d
                        var objs = %s
                        var indexToPathClass = %s
                        var regionRequests = %s
                        var task = org.elephant.sam.tasks.SAMSequenceTask.builder(getCurrentViewer())
                            .server(org.elephant.sam.Utils.createRenderedServer(getCurrentViewer()))
                            .regionRequests(regionRequests)
                            .serverURL("%s")
                            .endpointName("%s")
                            .verifySSL(%b)
                            .model(%s)
                            .promptMode(%s)
                            .objs(objs)
                            .checkpointUrl("%s")
                            .indexOffset(fromIndex)
                            .indexToPathClass(indexToPathClass)
                            .planePosition(%d)
                            .build()
                        task.setOnSucceeded(event -> {
                            List<PathObject> detected = task.getValue()
                            if (detected != null) {
                                if (!detected.isEmpty()) {
                                    Platform.runLater(() -> {
                                        PathObjectHierarchy hierarchy = getCurrentHierarchy()
                                        indexToPathClass.values().stream()
                                                .filter(pathClass -> !getQuPath().getAvailablePathClasses().contains(pathClass))
                                                .sorted(Comparator.comparing(PathClass::getName, new org.elephant.sam.comparators.NaturalOrderComparator()))
                                                .forEachOrdered(pathClass -> getQuPath().getAvailablePathClasses().add(pathClass))
                                        hierarchy.addObjects(detected)
                                        hierarchy.getSelectionModel().clearSelection()
                                        hierarchy.fireHierarchyChangedEvent(this)
                                    });
                                } else {
                                    print("No objects detected")
                                }
                            }
                        });
                        Platform.runLater(task)
                        """,
                        fromIndexProperty.get(),
                        toIndexProperty.get(),
                        objsString,
                        indexToPathClassString,
                        regionRequestsString,
                        serverURLProperty.get(),
                        endpointName,
                        verifySSLProperty.get(),
                        samTypeProperty.get().getFullyQualifiedName(),
                        samPromptModeProperty.get().getFullyQualifiedName(),
                        selectedWeightsProperty.get().getUrl(),
                        planePosition)
                .strip();
        imageData.getHistoryWorkflow().addStep(
                new DefaultScriptableWorkflowStep("SAMSequence", cmd));
    }

    /**
     * Reset the auto mask parameters to their defaults.
     */
    public void resetAutoMaskParameters() {
        outputTypeProperty.set(DEFAULT_OUTPUT_TYPE);
        setNamesProperty.set(DEFAULT_SET_NAMES);
        useRandomColorsProperty.set(DEFAULT_USE_RANDOM_COLORS);
        clearCurrentObjectsProperty.set(DEFAULT_CLEAR_CURRENT_OBJECTS);
        pointsPerSideProperty.set(DEFAULT_POINTS_PER_SIDE);
        pointsPerBatchProperty.set(DEFAULT_POINTS_PER_BATCH);
        predIoUThreshProperty.set(DEFAULT_PRED_IOU_THRESH);
        stabilityScoreThreshProperty.set(DEFAULT_STABILITY_SCORE_THRESH);
        stabilityScoreOffsetProperty.set(DEFAULT_STABILITY_SCORE_OFFSET);
        boxNmsThreshProperty.set(DEFAULT_BOX_NMS_THRESH);
        cropNLayersProperty.set(DEFAULT_CROP_N_LAYERS);
        cropNmsThreshProperty.set(DEFAULT_CROP_NMS_THRESH);
        cropOverlapRatioProperty.set(DEFAULT_CROP_OVERLAP_RATIO);
        cropNPointsDownscaleFactorProperty.set(DEFAULT_CROP_N_POINTS_DOWNSCALE_FACTOR);
        minMaskRegionAreaProperty.set(DEFAULT_MIN_MASK_REGION_AREA);
        includeImageEdgeProperty.set(DEFAULT_INCLUDE_IMAGE_EDGE);
    }

    private void startLiveMode() {
        // Turn off the multipoint tool, so that each new point is a new object
        PathPrefs.multipointToolProperty().set(false);
        // Try to run once with the current selected objects
        runPrompt();
    }

    private void stopLiveMode() {
        PathPrefs.multipointToolProperty().set(previousMultipointValue);
    }

    private void submitTask(Task<?> task) {
        task.setOnFailed(event -> {
            Platform.runLater(() -> {
                Dialogs.showErrorMessage("Connection failed",
                        "Please check that the samapi server (v0.4 and above) is running and the URL is correct.");
            });
        });
        pool.submit(task);
        currentTasks.add(task);
        task.stateProperty().addListener((observable, oldValue, newValue) -> taskStateChange(task, newValue));
    }

    /**
     * Submit a task to fetch the available weights.
     * 
     * @param samType
     */
    public void submitFetchWeightsTask(SAMType samType) {
        SAMFetchWeightsTask task = SAMFetchWeightsTask.builder()
                .serverURL(serverURLProperty.get())
                .verifySSL(verifySSLProperty.get())
                .samType(samType)
                .build();
        task.setOnSucceeded(event -> {
            List<SAMWeights> weights = task.getValue();
            if (weights != null) {
                Platform.runLater(() -> {
                    availableWeightsList.clear();
                    availableWeightsList.addAll(weights);
                });
            }
        });
        submitTask(task);
    }

    /**
     * Submit a task to cancel the current download.
     */
    public void submitCancelDownloadTask() {
        SAMCancelDownloadTask task = SAMCancelDownloadTask.builder()
                .serverURL(serverURLProperty.get())
                .verifySSL(verifySSLProperty.get())
                .build();
        submitTask(task);
    }

    /**
     * Submit a task to get the progress of the current download.
     */
    public SAMProgressTask submitProgressTask() {
        SAMProgressTask task = SAMProgressTask.builder()
                .serverURL(serverURLProperty.get())
                .verifySSL(verifySSLProperty.get())
                .build();
        submitTask(task);
        return task;
    }

    /**
     * Submit a task to register the selected weights.
     */
    public void submitRegisterWeightsTask(SAMWeights samWeights, SAMProgressTask progressTask) {
        SAMRegisterWeightsTask task = SAMRegisterWeightsTask.builder()
                .serverURL(serverURLProperty.get())
                .verifySSL(verifySSLProperty.get())
                .samType(samWeights.getType())
                .name(samWeights.getName())
                .url(samWeights.getUrl())
                .build();
        String title = "Register SAM weights";
        task.setOnSucceeded(event -> {
            String message = task.getValue();
            if (message != null) {
                Platform.runLater(() -> {
                    Dialogs.showMessageDialog(title, message);
                });
            }
            progressTask.cancel();
            submitFetchWeightsTask(samTypeRegisterProperty.get());
        });
        task.setOnCancelled(event -> {
            String message = task.getValue();
            if (message != null) {
                Platform.runLater(() -> {
                    Dialogs.showMessageDialog(title, message);
                });
            }
            progressTask.cancel();
        });
        task.setOnFailed(event -> {
            String message = task.getValue();
            if (message != null) {
                Platform.runLater(() -> {
                    Dialogs.showMessageDialog(title, message);
                });
            }
            progressTask.cancel();
        });
        submitTask(task);
    }

    /**
     * Submit a task to run detection.
     * 
     * @param foregroundObjects
     * @param backgroundObjects
     */
    private void submitDetectionTask(List<PathObject> foregroundObjects, List<PathObject> backgroundObjects) {
        if (foregroundObjects == null || foregroundObjects.isEmpty()) {
            logger.warn("Cannot submit task - foreground objects must not be empty!");
            return;
        }
        logger.info("Submitting task for {} foreground and {} background objects",
                foregroundObjects.size(), backgroundObjects.size());
        ImageServer<BufferedImage> renderedServer;
        try {
            renderedServer = Utils.createRenderedServer(qupath.getViewer());
        } catch (IOException e) {
            logger.error("Failed to create rendered server", e);
            updateInfoTextWithError("Failed to create rendered server: " + e.getMessage());
            return;
        }
        RegionRequest regionRequest = Utils.getViewerRegion(qupath.getViewer(), renderedServer);
        SAMDetectionTask task = SAMDetectionTask.builder(qupath.getViewer())
                .server(renderedServer)
                .regionRequest(regionRequest)
                .serverURL(serverURLProperty.get())
                .verifySSL(verifySSLProperty.get())
                .model(samTypeProperty.get())
                .outputType(outputTypeProperty.get())
                .setName(setNamesProperty.get())
                .setRandomColor(useRandomColorsProperty.get())
                .checkpointUrl(selectedWeightsProperty.get().getUrl())
                .addForegroundPrompts(foregroundObjects)
                .addBackgroundPrompts(backgroundObjects)
                .build();
        task.setOnSucceeded(event -> {
            List<PathObject> detected = task.getValue();
            if (detected != null) {
                if (!detected.isEmpty()) {
                    Platform.runLater(() -> {
                        PathObjectHierarchy hierarchy = qupath.getViewer().getImageData().getHierarchy();
                        if (!keepPromptsProperty.get()) {
                            // Remove prompt objects in one step
                            hierarchy.getSelectionModel().clearSelection();
                            hierarchy.removeObjects(foregroundObjects, true);
                        }
                        hierarchy.addObjects(detected);
                        hierarchy.getSelectionModel().setSelectedObjects(detected, detected.get(0));
                    });
                } else {
                    logger.warn("No objects detected");
                }
            }
        });
        submitTask(task);

        StringBuilder sbForegroundObjects = new StringBuilder();
        sbForegroundObjects.append("[\n");
        for (PathObject pathObject : foregroundObjects) {
            if (pathObject.getROI() instanceof PointsROI) {
                PointsROI pointsROI = (PointsROI) pathObject.getROI();
                sbForegroundObjects
                        .append("    PathObjects.createAnnotationObject(\n");
                sbForegroundObjects
                        .append("        " + Utils.getGroovyScriptForCreatePointsROI(pointsROI));
                sbForegroundObjects.append(",\n");
                sbForegroundObjects
                        .append("        " + Utils.getGroovyScriptForPathClass(pathObject.getPathClass()) + "\n");
                sbForegroundObjects.append("    ),\n");
            } else if (pathObject.getROI() instanceof RectangleROI) {
                RectangleROI rectangleROI = (RectangleROI) pathObject.getROI();
                sbForegroundObjects
                        .append("    PathObjects.createAnnotationObject(\n");
                sbForegroundObjects
                        .append("        " + Utils.getGroovyScriptForCreateRectangleROI(rectangleROI) + ",\n");
                sbForegroundObjects
                        .append("        " + Utils.getGroovyScriptForPathClass(pathObject.getPathClass()));
                sbForegroundObjects.append("\n");
                sbForegroundObjects.append("    ");
                sbForegroundObjects.append("),");
                sbForegroundObjects.append("\n");
            }
        }
        sbForegroundObjects.append("]");

        StringBuilder sbBackgroundObjects = new StringBuilder();
        if (backgroundObjects.isEmpty()) {
            sbBackgroundObjects.append("[]");
        } else {
            sbBackgroundObjects.append("[\n");
            int i = 0;
            for (PathObject pathObject : backgroundObjects) {
                if (pathObject.getROI() instanceof PointsROI) {
                    if (i++ > 0)
                        sbBackgroundObjects.append(",\n");
                    PointsROI pointsROI = (PointsROI) pathObject.getROI();
                    sbBackgroundObjects
                            .append("    PathObjects.createAnnotationObject(\n");
                    sbBackgroundObjects
                            .append("        " + Utils.getGroovyScriptForCreatePointsROI(pointsROI));
                    sbBackgroundObjects.append(",\n");
                    sbBackgroundObjects
                            .append("        " + Utils.getGroovyScriptForPathClass(pathObject.getPathClass()) + "\n");
                    sbBackgroundObjects.append("    )");
                }
            }
            sbBackgroundObjects.append("]");
        }
        final String cmd = String.format("""
                var foregroundObjects = %s
                var backgroundObjects = %s
                var task = org.elephant.sam.tasks.SAMDetectionTask.builder(getCurrentViewer())
                    .server(org.elephant.sam.Utils.createRenderedServer(getCurrentViewer()))
                    .regionRequest(%s)
                    .serverURL("%s")
                    .verifySSL(%b)
                    .model(%s)
                    .outputType(%s)
                    .setName(%b)
                    .setRandomColor(%b)
                    .checkpointUrl("%s")
                    .addForegroundPrompts(foregroundObjects)
                    .addBackgroundPrompts(backgroundObjects)
                    .build()
                task.setOnSucceeded(event -> {
                    List<PathObject> detected = task.getValue()
                    if (detected != null) {
                        if (!detected.isEmpty()) {
                            Platform.runLater(() -> {
                                PathObjectHierarchy hierarchy = getCurrentHierarchy()
                                hierarchy.addObjects(detected)
                                hierarchy.getSelectionModel().clearSelection()
                                hierarchy.fireHierarchyChangedEvent(this)
                            });
                        } else {
                            print("No objects detected")
                        }
                    }
                });
                Platform.runLater(task)
                """,
                sbForegroundObjects.toString(),
                sbBackgroundObjects.toString(),
                Utils.getGroovyScriptForCreateRegionRequest(regionRequest),
                serverURLProperty.get(),
                verifySSLProperty.get(),
                samTypeProperty.get().getFullyQualifiedName(),
                outputTypeProperty.get().getFullyQualifiedName(),
                setNamesProperty.get(),
                useRandomColorsProperty.get(),
                selectedWeightsProperty.get().getUrl())
                .strip();
        imageDataProperty.get().getHistoryWorkflow().addStep(
                new DefaultScriptableWorkflowStep("SAMDetection", cmd));
    }

    /**
     * Submit a task to run SAM3 detection.
     * 
     * @param textPrompt
     * @param positiveBboxes
     * @param negativeBboxes
     */
    private void submitSAM3DetectionTask(String textPrompt, List<PathObject> positiveBboxes,
            List<PathObject> negativeBboxes) {
        if (textPrompt == null || textPrompt.isEmpty() && (positiveBboxes == null || positiveBboxes.isEmpty())) {
            logger.warn("Cannot submit task - text prompt and positive bboxes must not both be empty!");
            return;
        }
        logger.info("Submitting task for {} positive and {} negative bboxes",
                positiveBboxes.size(), negativeBboxes.size());
        ImageServer<BufferedImage> renderedServer;
        try {
            renderedServer = Utils.createRenderedServer(qupath.getViewer());
        } catch (IOException e) {
            logger.error("Failed to create rendered server", e);
            updateInfoTextWithError("Failed to create rendered server: " + e.getMessage());
            return;
        }
        RegionRequest regionRequest = Utils.getViewerRegion(qupath.getViewer(), renderedServer);
        SAM3DetectionTask task = SAM3DetectionTask.builder(qupath.getViewer())
                .server(renderedServer)
                .regionRequest(regionRequest)
                .serverURL(serverURLProperty.get())
                .verifySSL(verifySSLProperty.get())
                .model(samTypeProperty.get())
                .outputType(outputTypeProperty.get())
                .setName(setNamesProperty.get())
                .checkpointUrl(selectedWeightsProperty.get().getUrl())
                .setRandomColor(useRandomColorsProperty.get())
                .textPrompt(textPrompt)
                .addPositiveBboxes(positiveBboxes)
                .addNegativeBboxes(negativeBboxes)
                .resetPrompts(resetPromptsProperty.get())
                .confidenceThresh(confidenceThreshProperty.get())
                .build();
        task.setOnSucceeded(event -> {
            List<PathObject> detected = task.getValue();
            if (detected != null) {
                if (!detected.isEmpty()) {
                    Platform.runLater(() -> {
                        PathObjectHierarchy hierarchy = qupath.getViewer().getImageData().getHierarchy();
                        if (!keepPromptsProperty.get()) {
                            // Remove prompt objects in one step
                            hierarchy.getSelectionModel().clearSelection();
                            hierarchy.removeObjects(positiveBboxes, true);
                            hierarchy.removeObjects(negativeBboxes, true);
                        }
                        hierarchy.addObjects(detected);
                        hierarchy.getSelectionModel().setSelectedObjects(detected, detected.get(0));
                    });
                } else {
                    logger.warn("No objects detected");
                }
            }
        });
        submitTask(task);

        StringBuilder sbPositiveBboxes = new StringBuilder();
        if (positiveBboxes.isEmpty()) {
            sbPositiveBboxes.append("[]");
        } else {
            sbPositiveBboxes.append("[\n");
            int i = 0;
            for (PathObject pathObject : positiveBboxes) {
                if (pathObject.getROI() instanceof RectangleROI) {
                    if (i++ > 0)
                        sbPositiveBboxes.append(",\n");
                    RectangleROI rectangleROI = (RectangleROI) pathObject.getROI();
                    sbPositiveBboxes
                            .append("    PathObjects.createAnnotationObject(\n");
                    sbPositiveBboxes
                            .append("        " + Utils.getGroovyScriptForCreateRectangleROI(rectangleROI) + ",\n");
                    sbPositiveBboxes
                            .append("        " + Utils.getGroovyScriptForPathClass(pathObject.getPathClass()));
                    sbPositiveBboxes.append("\n");
                    sbPositiveBboxes.append("    ");
                    sbPositiveBboxes.append("),");
                    sbPositiveBboxes.append("\n");
                } else {
                    logger.warn("Skipping positive bbox object with unsupported ROI type: {}",
                            pathObject.getROI().getClass());
                }
            }
            sbPositiveBboxes.append("]");
        }

        StringBuilder sbNegativeBboxes = new StringBuilder();
        if (negativeBboxes.isEmpty()) {
            sbNegativeBboxes.append("[]");
        } else {
            sbNegativeBboxes.append("[\n");
            int i = 0;
            for (PathObject pathObject : negativeBboxes) {
                if (pathObject.getROI() instanceof RectangleROI) {
                    if (i++ > 0)
                        sbNegativeBboxes.append(",\n");
                    RectangleROI rectangleROI = (RectangleROI) pathObject.getROI();
                    sbNegativeBboxes
                            .append("    PathObjects.createAnnotationObject(\n");
                    sbNegativeBboxes
                            .append("        " + Utils.getGroovyScriptForCreateRectangleROI(rectangleROI) + ",\n");
                    sbNegativeBboxes
                            .append("        " + Utils.getGroovyScriptForPathClass(pathObject.getPathClass()));
                    sbNegativeBboxes.append("\n");
                    sbNegativeBboxes.append("    ");
                    sbNegativeBboxes.append("),");
                    sbNegativeBboxes.append("\n");
                } else {
                    logger.warn("Skipping positive bbox object with unsupported ROI type: {}",
                            pathObject.getROI().getClass());
                }
            }
            sbNegativeBboxes.append("]");
        }
        final String cmd = String.format("""
                var positiveBboxes = %s
                var negativeBboxes = %s
                var task = org.elephant.sam.tasks.SAM3DetectionTask.builder(getCurrentViewer())
                    .server(org.elephant.sam.Utils.createRenderedServer(getCurrentViewer()))
                    .regionRequest(%s)
                    .serverURL("%s")
                    .verifySSL(%b)
                    .model(%s)
                    .outputType(%s)
                    .setName(%b)
                    .checkpointUrl("%s")
                    .setRandomColor(%b)
                    .textPrompt("%s")
                    .addPositiveBboxes(positiveBboxes)
                    .addNegativeBboxes(negativeBboxes)
                    .resetPrompts(%b)
                    .confidenceThresh(%f)
                    .build()
                task.setOnSucceeded(event -> {
                    List<PathObject> detected = task.getValue()
                    if (detected != null) {
                        if (!detected.isEmpty()) {
                            Platform.runLater(() -> {
                                PathObjectHierarchy hierarchy = getCurrentHierarchy()
                                hierarchy.addObjects(detected)
                                hierarchy.getSelectionModel().clearSelection()
                                hierarchy.fireHierarchyChangedEvent(this)
                            });
                        } else {
                            print("No objects detected")
                        }
                    }
                });
                Platform.runLater(task)
                """,
                sbPositiveBboxes.toString(),
                sbNegativeBboxes.toString(),
                Utils.getGroovyScriptForCreateRegionRequest(regionRequest),
                serverURLProperty.get(),
                verifySSLProperty.get(),
                samTypeProperty.get().getFullyQualifiedName(),
                outputTypeProperty.get().getFullyQualifiedName(),
                setNamesProperty.get(),
                selectedWeightsProperty.get().getUrl(),
                useRandomColorsProperty.get(),
                textPrompt,
                resetPromptsProperty.get(),
                confidenceThreshProperty.get())
                .strip();
        imageDataProperty.get().getHistoryWorkflow().addStep(
                new DefaultScriptableWorkflowStep("SAMDetection", cmd));
    }

    /**
     * Handle a change in task state.
     * 
     * @param task
     * @param newValue
     */
    private void taskStateChange(Task<?> task, Worker.State newValue) {
        switch (newValue) {
            case SUCCEEDED:
                logger.debug("Task completed successfully");
                currentTasks.remove(task);
                break;
            case CANCELLED:
                logger.info("Task cancelled");
                currentTasks.remove(task);
                if (task.getException() != null) {
                    logger.warn("Task failed: {}", task, task.getException());
                    updateInfoTextWithError("Task cancelled with exception " + task.getException() +
                            "\nSee log for details.");
                } else {
                    updateInfoText("Task cancelled");
                }
                break;
            case FAILED:
                currentTasks.remove(task);
                if (task.getException() != null) {
                    logger.warn("Task failed: {}", task, task.getException());
                    updateInfoTextWithError("Task failed with exception " + task.getException() +
                            "\nSee log for details.");
                } else {
                    updateInfoTextWithError("Task failed!");
                }
                break;
            case RUNNING:
                logger.trace("Task running");
                break;
            case SCHEDULED:
                logger.trace("Task scheduled");
                break;
            default:
                logger.debug("Task state changed to {}", newValue);
        }
    }

    /**
     * Handle a change in image data.
     * 
     * @param observable
     * @param oldValue
     * @param newValue
     */
    private void imageDataChanged(ObservableValue<? extends ImageData<BufferedImage>> observable,
            ImageData<BufferedImage> oldValue, ImageData<BufferedImage> newValue) {
        if (oldValue != null)
            oldValue.getHierarchy().removeListener(hierarchyListener);
        if (newValue != null)
            newValue.getHierarchy().addListener(hierarchyListener);
    }

    /**
     * Handle a change in the hierarchy.
     * 
     * @param event
     */
    private void hierarchyChanged(PathObjectHierarchyEvent event) {
        if (event.isChanging() || event.getEventType() != PathObjectHierarchyEvent.HierarchyEventType.ADDED)
            return;
        if (forceBackgroundPointsProperty.get()) {
            // If we are forcing background points, then we need to make sure that any new
            // points are an 'ignored' class
            for (PathObject pathObject : event.getChangedObjects()) {
                if (pathObject.getROI() instanceof PointsROI || pathObject.getROI() instanceof RectangleROI) {
                    PathClass currentClass = pathObject.getPathClass();
                    if (currentClass == null || !PathClassTools.isIgnoredClass(currentClass))
                        pathObject.setPathClass(PathClass.StandardPathClasses.IGNORE);
                }
            }
        }
        if (liveModeProperty.get()) {
            if (getSamTypeProperty().get().isSAM3Compatible()) {
                String textPrompt = getTextPromptProperty().get();
                List<PathObject> foregroundObjects = getSAM3ForegroundObjects(event.getChangedObjects());
                List<PathObject> backgroundObjects = getSAM3BackgroundObjects(event.getChangedObjects());
                submitSAM3DetectionTask(textPrompt, foregroundObjects, backgroundObjects);
            } else {
                List<PathObject> foregroundObjects = getForegroundObjects(event.getChangedObjects());
                if (!foregroundObjects.isEmpty()) {
                    List<PathObject> backgroundObjects = getBackgroundObjects(
                            event.getHierarchy().getAnnotationObjects());
                    submitDetectionTask(foregroundObjects, backgroundObjects);
                }
            }
        }
    }

    /**
     * Get the foreground objects from a collection of path objects.
     * 
     * @param pathObjects
     * @return the list of foreground objects
     */
    private List<PathObject> getForegroundObjects(Collection<? extends PathObject> pathObjects) {
        return pathObjects
                .stream()
                .filter(this::isForegroundObject)
                .collect(Collectors.toList());
    }

    /**
     * Get the foreground objects from a collection of path objects.
     * 
     * @param pathObjects
     * @return the list of foreground objects
     */
    private List<PathObject> getSAM3ForegroundObjects(Collection<? extends PathObject> pathObjects) {
        return pathObjects
                .stream()
                .filter(this::isSAM3ForegroundObject)
                .collect(Collectors.toList());
    }

    /**
     * Get the background objects from a collection of path objects.
     * 
     * @param pathObjects
     * @return the list of background objects
     */
    private List<PathObject> getBackgroundObjects(Collection<? extends PathObject> pathObjects) {
        return pathObjects
                .stream()
                .filter(this::isBackgroundObject)
                .collect(Collectors.toList());
    }

    /**
     * Get the background objects from a collection of path objects.
     * 
     * @param pathObjects
     * @return the list of background objects
     */
    private List<PathObject> getSAM3BackgroundObjects(Collection<? extends PathObject> pathObjects) {
        return pathObjects
                .stream()
                .filter(this::isSAM3BackgroundObject)
                .collect(Collectors.toList());
    }

    /**
     * Get the top-level annotation objects from a collection of path objects.
     * 
     * @param pathObjects
     * @return the list of foreground objects
     */
    private List<PathObject> getTopLevelObjects(Collection<? extends PathObject> pathObjects) {
        return pathObjects
                .stream()
                .filter(pathObject -> pathObject.getLevel() == 1)
                .collect(Collectors.toList());
    }

    /**
     * Test if an object has a permitted line ROI.
     * 
     * @param pathObject
     * @return true if the object has a permitted line ROI
     */
    private boolean hasPermittedLineROI(PathObject pathObject) {
        return useLinesForPointsProperty.get() && Utils.hasLineROI(pathObject);
    }

    /**
     * Test if an object could act as a foreground prompt for detection.
     * 
     * @param pathObject
     * @return
     */
    private boolean isForegroundObject(PathObject pathObject) {
        return Utils.isPotentialPromptObject(pathObject)
                && !isBackgroundObject(pathObject)
                && (Utils.hasRectangleROI(pathObject) || Utils.hasPointsROI(pathObject)
                        || hasPermittedLineROI(pathObject));
    }

    /**
     * Test if an object could act as a foreground prompt for SAM3 detection.
     * 
     * @param pathObject
     * @return
     */
    private boolean isSAM3ForegroundObject(PathObject pathObject) {
        return Utils.isPotentialPromptObject(pathObject)
                && !isSAM3BackgroundObject(pathObject)
                && Utils.hasRectangleROI(pathObject);
    }

    /**
     * Test if an object could act as a background prompt for detection.
     * 
     * @param pathObject
     * @return
     */
    private boolean isBackgroundObject(PathObject pathObject) {
        return Utils.isPotentialPromptObject(pathObject)
                && (Utils.hasPointsROI(pathObject) || hasPermittedLineROI(pathObject))
                && pathObject.getPathClass() != null &&
                PathClassTools.isIgnoredClass(pathObject.getPathClass());
    }

    /**
     * Test if an object could act as a background prompt for detection.
     * 
     * @param pathObject
     * @return
     */
    private boolean isSAM3BackgroundObject(PathObject pathObject) {
        return Utils.isPotentialPromptObject(pathObject)
                && Utils.hasRectangleROI(pathObject)
                && pathObject.getPathClass() != null &&
                PathClassTools.isIgnoredClass(pathObject.getPathClass());
    }
}
