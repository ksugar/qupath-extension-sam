package org.elephant.sam;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;
import javafx.concurrent.Worker;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.common.ThreadTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * QuPath command to run the Segment Anything Model for automatic mask generator.
 * See https://github.com/ksugar/qupath-extension-sam for details.
 */
public class SAMAutoMaskCommand implements Runnable {

    private final static Logger logger = LoggerFactory.getLogger(SAMAutoMaskCommand.class);

    private final static double H_GAP = 5;

    private final static String TITLE = "SAM AutoMask";

    private Stage stage;

    private final QuPathGUI qupath;

    /**
     * Server for detection - see https://github.com/ksugar/samapi
     */
    private final StringProperty serverURL = PathPrefs.createPersistentPreference(
            "ext.SAM.autoMask.serverUrl", "http://localhost:8000/sam/automask/");

    /**
     * Selected model for SAM
     */
    private final ObjectProperty<SAMModel> model = PathPrefs.createPersistentPreference(
            "ext.SAM.autoMask.model", SAMModel.VIT_L, SAMModel.class);

    /**
     * Selected output type
     */
    private final ObjectProperty<SAMOutput> outputType = PathPrefs.createPersistentPreference(
            "ext.SAM.autoMask.outputType", SAMOutput.MULTI_ALL, SAMOutput.class);

    /**
     * Set the names of new SAM detected objects.
     */
    private final BooleanProperty setNames = PathPrefs.createPersistentPreference(
            "ext.SAM.autoMask.setNames", true);

    /**
     * Assign random colors to new SAM objects.
     */
    private final BooleanProperty useRandomColors = PathPrefs.createPersistentPreference(
            "ext.SAM.autoMask.useRandomColors", true);

    /**
     * Points per side parameter.
     */
    private final IntegerProperty pointsPerSideProperty = PathPrefs.createPersistentPreference(
            "ext.SAM.autoMask.pointsPerSide", 32);

    /**
     * Points per batch parameter.
     */
    private final IntegerProperty pointsPerBatchProperty = PathPrefs.createPersistentPreference(
            "ext.SAM.autoMask.pointsPerBatch", 64);

    /**
     * Pred IoU thresh parameter.
     */
    private final DoubleProperty predIoUThreshProperty = PathPrefs.createPersistentPreference(
            "ext.SAM.autoMask.predIoUThresh", 0.88);

    /**
     * Stability score thresh parameter.
     */
    private final DoubleProperty stabilityScoreThreshProperty = PathPrefs.createPersistentPreference(
            "ext.SAM.autoMask.stabilityScoreThresh", 0.95);

    /**
     * Stability score offset parameter.
     */
    private final DoubleProperty stabilityScoreOffsetProperty = PathPrefs.createPersistentPreference(
            "ext.SAM.autoMask.stabilityScoreOffset", 1.0);

    /**
     * Box NMS thresh parameter.
     */
    private final DoubleProperty boxNmsThreshProperty = PathPrefs.createPersistentPreference(
            "ext.SAM.autoMask.boxNmsThresh", 0.7);

    /**
     * Crop N layers parameter.
     */
    private final IntegerProperty cropNLayersProperty = PathPrefs.createPersistentPreference(
            "ext.SAM.autoMask.cropNLayers", 0);

    /**
     * Crop NMS thresh parameter.
     */
    private final DoubleProperty cropNmsThreshProperty = PathPrefs.createPersistentPreference(
            "ext.SAM.autoMask.cropNmsThresh", 0.7);

    /**
     * Crop overlap ratio parameter.
     */
    private final DoubleProperty cropOverlapRatioProperty = PathPrefs.createPersistentPreference(
            "ext.SAM.autoMask.cropOverlapRatio", (double) 512 / 1500);

    /**
     * Crop N points downscale factor parameter.
     */
    private final IntegerProperty cropNPointsDownscaleFactorProperty = PathPrefs.createPersistentPreference(
            "ext.SAM.autoMask.cropNPointsDownscaleFactor", 1);

    /**
     * Min mask region area parameter.
     */
    private final IntegerProperty minMaskRegionAreaProperty = PathPrefs.createPersistentPreference(
            "ext.SAM.autoMask.minMaskRegionArea", 0);

    /**
     * Current image data
     */
    private final ObjectProperty<ImageData<BufferedImage>> imageDataProperty = new SimpleObjectProperty<>();

    /**
     * The task currently running
     */
    private final ObservableSet<SAMAutoMaskTask> currentTasks = FXCollections.observableSet();

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
    public SAMAutoMaskCommand(QuPathGUI qupath) {
        this.qupath = qupath;
    }

