package org.elephant.sam;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;
import javafx.concurrent.Worker;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Separator;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import org.controlsfx.control.action.Action;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.common.ThreadTools;
import qupath.lib.gui.ActionTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.tools.PathTool;
import qupath.lib.gui.viewer.tools.PathTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassTools;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyEvent;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyListener;
import qupath.lib.roi.PointsROI;
import qupath.lib.roi.RectangleROI;
import qupath.lib.roi.interfaces.ROI;

import java.awt.image.BufferedImage;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Main QuPath command to run the Segment Anything Model for assisted annotation.
 * See https://github.com/ksugar/qupath-extension-sam for details.
 */
public class SAMCommand implements Runnable {

    private final static Logger logger = LoggerFactory.getLogger(SAMCommand.class);

    private final static double H_GAP = 5;

    private final static String TITLE = "SAM";

    private Stage stage;

    private final QuPathGUI qupath;

    /**
     * Server for detection - see https://github.com/ksugar/samapi
     */
    private final StringProperty serverURL = PathPrefs.createPersistentPreference(
            "ext.SAM.serverUrl", "http://localhost:8000/sam/");

    /**
     * Selected model for detection
     */
    private final ObjectProperty<SAMModel> model = PathPrefs.createPersistentPreference(
            "ext.SAM.model", SAMModel.VIT_L, SAMModel.class);

    /**
     * Selected output type
     */
    private final ObjectProperty<SAMOutput> outputType = PathPrefs.createPersistentPreference(
            "ext.SAM.outputType", SAMOutput.SINGLE_MASK, SAMOutput.class);

    /**
     * Optionally allow line ROIs to be used as an alternative to points.
     * Defaults to false, as this tends to get too many points.
     */
    private final BooleanProperty useLinesForPoints = PathPrefs.createPersistentPreference(
            "ext.SAM.permitLines", false);

    /**
     * Optionally keep the prompt objects after detection.
     */
    private final BooleanProperty keepPrompts = PathPrefs.createPersistentPreference(
            "ext.SAM.keepPrompts", false);

    /**
     * Set the names of new DAM detected objects.
     */
    private final BooleanProperty setNames = PathPrefs.createPersistentPreference(
            "ext.SAM.setNames", true);

    /**
     * Assign random colors to new SAM objects.
     */
    private final BooleanProperty useRandomColors = PathPrefs.createPersistentPreference(
            "ext.SAM.useRandomColors", true);

    /**
     * Whether live mode is turned on, to make detections as annotations are added
     */
    private final BooleanProperty liveMode = new SimpleBooleanProperty(false);

    /**
     * Current image data
     */
    private final ObjectProperty<ImageData<BufferedImage>> imageDataProperty = new SimpleObjectProperty<>();

    /**
     * The task currently running
     */
    private final ObservableSet<SAMDetectionTask> currentTasks = FXCollections.observableSet();

    /**
     * Currently awaiting a detection from the server
     */
    private final BooleanBinding isDetecting = Bindings.isNotEmpty(currentTasks);

    /**
     * Progress of the current detection.
     */
    private final DoubleProperty detectionProgress = new SimpleDoubleProperty(1.0);

    /**
     * Flag to indicate that running isn't possible right now
     */
    private final BooleanBinding disableRunning = imageDataProperty.isNull()
            .or(serverURL.isEmpty())
            .or(model.isNull())
            .or(isDetecting);

    /**
     * Override the classification of new point annotations to be an ignored class.
     */
    private BooleanProperty forceBackgroundPoints = new SimpleBooleanProperty(false);

    private PathObjectHierarchyListener hierarchyListener = this::hierarchyChanged;

    private ChangeListener<ImageData<BufferedImage>> imageDataListener = this::imageDataChanged;

    // We need to turn off the multipoint tool when running live detection, but restore it afterwards
    private boolean previousMultipointValue = PathPrefs.multipointToolProperty().get();

    private ExecutorService pool;

    /**
     * Cache an RGB image server for the current image data.
     */
    private ImageServer<BufferedImage> rgbImageServer;

    /**
     * String to contain help text for the user
     */
    private StringProperty infoText = new SimpleStringProperty();

    /**
     * Timestamp of the last info text representing an error. This can be used for styling,
     * and/or to ensure that errors remain visible for a minimum amount of time.
     */
    private LongProperty infoTextErrorTimestamp = new SimpleLongProperty(0);


