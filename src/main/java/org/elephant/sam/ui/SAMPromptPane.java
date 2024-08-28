package org.elephant.sam.ui;

import java.awt.Shape;
import java.awt.image.BufferedImage;

import org.controlsfx.control.action.Action;
import org.elephant.sam.commands.SAMMainCommand;
import org.elephant.sam.entities.SAMOutput;
import org.elephant.sam.entities.SAMPromptMode;
import org.elephant.sam.entities.SAMType;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.IntegerBinding;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import qupath.lib.gui.actions.ActionTools;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.QuPathViewerListener;
import qupath.lib.gui.viewer.tools.PathTools;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassTools;

/**
 * The pane for the SAM prompt.
 */
public class SAMPromptPane extends GridPane {

    private final SAMMainCommand command;

    private final ObjectProperty<SAMType> samTypeProperty = new SimpleObjectProperty<>();

    private final ObjectProperty<SAMPromptMode> samPromptModeProperty = new SimpleObjectProperty<>();

    private final ObjectProperty<QuPathViewer> viewerProperty = new SimpleObjectProperty<>();

    private final BooleanBinding isImageOpenBinding = Bindings.createBooleanBinding(
            () -> viewerProperty.get() != null && viewerProperty.get().hasServer(), viewerProperty);

    /**
     * Integer property to have Z slices.
     */
    private final IntegerProperty nZSlicesProperty = new SimpleIntegerProperty();

    /**
     * Integer property to have timepoints.
     */
    private final IntegerProperty nTimePointsProperty = new SimpleIntegerProperty();

    /**
     * Flag to indicate that the SAM type is compatible with video prediction.
     */
    private final BooleanBinding isVideoCompatibleBinding = Bindings.createBooleanBinding(
            () -> samTypeProperty.get() != null && samTypeProperty.get().isVideoCompatible(), samTypeProperty);

    /**
     * Flag to indicate that the 2D (XY) is available.
     */
    private final BooleanBinding isXYAvailable = isImageOpenBinding;

    /**
     * Flag to indicate that the 3D (XYZ) is available.
     */
    private final BooleanBinding isXYZAvailable = isImageOpenBinding
            .and(isVideoCompatibleBinding)
            .and(nZSlicesProperty.greaterThan(1));

    /**
     * Flag to indicate that the time lapse (XYT) is available.
     */
    private final BooleanBinding isXYTAvailable = isImageOpenBinding
            .and(isVideoCompatibleBinding)
            .and(nTimePointsProperty.greaterThan(1));

    /**
     * Tooltip for the from field.
     */
    private final StringBinding fromIndexTooltipBinding = Bindings.createStringBinding(() -> {
        if (samPromptModeProperty.get() == SAMPromptMode.XYZ) {
            return "The Z slice to start 3D detection mode.";
        } else if (samPromptModeProperty.get() == SAMPromptMode.XYT) {
            return "The timepoint to start 2D+T detection mode.";
        } else {
            return "This is not used in 2D detection mode.";
        }
    }, samPromptModeProperty);

    /**
     * Tooltip for the to field.
     */
    private final StringBinding toIndexTooltipBinding = Bindings.createStringBinding(() -> {
        if (samPromptModeProperty.get() == SAMPromptMode.XYZ) {
            return "The Z slice to end 3D detection mode.";
        } else if (samPromptModeProperty.get() == SAMPromptMode.XYT) {
            return "The timepoint to end 2D+T detection mode.";
        } else {
            return "This is not used in 2D detection mode.";
        }
    }, samPromptModeProperty);

    private final IntegerBinding maxIndexBinding = Bindings.createIntegerBinding(() -> {
        if (samPromptModeProperty.get() == SAMPromptMode.XYZ) {
            return Math.max(0, nZSlicesProperty.get() - 1);
        } else if (samPromptModeProperty.get() == SAMPromptMode.XYT) {
            return Math.max(0, nTimePointsProperty.get() - 1);
        } else {
            return 0;
        }
    }, samPromptModeProperty, nZSlicesProperty, nTimePointsProperty);