    /**
     * Show the SAM AutoMask dialog.
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
        if (pool == null)
            pool = Executors.newCachedThreadPool(ThreadTools.createThreadFactory("SAM-autoMask", true));
        this.imageDataProperty.unbind();
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
        submitTask();
    }
    private void submitTask() {
        SAMAutoMaskTask task = SAMAutoMaskTask.builder(qupath.getViewer())
                        .serverURL(serverURL.get())
                        .model(model.get())
                        .outputType(outputType.get())
                        .setName(setNames.get())
                        .setRandomColor(useRandomColors.get())
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
                        .build();
        pool.submit(task);
        currentTasks.add(task);
        task.stateProperty().addListener((observable, oldValue, newValue) -> taskStateChange(task, newValue));
    }

    private void taskStateChange(SAMAutoMaskTask task, Worker.State newValue) {
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

        addOutputTypePrompt(pane, row++);
        addCheckboxes(pane, row++);

        addSeparator(pane, row++);

        addParameterPane(pane, row);
        row += 11;

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

    private void addOutputTypePrompt(GridPane pane, int row) {
        ComboBox<SAMOutput> combo = new ComboBox<>();
        SAMOutput[] samOutputs = Arrays.stream(SAMOutput.values())
                .filter(v -> v != SAMOutput.SINGLE_MASK)
                .toArray(SAMOutput[]::new);
        combo.getItems().setAll(samOutputs);
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

        CheckBox cbDisplayNames = createCheckbox("Display names",
                qupath.getOverlayOptions().showNamesProperty(),
                "Display the annotation names in the viewer\n(this is a global preference)");

        GridPane checkboxPane = createColumnPane(cbRandomColors, cbAssignNames);
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
        Button btnRunOnce = new Button("Run");
        btnRunOnce.setOnAction(event -> runOnce());
        btnRunOnce.setMaxWidth(Double.MAX_VALUE);
        btnRunOnce.setTooltip(new Tooltip(
                "Run the model once using the selected annotations (points or rectangles)"));
        btnRunOnce.disableProperty().bind(disableRunning);
        Pane buttonPane = createColumnPane(btnRunOnce);
        pane.add(buttonPane, 0, row, GridPane.REMAINING, 1);
    }

    private Spinner<Integer> createIntegerSpinner(int min, int max, IntegerProperty property, int amountToStepBy,
                                                  String tooltipText) {
        Spinner<Integer> spinner = new Spinner<>(min, max, property.getValue(), amountToStepBy);
        spinner.setTooltip(new Tooltip(tooltipText));
        property.asObject().bindBidirectional(spinner.getValueFactory().valueProperty());
        spinner.setEditable(true);
        GuiTools.restrictTextFieldInputToNumber(spinner.getEditor(), false);
        GuiTools.resetSpinnerNullToPrevious(spinner);
        spinner.focusedProperty().addListener((v, o, n) -> {
            if (spinner.getEditor().getText().equals(""))
                spinner.getValueFactory().valueProperty().set(min);
        });
        spinner.valueProperty().addListener((obs, oldValue, newValue) -> property.set(newValue));
        return spinner;
    }

    private Spinner<Double> createDoubleSpinner(double min, double max, DoubleProperty property, double amountToStepBy,
                                                String tooltipText) {
        Spinner<Double> spinner = new Spinner<>(min, max, property.getValue(), amountToStepBy);
        spinner.setTooltip(new Tooltip(tooltipText));
        property.asObject().bindBidirectional(spinner.getValueFactory().valueProperty());
        spinner.setEditable(true);
        GuiTools.restrictTextFieldInputToNumber(spinner.getEditor(), true);
        GuiTools.resetSpinnerNullToPrevious(spinner);
        spinner.focusedProperty().addListener((v, o, n) -> {
            if (spinner.getEditor().getText().equals(""))
                spinner.getValueFactory().valueProperty().set(min);
        });
        spinner.valueProperty().addListener((obs, oldValue, newValue) -> property.set(newValue));
        return spinner;
    }


    private void addParameterPane(GridPane pane, int row) {
        Spinner<Integer> pointsPerSideSpinner = createIntegerSpinner(
                1, 10000, pointsPerSideProperty, 1,
                "The number of points to be sampled along one side of the image. " +
                        "The total number of points is points_per_side**2.");
        GridPane pointsPerSidePane = createColumnPane(new Label("Points per side"), pointsPerSideSpinner);
        pane.add(pointsPerSidePane, 0, row++, GridPane.REMAINING, 1);

        Spinner<Integer> pointsPerBatchSpinner = createIntegerSpinner(
                1, 10000, pointsPerBatchProperty, 1,
                "Sets the number of points run simultaneously by the model. " +
                        "Higher numbers may be faster but use more GPU memory."
        );
        GridPane pointsPerBatchPane = createColumnPane(
                new Label("Points per batch"), pointsPerBatchSpinner);
        pane.add(pointsPerBatchPane, 0, row++, GridPane.REMAINING, 1);

        Spinner<Double> predIoUThreshSpinner = createDoubleSpinner(
                0.0, 1.0, predIoUThreshProperty, 0.01,
                "A filtering threshold in [0,1], using the model's predicted mask quality."
        );
        GridPane predIoUThreshPane = createColumnPane(
                new Label("Pred IoU thresh"), predIoUThreshSpinner);
        pane.add(predIoUThreshPane, 0, row++, GridPane.REMAINING, 1);

        Spinner<Double> stabilityScoreThreshSpinner = createDoubleSpinner(
                0.0, 1.0, stabilityScoreThreshProperty, 0.01,
                "A filtering threshold in [0,1], using the stability of the mask under changes to the cutoff " +
                        "used to binarize the model's mask predictions."
        );
        GridPane stabilityScoreThreshPane = createColumnPane(
                new Label("Stability score thresh"), stabilityScoreThreshSpinner);
        pane.add(stabilityScoreThreshPane, 0, row++, GridPane.REMAINING, 1);

        Spinner<Double> stabilityScoreOffsetSpinner = createDoubleSpinner(
                0.0, 1.0, stabilityScoreOffsetProperty, 0.01,
                "The amount to shift the cutoff when calculated the stability score."
        );
        GridPane stabilityScoreOffsetPane = createColumnPane(
                new Label("Stability score offset"), stabilityScoreOffsetSpinner);
        pane.add(stabilityScoreOffsetPane, 0, row++, GridPane.REMAINING, 1);

        Spinner<Double> boxNmsThreshSpinner = createDoubleSpinner(
                0.0, 1.0, boxNmsThreshProperty, 0.01,
                "The box IoU cutoff used by non-maximal suppression to filter duplicate masks."
        );
        GridPane boxNmsThreshPane = createColumnPane(
                new Label("Box NMS thresh"), boxNmsThreshSpinner);
        pane.add(boxNmsThreshPane, 0, row++, GridPane.REMAINING, 1);

        Spinner<Integer> cropNLayersSpinner = createIntegerSpinner(
                0, 10, cropNLayersProperty, 1,
                "If >0, mask prediction will be run again on crops of the image. " +
                        "Sets the number of layers to run, where each layer has 2**i_layer number of image crops."
        );
        GridPane cropNLayersPane = createColumnPane(
                new Label("Crop N layers"), cropNLayersSpinner);
        pane.add(cropNLayersPane, 0, row++, GridPane.REMAINING, 1);

        Spinner<Double> cropNmsThreshSpinner = createDoubleSpinner(
                0.0, 1.0, cropNmsThreshProperty, 0.01,
                "The box IoU cutoff used by non-maximal suppression to filter duplicate masks between different crops."
        );
        GridPane cropNmsThreshPane = createColumnPane(
                new Label("Crop NMS thresh"), cropNmsThreshSpinner);
        pane.add(cropNmsThreshPane, 0, row++, GridPane.REMAINING, 1);

        Spinner<Double> cropOverlapRatioSpinner = createDoubleSpinner(
                0.0, 1.0, cropOverlapRatioProperty, 0.01,
                "Sets the degree to which crops overlap. In the first crop layer, " +
                        "crops will overlap by this fraction of the image length. " +
                        "Later layers with more crops scale down this overlap."
        );
        GridPane cropOverlapRatioPane = createColumnPane(
                new Label("Crop overlap ratio"), cropOverlapRatioSpinner);
        pane.add(cropOverlapRatioPane, 0, row++, GridPane.REMAINING, 1);

        Spinner<Integer> cropNPointsDownscaleFactorSpinner = createIntegerSpinner(
                0, 100, cropNPointsDownscaleFactorProperty, 1,
                "The number of points-per-side sampled in layer n is scaled down by crop_n_points_downscale_factor**n."
        );
        GridPane cropNPointsDownscaleFactorPane = createColumnPane(
                new Label("Crop N points downscale factor"), cropNPointsDownscaleFactorSpinner);
        pane.add(cropNPointsDownscaleFactorPane, 0, row++, GridPane.REMAINING, 1);

        Spinner<Integer> minMaskRegionAreaSpinner = createIntegerSpinner(
                0, 100, minMaskRegionAreaProperty, 1,
                "If >0, postprocessing will be applied to remove disconnected regions and holes in masks with area " +
                        "smaller than min_mask_region_area. Requires opencv."
        );
        GridPane minMaskRegionAreaPane = createColumnPane(
                new Label("Min mask region area"), minMaskRegionAreaSpinner);
        pane.add(minMaskRegionAreaPane, 0, row++, GridPane.REMAINING, 1);
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