    /**
     * Constructor.
     * @param qupath main QuPath instance
     */
    public SAMCommand(QuPathGUI qupath) {
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
        liveMode.addListener((observable, oldValue, newValue) -> {
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
        stage.show();
        if (creatingStage) {
            fixStageSizeOnFirstShow(stage);
        }
    }

    private static void fixStageSizeOnFirstShow(Stage stage) {
        stage.sizeToScene();
        // Make slightly wider, as workaround for column constraints not including h-gap
        stage.setWidth(stage.getWidth() + H_GAP*2);
        // Set other size/position variables so they are retained after stage is hidden
        stage.setHeight(stage.getHeight());
        stage.setX(stage.getX());
        stage.setY(stage.getY());
    }

    private void hideStage() {
        this.liveMode.set(false);
        this.imageDataProperty.unbind();
        this.imageDataProperty.removeListener(imageDataListener); // To be sure...
        stage.hide();
    }

    private void runOnce() {
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
        List<PathObject> foregroundObjects = getForegroundObjects(selectedObjects);
        if (!foregroundObjects.isEmpty()) {
            List<PathObject> backgroundObjects = getBackgroundObjects(selectedObjects);
            submitTask(foregroundObjects, backgroundObjects);
        } else {
            updateInfoText("No foreground objects to use for detection");
        }
    }

    private void startLiveMode() {
        // Turn off the multipoint tool, so that each new point is a new object
        previousMultipointValue = PathPrefs.multipointToolProperty().get();
        PathPrefs.multipointToolProperty().set(false);
        // Try to run once with the current selected objects
        runOnce();
    }

    private void stopLiveMode() {
        PathPrefs.multipointToolProperty().set(previousMultipointValue);
    }



    private void imageDataChanged(ObservableValue<? extends ImageData<BufferedImage>> observable,
                                 ImageData<BufferedImage> oldValue, ImageData<BufferedImage> newValue) {
        if (oldValue != null)
            oldValue.getHierarchy().removeListener(hierarchyListener);
        if (newValue != null)
            newValue.getHierarchy().addListener(hierarchyListener);
    }

    private void hierarchyChanged(PathObjectHierarchyEvent event) {
        if (event.isChanging() || event.getEventType() != PathObjectHierarchyEvent.HierarchyEventType.ADDED)
            return;
        if (forceBackgroundPoints.get()) {
            // If we are forcing background points, then we need to make sure that any new points are an 'ignored' class
            for (PathObject pathObject : event.getChangedObjects()) {
                if (pathObject.getROI() instanceof PointsROI) {
                    PathClass currentClass = pathObject.getPathClass();
                    if (currentClass == null || !PathClassTools.isIgnoredClass(currentClass))
                        pathObject.setPathClass(PathClass.StandardPathClasses.IGNORE);
                }
            }
        }
        if (liveMode.get()) {
            List<PathObject> foregroundObjects = getForegroundObjects(event.getChangedObjects());
            if (!foregroundObjects.isEmpty()) {
                List<PathObject> backgroundObjects = getBackgroundObjects(event.getHierarchy().getAnnotationObjects());
                submitTask(foregroundObjects, backgroundObjects);
            }
        }
    }

    private List<PathObject> getForegroundObjects(Collection<? extends PathObject> pathObjects) {
        return pathObjects
                .stream()
                .filter(this::isForegroundObject)
                .collect(Collectors.toList());
    }

    private List<PathObject> getBackgroundObjects(Collection<? extends PathObject> pathObjects) {
        return pathObjects
                .stream()
                .filter(this::isBackgroundObject)
                .collect(Collectors.toList());
    }

    private void submitTask(List<PathObject> foregroundObjects, List<PathObject> backgroundObjects) {
        if (foregroundObjects == null || foregroundObjects.isEmpty()) {
            logger.warn("Cannot submit task - foreground objects must not be empty!");
            return;
        }
        logger.info("Submitting task for {} foreground and {} background objects",
                foregroundObjects.size(), backgroundObjects.size());
        SAMDetectionTask task = SAMDetectionTask.builder(qupath.getViewer())
                        .addForegroundPrompts(foregroundObjects)
                        .addBackgroundPrompts(backgroundObjects)
                        .serverURL(serverURL.get())
                        .model(model.get())
                        .outputType(outputType.get())
                        .keepPromptObjects(keepPrompts.get())
                        .setName(setNames.get())
                        .setRandomColor(useRandomColors.get())
                        .build();
        pool.submit(task);
        currentTasks.add(task);
        task.stateProperty().addListener((observable, oldValue, newValue) -> taskStateChange(task, newValue));
    }

    private void taskStateChange(SAMDetectionTask task, Worker.State newValue) {
        switch (newValue) {
            case SUCCEEDED:
                logger.debug("Task completed successfully");
                currentTasks.remove(task);
                break;
            case CANCELLED:
                logger.info("Task cancelled");
                currentTasks.remove(task);
                updateInfoTextWithError("Detection failed with exception " + task.getException() +
                        "\nSee log for details.");
                break;
            case FAILED:
                currentTasks.remove(task);
                if (task.getException() != null) {
                    logger.warn("Task failed: {}", task, task.getException());
                    updateInfoTextWithError("Detection failed with exception " + task.getException() +
                            "\nSee log for details.");
                } else {
                    updateInfoTextWithError("Detection failed!");
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

    private void updateInfoTextWithError(String message) {
        infoText.set(message);
        infoTextErrorTimestamp.set(System.currentTimeMillis());
    }

    private void updateInfoText(String message) {
        // Don't overwrite an error message until 5 seconds have passed & we have something worthwhile to show
        long lastErrorTimestamp = infoTextErrorTimestamp.get();
        boolean hasMessage = message != null && !message.isEmpty();
        if (System.currentTimeMillis() - lastErrorTimestamp < 5000 || (!hasMessage && lastErrorTimestamp > 0))
            return;
        infoText.set(message);
        infoTextErrorTimestamp.set(0);
    }

    private static boolean hasLineROI(PathObject pathObject) {
        return pathObject.hasROI() && pathObject.getROI().getRoiType() == ROI.RoiType.LINE;
    }

    private boolean hasPermittedLineROI(PathObject pathObject) {
        return useLinesForPoints.get() && hasLineROI(pathObject);
    }

    private static boolean hasRectangleROI(PathObject pathObject) {
        return pathObject.getROI() instanceof RectangleROI;
    }

    private static boolean hasPointsROI(PathObject pathObject) {
        return pathObject.hasROI() && pathObject.getROI().getRoiType() == ROI.RoiType.POINT;
    }

    /**
     * Any annotation object with a non-empty ROI could potentially act as a prompt for detection.
     * @param pathObject
     * @return
     */
    private boolean isPotentialPromptObject(PathObject pathObject) {
        return pathObject.isAnnotation() && pathObject.hasROI() && !pathObject.getROI().isEmpty();
    }

    /**
     * Test if an object could act as a foreground prompt for detection.
     * @param pathObject
     * @return
     */
    private boolean isForegroundObject(PathObject pathObject) {
        if (isPotentialPromptObject(pathObject) && !isBackgroundObject(pathObject)) {
            return hasRectangleROI(pathObject) || hasPointsROI(pathObject) || hasPermittedLineROI(pathObject);
        }
        return false;
    }

    /**
     * Test if an object could act as a background prompt for detection.
     * @param pathObject
     * @return
     */
    private boolean isBackgroundObject(PathObject pathObject) {
        return isPotentialPromptObject(pathObject) && (hasPointsROI(pathObject) || hasPermittedLineROI(pathObject))
                && pathObject.getPathClass() != null &&
                PathClassTools.isIgnoredClass(pathObject.getPathClass());
    }


    private Stage createStage() {
        Stage stage = new Stage();
        Pane pane = createPane();
        Scene scene = new Scene(pane);
        stage.setScene(scene);
        Platform.runLater(() -> {
                stage.sizeToScene();
                stage.setWidth(stage.getWidth() + H_GAP*2);
                stage.setX(stage.getX());
                stage.setY(stage.getY());
                stage.setHeight(stage.getHeight());
        });
        stage.setResizable(false);
        stage.setTitle(TITLE);
        stage.initOwner(qupath.getStage());
        stage.setOnCloseRequest(event -> {
            hideStage();
            event.consume();
        });
        return stage;
    }


    private Pane createPane() {
        GridPane pane = new GridPane();
        int row = 0;

        addInstructionPrompt(pane, row++);

        addSeparator(pane, row++);

        addServerPrompt(pane, row++);
        addModelPrompt(pane, row++);

        addSeparator(pane, row++);

        addDrawingTools(pane, row++);

        addSeparator(pane, row++);

        addOutputTypePrompt(pane, row++);
        addCheckboxes(pane, row++);

        addSeparator(pane, row++);

        addButtons(pane, row++);

        addSeparator(pane, row++);

        addInfoPane(pane, row++);
        pane.addEventFilter(MouseEvent.MOUSE_MOVED, this::handleMouseMoved);

        pane.setHgap(H_GAP);
        pane.setVgap(H_GAP);
        pane.setPadding(new Insets(10.0));
        pane.setMaxWidth(Double.MAX_VALUE);

        System.err.println("COUNT: " + pane.getColumnCount());
        for (int i = 0; i < pane.getColumnCount(); i++) {
            ColumnConstraints constraints = new ColumnConstraints();
            if (i == 1)
                constraints.setHgrow(Priority.ALWAYS);
            pane.getColumnConstraints().add(constraints);
        }
        return pane;
    }

    private void handleMouseMoved(MouseEvent event) {
        Node node = event.getPickResult().getIntersectedNode();
        while (node != null) {
            if (node instanceof Control) {
                Tooltip tooltip = ((Control) node).getTooltip();
                if (tooltip != null) {
                    updateInfoText(tooltip.getText());
                    return;
                }
            }
            node = node.getParent();
        }
        // Reset the info text, unless it shows an error
        updateInfoText("");
    }


    private void addInstructionPrompt(GridPane pane, int row) {
        ProgressIndicator progressIndicator = new ProgressIndicator();
        progressIndicator.setPrefSize(32, 32);
        progressIndicator.progressProperty().bind(detectionProgress);
        detectionProgress.set(ProgressIndicator.INDETERMINATE_PROGRESS);
        progressIndicator.visibleProperty().bind(isDetecting);

        Label label = new Label("AI-assisted annotation using\nMeta's 'Segment Anything Model'");
        label.setWrapText(true);
        label.setAlignment(Pos.CENTER);
        label.setTextAlignment(TextAlignment.CENTER);

        BorderPane instructionPane = new BorderPane(label);
        instructionPane.setRight(progressIndicator);
        pane.add(instructionPane, 0, row, GridPane.REMAINING, 1);
    }

    private void addSeparator(GridPane pane, int row) {
        Separator separator = new Separator();
        separator.setPadding(new Insets(5.0));
        pane.add(separator, 0, row, GridPane.REMAINING, 1);
    }

    private void addServerPrompt(GridPane pane, int row) {
        Label labelUrl = new Label();
        labelUrl.textProperty().bind(serverURL);
        labelUrl.setMaxWidth(Double.MAX_VALUE);
        labelUrl.setTextOverrun(OverrunStyle.LEADING_ELLIPSIS);
        GridPane.setFillWidth(labelUrl, true);
        GridPane.setHgrow(labelUrl, Priority.ALWAYS);
        Tooltip tooltip = new Tooltip("The server running SAM detection.\n" +
                "This must be set up and running before any detection can happen.");
        labelUrl.setTooltip(tooltip);

        Button btnEdit = new Button("Edit");
        btnEdit.setMaxWidth(Double.MAX_VALUE);
        btnEdit.setOnAction(event -> promptToSetUrl());
        btnEdit.setTooltip(new Tooltip("Edit the server URL"));

        Label label = new Label("Server");
        label.setTooltip(tooltip);
        label.setLabelFor(labelUrl);
        pane.add(label, 0, row);
        pane.add(labelUrl, 1, row);
        pane.add(btnEdit, 2, row);
    }

    private void promptToSetUrl() {
        String currentURL = serverURL.get();
        TextInputDialog dialog = new TextInputDialog(currentURL);
        dialog.setHeaderText("Input SAM server URL");
        dialog.getEditor().setPrefColumnCount(32);
        String newURL = dialog.showAndWait().orElse(currentURL);
        if (newURL == null || newURL.isBlank() || newURL.equals(currentURL))
            return;
        serverURL.set(newURL);
    }

    private void addModelPrompt(GridPane pane, int row) {
        ComboBox<SAMModel> combo = new ComboBox<>();
        combo.getItems().setAll(SAMModel.values());
        combo.getSelectionModel().select(model.get());
        model.bind(combo.getSelectionModel().selectedItemProperty());
        combo.setMaxWidth(Double.MAX_VALUE);
        Tooltip tooltip = new Tooltip("The SAM model to use for detection.\n" +
                "These differ in size and speed (and maybe accuracy)");
        combo.setTooltip(tooltip);
        GridPane.setFillWidth(combo, true);

        Label label = new Label("SAM model");
        label.setLabelFor(combo);
        label.setTooltip(tooltip);
        pane.add(label, 0, row);
        pane.add(combo, 1, row, GridPane.REMAINING, 1);
    }

    private void addDrawingTools(GridPane pane, int row) {
        Action actionRectangle = qupath.getToolAction(PathTools.RECTANGLE);
        ToggleButton btnRectangle = ActionTools.createToggleButton(actionRectangle, true);

        Action actionPoints = qupath.getToolAction(PathTools.POINTS);
        ToggleButton btnPoints = ActionTools.createToggleButton(actionPoints, true);

        Action actionMove = qupath.getToolAction(PathTools.MOVE);
        ToggleButton btnMove = ActionTools.createToggleButton(actionMove, true);

        Label label = new Label("Draw prompts");
        label.setTooltip(new Tooltip("Draw foreground or background prompts.\n" +
                "Please select a rectangle or points tool to start."));
        RadioButton radioForeground = new RadioButton("Foreground");
        radioForeground.setTooltip(new Tooltip(
                "Draw foreground prompt.\n" +
                        "Requires rectangle or point tool to be selected."));
        radioForeground.disableProperty().bind(
                qupath.selectedToolProperty().isNotEqualTo(PathTools.POINTS).and(
                        qupath.selectedToolProperty().isNotEqualTo(PathTools.RECTANGLE)
                ));
        RadioButton radioBackground = new RadioButton("Background");
        radioBackground.setTooltip(new Tooltip(
                "Draw background prompt.\n" +
                        "Requires point tool to be selected."));
        radioBackground.disableProperty().bind(qupath.selectedToolProperty().isNotEqualTo(PathTools.POINTS));
        ObjectProperty<PathClass> autoAnnotation = PathPrefs.autoSetAnnotationClassProperty();
        if (autoAnnotation.get() != null && PathClassTools.isIgnoredClass(autoAnnotation.get()))
            radioBackground.setSelected(true);
        else
            radioForeground.setSelected(true);
        ToggleGroup group = new ToggleGroup();
        group.getToggles().setAll(radioForeground, radioBackground);
        forceBackgroundPoints.bind(group.selectedToggleProperty().isEqualTo(radioBackground));

        VBox vbox = new VBox(radioForeground, radioBackground);
        vbox.setSpacing(H_GAP);
        vbox.setMaxWidth(Double.MAX_VALUE);
        GridPane.setFillWidth(vbox, true);
        int col = 0;
        GridPane drawingPane = new GridPane();
        drawingPane.add(label, col++, 0);
        drawingPane.add(vbox, col++, 0);
        drawingPane.add(btnRectangle, col++, 0);
        drawingPane.add(btnPoints, col++, 0);
        drawingPane.add(new Separator(Orientation.VERTICAL), col++, 0);
        drawingPane.add(btnMove, col++, 0);
        drawingPane.setHgap(H_GAP);

        pane.add(drawingPane, 0, row, GridPane.REMAINING, 1);
    }

    private void addOutputTypePrompt(GridPane pane, int row) {
        ComboBox<SAMOutput> combo = new ComboBox<>();
        combo.getItems().setAll(SAMOutput.values());
        combo.getSelectionModel().select(outputType.get());
        Tooltip tooltip = new Tooltip("Choose how many masks SAM should create (1 or 3).\n" +
                "For multiple masks, you can specify which is kept.");
        combo.setTooltip(tooltip);
        outputType.bind(combo.getSelectionModel().selectedItemProperty());
        combo.setMaxWidth(Double.MAX_VALUE);
        GridPane.setFillWidth(combo, true);

        Label label = new Label("Output type");
        label.setLabelFor(combo);
        label.setTooltip(tooltip);
        pane.add(label, 0, row);
        pane.add(combo, 1, row, GridPane.REMAINING, 1);
    }

    private void addCheckboxes(GridPane pane, int row) {
        CheckBox cbRandomColors = createCheckbox("Assign random colors",
                useRandomColors,
                "Assign random colors to new (unclassified) objects created by SAM");

        CheckBox cbAssignNames = createCheckbox("Assign names",
                setNames,
                "Assign names to identify new objects as created by SAM, including quality scores");

        CheckBox cbKeepPrompts = createCheckbox("Keep prompts",
                keepPrompts,
                "Keep the foreground prompts after detection.\n" +
                        "By default these are deleted.");

        CheckBox cbDisplayNames = createCheckbox("Display names",
                qupath.getOverlayOptions().showNamesProperty(),
                "Display the annotation names in the viewer\n(this is a global preference)");

        GridPane checkboxPane = createColumnPane(cbRandomColors, cbAssignNames);
        checkboxPane.add(cbKeepPrompts, 0, 1, GridPane.REMAINING, 1);
        checkboxPane.add(cbDisplayNames, 1, 1, GridPane.REMAINING, 1);
        checkboxPane.setVgap(H_GAP);

        pane.add(checkboxPane, 0, row, GridPane.REMAINING, 1);
        pane.setMinSize(GridPane.USE_COMPUTED_SIZE, GridPane.USE_COMPUTED_SIZE);
        pane.setMaxSize(Double.MAX_VALUE, GridPane.USE_COMPUTED_SIZE);
    }

    private static CheckBox createCheckbox(String text, BooleanProperty property, String tooltip) {
        CheckBox cb = new CheckBox(text);
        cb.selectedProperty().bindBidirectional(property);
        cb.setMaxWidth(Double.MAX_VALUE);
        GridPane.setFillWidth(cb, true);
        if (tooltip != null)
            cb.setTooltip(new Tooltip(tooltip));
        return cb;
    }


    private void addButtons(GridPane pane, int row) {
        Button btnRunOnce = new Button("Run for selected");
        btnRunOnce.setOnAction(event -> runOnce());
        btnRunOnce.setMaxWidth(Double.MAX_VALUE);
        btnRunOnce.setTooltip(new Tooltip(
                "Run the model once using the selected annotations (points or rectangles)"));

        ToggleButton btnLiveMode = new ToggleButton("Live mode");
        liveMode.bindBidirectional(btnLiveMode.selectedProperty());
        btnLiveMode.disableProperty().bind(disableRunning);
        btnLiveMode.setMaxWidth(Double.MAX_VALUE);
        btnLiveMode.setTooltip(new Tooltip(
                "Turn on live detection to run the model on every new foreground annotation (point or rectangle)"));

        btnRunOnce.disableProperty().bind(disableRunning.or(liveMode));

        Pane buttonPane = createColumnPane(btnRunOnce, btnLiveMode);
        pane.add(buttonPane, 0, row, GridPane.REMAINING, 1);
    }


    private void addInfoPane(GridPane pane, int row) {
        Label labelInfo = new Label();
        labelInfo.setMaxWidth(Double.MAX_VALUE);
        labelInfo.setWrapText(true);
        labelInfo.setAlignment(Pos.CENTER);
        labelInfo.setTextAlignment(TextAlignment.CENTER);
        labelInfo.textProperty().bind(infoText);
        labelInfo.setTextOverrun(OverrunStyle.ELLIPSIS);
        labelInfo.setPrefHeight(64);
        labelInfo.styleProperty().bind(Bindings.createStringBinding(() -> {
            if (infoTextErrorTimestamp.get() > 0)
                return "-fx-text-fill: -qp-script-error-color;";
            else
                return null;
        }, infoTextErrorTimestamp));
        GridPane.setFillWidth(labelInfo, true);
        pane.add(labelInfo, 0, row, GridPane.REMAINING, 1);
    }

    private static GridPane createColumnPane(Node... nodes) {
        GridPane pane = new GridPane();
        pane.setMaxWidth(Double.MAX_VALUE);
        pane.setHgap(H_GAP);
        for (int i = 0; i < nodes.length; i++) {
            pane.add(nodes[i], i, 0);
            pane.getColumnConstraints().add(createPercentageConstraint(100.0 / nodes.length));
        }
        GridPane.setFillWidth(pane, true);
        GridPane.setHgrow(pane, Priority.ALWAYS);
        return pane;
    }

    private static ColumnConstraints createPercentageConstraint(double percentage) {
        ColumnConstraints constraint = new ColumnConstraints();
        constraint.setPercentWidth(percentage);
        constraint.setHgrow(Priority.ALWAYS);
        return constraint;
    }

}