    /**
     * Create a new pane for the SAM prompt.
     * 
     * @param command
     *            The SAM command.
     */
    public SAMPromptPane(SAMMainCommand command) {
        super();

        this.command = command;
        samTypeProperty.bind(command.getSamTypeProperty());
        samPromptModeProperty.bindBidirectional(command.getSamPromptModeProperty());
        command.getQuPath().getViewer().addViewerListener(new QuPathViewerListener() {

            @Override
            public void imageDataChanged(QuPathViewer viewer, ImageData<BufferedImage> imageDataOld,
                    ImageData<BufferedImage> imageDataNew) {
                viewerProperty.set(viewer);
                if (viewer.getServer() == null) {
                    nZSlicesProperty.set(0);
                    nTimePointsProperty.set(0);
                } else {
                    nZSlicesProperty.set(viewer.getServer().nZSlices());
                    nTimePointsProperty.set(viewer.getServer().nTimepoints());
                }
            }

            @Override
            public void visibleRegionChanged(QuPathViewer viewer, Shape shape) {
                // Do nothing
            }

            @Override
            public void selectedObjectChanged(QuPathViewer viewer, PathObject pathObjectSelected) {
                // Do nothing
            }

            @Override
            public void viewerClosed(QuPathViewer viewer) {
                viewerProperty.set(viewer);
            }
        });

        int row = 0;

        addCommandPane(row++);

        addSeparator(row++);

        addOutputTypePrompt(row++);
        addCheckboxes(row++);

        addSeparator(row++);

        addModePane(row++);

        addSeparator(row++);

        addButtons(row++);

        setHgap(SAMUIUtils.H_GAP);
        setVgap(SAMUIUtils.V_GAP);
        setPadding(new Insets(10.0));
        setMaxWidth(Double.MAX_VALUE);

        for (int i = 0; i < getColumnCount(); i++) {
            ColumnConstraints constraints = new ColumnConstraints();
            if (i == 1)
                constraints.setHgrow(Priority.ALWAYS);
            getColumnConstraints().add(constraints);
        }
    }

    private void addCommandPane(int row) {
        Action actionRectangle = command.getQuPath().getToolManager().getToolAction(PathTools.RECTANGLE);
        ToggleButton btnRectangle = ActionTools.createToggleButtonWithGraphicOnly(actionRectangle);

        Action actionPoints = command.getQuPath().getToolManager().getToolAction(PathTools.POINTS);
        ToggleButton btnPoints = ActionTools.createToggleButtonWithGraphicOnly(actionPoints);

        Action actionMove = command.getQuPath().getToolManager().getToolAction(PathTools.MOVE);
        ToggleButton btnMove = ActionTools.createToggleButtonWithGraphicOnly(actionMove);

        Label label = new Label("Draw prompts");
        label.setTooltip(new Tooltip("Draw foreground or background prompts.\n" +
                "Please select a rectangle or points tool to start."));
        RadioButton radioForeground = new RadioButton("Foreground");
        radioForeground.setTooltip(new Tooltip(
                "Draw foreground prompt.\n" +
                        "Requires rectangle or point tool to be selected."));
        radioForeground.disableProperty().bind(
                command.getQuPath().getToolManager().selectedToolProperty().isNotEqualTo(PathTools.POINTS).and(
                        command.getQuPath().getToolManager().selectedToolProperty().isNotEqualTo(PathTools.RECTANGLE)));
        RadioButton radioBackground = new RadioButton("Background");
        radioBackground.setTooltip(new Tooltip(
                "Draw background prompt.\n" +
                        "Requires point tool to be selected."));
        radioBackground.disableProperty()
                .bind(command.getQuPath().getToolManager().selectedToolProperty().isNotEqualTo(PathTools.POINTS));
        ObjectProperty<PathClass> autoAnnotation = PathPrefs.autoSetAnnotationClassProperty();
        if (autoAnnotation.get() != null && PathClassTools.isIgnoredClass(autoAnnotation.get()))
            radioBackground.setSelected(true);
        else
            radioForeground.setSelected(true);
        ToggleGroup group = new ToggleGroup();
        group.getToggles().setAll(radioForeground, radioBackground);
        command.getForceBackgroundPointsProperty().bind(group.selectedToggleProperty().isEqualTo(radioBackground));

        VBox vbox = new VBox(radioForeground, radioBackground);
        vbox.setSpacing(SAMUIUtils.H_GAP);
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
        drawingPane.setHgap(SAMUIUtils.H_GAP);

        add(drawingPane, 0, row, GridPane.REMAINING, 1);
    }

    private void addSeparator(int row) {
        Separator separator = new Separator();
        separator.setPadding(new Insets(5.0));
        add(separator, 0, row, GridPane.REMAINING, 1);
    }

    private void addOutputTypePrompt(int row) {
        ComboBox<SAMOutput> combo = new ComboBox<>();
        combo.getItems().setAll(SAMOutput.values());
        combo.getSelectionModel().select(command.getOutputTypeProperty().get());
        Tooltip tooltip = new Tooltip("Choose how many masks SAM should create (1 or 3).\n" +
                "For multiple masks, you can specify which is kept.");
        combo.setTooltip(tooltip);
        combo.valueProperty().bindBidirectional(command.getOutputTypeProperty());
        combo.setMaxWidth(Double.MAX_VALUE);
        GridPane.setFillWidth(combo, true);

        Label label = new Label("Output type");
        label.setLabelFor(combo);
        label.setTooltip(tooltip);
        add(label, 0, row);
        add(combo, 1, row, GridPane.REMAINING, 1);
    }

    private void addCheckboxes(int row) {
        CheckBox cbRandomColors = createCheckbox("Assign random colors",
                command.getUseRandomColorsProperty(),
                "Assign random colors to new (unclassified) objects created by SAM");

        CheckBox cbAssignNames = createCheckbox("Assign names",
                command.getSetNamesProperty(),
                "Assign names to identify new objects as created by SAM, including quality scores");

        CheckBox cbKeepPrompts = createCheckbox("Keep prompts",
                command.getKeepPromptsProperty(),
                "Keep the foreground prompts after detection.\n" +
                        "By default these are deleted.");

        CheckBox cbDisplayNames = createCheckbox("Display names",
                command.getQuPath().getOverlayOptions().showNamesProperty(),
                "Display the annotation names in the viewer\n(this is a global preference)");

        GridPane checkboxPane = SAMUIUtils.createColumnPane(cbRandomColors, cbAssignNames);
        checkboxPane.add(cbKeepPrompts, 0, 1, GridPane.REMAINING, 1);
        checkboxPane.add(cbDisplayNames, 1, 1, GridPane.REMAINING, 1);
        checkboxPane.setVgap(SAMUIUtils.V_GAP);

        add(checkboxPane, 0, row, GridPane.REMAINING, 1);
        setMinSize(GridPane.USE_COMPUTED_SIZE, GridPane.USE_COMPUTED_SIZE);
        setMaxSize(Double.MAX_VALUE, GridPane.USE_COMPUTED_SIZE);
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

    private void addModePane(int row) {
        Label label = new Label("Mode");
        label.setTooltip(new Tooltip("Select detection mode (2D / 3D / 2D+T)."));
        RadioButton radioModeXY = new RadioButton(SAMPromptMode.XY.toString());
        radioModeXY.setTooltip(new Tooltip("2D detection mode."));
        radioModeXY.disableProperty().bind(isXYAvailable.not());
        RadioButton radioModeXYZ = new RadioButton(SAMPromptMode.XYZ.toString());
        radioModeXYZ.setTooltip(new Tooltip("3D detection mode (only available with SAM2-compatible models)."));
        radioModeXYZ.disableProperty().bind(isXYZAvailable.not());
        RadioButton radioModeXYT = new RadioButton(SAMPromptMode.XYT.toString());
        radioModeXYT.setTooltip(new Tooltip("2D+T detection mode (only available with SAM2-compatible models)."));
        radioModeXYT.disableProperty().bind(isXYTAvailable.not());
        if (samPromptModeProperty.get() == SAMPromptMode.XY) {
            radioModeXY.setSelected(true);
        } else if (samPromptModeProperty.get() == SAMPromptMode.XYZ) {
            radioModeXYZ.setSelected(true);
        } else if (samPromptModeProperty.get() == SAMPromptMode.XYT) {
            radioModeXYT.setSelected(true);
        }
        ToggleGroup group = new ToggleGroup();
        group.getToggles().setAll(radioModeXY, radioModeXYZ, radioModeXYT);

        ChangeListener<Boolean> disableListener = (ObservableValue<? extends Boolean> observable, Boolean oldValue,
                Boolean newValue) -> {
            RadioButton rb = (RadioButton) ((BooleanProperty) observable).getBean();

            // Optionally, handle the selection state if required
            if (newValue && rb.isSelected()) {
                // If the disabled button is selected, select another button in the group
                group.getToggles().stream()
                        .filter(toggle -> !((RadioButton) toggle).isDisable())
                        .findFirst()
                        .ifPresent(toggle -> toggle.setSelected(true));
            }
        };

        radioModeXY.disableProperty().addListener(disableListener);
        radioModeXYZ.disableProperty().addListener(disableListener);
        radioModeXYT.disableProperty().addListener(disableListener);

        // Add a listener to the ObjectProperty to handle changes in the selected toggle
        group.selectedToggleProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                RadioButton selectedRadioButton = (RadioButton) newValue;
                if (selectedRadioButton.getText().equals(SAMPromptMode.XY.toString())) {
                    samPromptModeProperty.set(SAMPromptMode.XY);
                } else if (selectedRadioButton.getText().equals(SAMPromptMode.XYZ.toString())) {
                    samPromptModeProperty.set(SAMPromptMode.XYZ);
                } else if (selectedRadioButton.getText().equals(SAMPromptMode.XYT.toString())) {
                    samPromptModeProperty.set(SAMPromptMode.XYT);
                }
            }
        });

        HBox hbox = new HBox(radioModeXY, radioModeXYZ, radioModeXYT);
        hbox.setSpacing(SAMUIUtils.H_GAP);
        hbox.setMaxWidth(Double.MAX_VALUE);
        GridPane.setFillWidth(hbox, true);

        Label fromIndexLabel = new Label("from index");
        Spinner<Integer> fromIndexSpinner = new Spinner<>();
        fromIndexSpinner.setEditable(true);
        command.getFromIndexProperty().bind(fromIndexSpinner.valueProperty());
        fromIndexSpinner.setTooltip(new Tooltip(fromIndexTooltipBinding.get()));
        fromIndexTooltipBinding.addListener((observable, oldValue, newValue) -> {
            fromIndexSpinner.setTooltip(new Tooltip(newValue));
        });
        fromIndexSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(0, maxIndexBinding.get(), 0));
        maxIndexBinding.addListener((obs, oldMax, newMax) -> {
            final int max = newMax.intValue();
            fromIndexSpinner.setValueFactory(
                    new SpinnerValueFactory.IntegerSpinnerValueFactory(0, max, 0));
        });

        Label toIndexLabel = new Label("to index");
        Spinner<Integer> toIndexSpinner = new Spinner<>();
        toIndexSpinner.setEditable(true);
        command.getToIndexProperty().bind(toIndexSpinner.valueProperty());
        toIndexSpinner.setTooltip(new Tooltip(toIndexTooltipBinding.get()));
        toIndexTooltipBinding.addListener((observable, oldValue, newValue) -> {
            toIndexSpinner.setTooltip(new Tooltip(newValue));
        });
        toIndexSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(0, maxIndexBinding.get(), maxIndexBinding.get()));
        maxIndexBinding.addListener((obs, oldMax, newMax) -> {
            final int max = newMax.intValue();
            toIndexSpinner.setValueFactory(
                    new SpinnerValueFactory.IntegerSpinnerValueFactory(0, max, max));
        });

        HBox hboxFromTo = new HBox(fromIndexLabel, fromIndexSpinner, toIndexLabel, toIndexSpinner);
        hboxFromTo.visibleProperty().bind(samPromptModeProperty.isNotEqualTo(SAMPromptMode.XY));
        hboxFromTo.setSpacing(SAMUIUtils.H_GAP);
        hboxFromTo.setMaxWidth(Double.MAX_VALUE);
        hboxFromTo.setAlignment(Pos.CENTER_LEFT);
        GridPane.setFillWidth(hboxFromTo, true);

        int col = 0;
        GridPane modePane = new GridPane();
        modePane.add(label, col++, 0);
        modePane.add(hbox, col++, 0);
        modePane.setHgap(SAMUIUtils.H_GAP);
        modePane.add(hboxFromTo, 0, 1, GridPane.REMAINING, 1);
        modePane.setVgap(SAMUIUtils.V_GAP);

        add(modePane, 0, row, GridPane.REMAINING, 1);
    }

    private void addButtons(int row) {
        Button btnRunOnce = new Button("Run for selected");
        btnRunOnce.setOnAction(event -> command.runPrompt());
        btnRunOnce.setMaxWidth(Double.MAX_VALUE);
        btnRunOnce.setTooltip(new Tooltip(
                "Run the model once using the selected annotations (points or rectangles)"));

        ToggleButton btnLiveMode = new ToggleButton("Live mode");
        command.getLiveModeProperty().bindBidirectional(btnLiveMode.selectedProperty());
        btnLiveMode.disableProperty().bind(
                command.getDisableRunning().or(samPromptModeProperty.isNotEqualTo(SAMPromptMode.XY)));
        btnLiveMode.setMaxWidth(Double.MAX_VALUE);
        btnLiveMode.setTooltip(new Tooltip(
                "Turn on live detection to run the model on every new foreground annotation (point or rectangle)"));

        btnRunOnce.disableProperty().bind(command.getDisableRunning().or(command.getLiveModeProperty()));

        Pane buttonPane = SAMUIUtils.createColumnPane(btnRunOnce, btnLiveMode);
        add(buttonPane, 0, row, GridPane.REMAINING, 1);
    }

}
