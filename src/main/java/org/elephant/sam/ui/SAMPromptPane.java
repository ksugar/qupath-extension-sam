package org.elephant.sam.ui;

import org.controlsfx.control.action.Action;
import org.elephant.sam.commands.SAMMainCommand;
import org.elephant.sam.entities.SAMOutput;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Separator;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import qupath.lib.gui.ActionTools;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.viewer.tools.PathTools;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassTools;

/**
 * The pane for the SAM prompt.
 */
public class SAMPromptPane extends GridPane {

    private final SAMMainCommand command;

    /**
     * Create a new pane for the SAM prompt.
     * 
     * @param command
     *            The SAM command.
     */
    public SAMPromptPane(SAMMainCommand command) {
        super();

        this.command = command;

        int row = 0;

        addCommandPane(row++);

        addSeparator(row++);

        addOutputTypePrompt(row++);
        addCheckboxes(row++);

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
        Action actionRectangle = command.getQuPath().getToolAction(PathTools.RECTANGLE);
        ToggleButton btnRectangle = ActionTools.createToggleButton(actionRectangle, true);

        Action actionPoints = command.getQuPath().getToolAction(PathTools.POINTS);
        ToggleButton btnPoints = ActionTools.createToggleButton(actionPoints, true);

        Action actionMove = command.getQuPath().getToolAction(PathTools.MOVE);
        ToggleButton btnMove = ActionTools.createToggleButton(actionMove, true);

        Label label = new Label("Draw prompts");
        label.setTooltip(new Tooltip("Draw foreground or background prompts.\n" +
                "Please select a rectangle or points tool to start."));
        RadioButton radioForeground = new RadioButton("Foreground");
        radioForeground.setTooltip(new Tooltip(
                "Draw foreground prompt.\n" +
                        "Requires rectangle or point tool to be selected."));
        radioForeground.disableProperty().bind(
                command.getQuPath().selectedToolProperty().isNotEqualTo(PathTools.POINTS).and(
                        command.getQuPath().selectedToolProperty().isNotEqualTo(PathTools.RECTANGLE)));
        RadioButton radioBackground = new RadioButton("Background");
        radioBackground.setTooltip(new Tooltip(
                "Draw background prompt.\n" +
                        "Requires point tool to be selected."));
        radioBackground.disableProperty()
                .bind(command.getQuPath().selectedToolProperty().isNotEqualTo(PathTools.POINTS));
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

    private void addButtons(int row) {
        Button btnRunOnce = new Button("Run for selected");
        btnRunOnce.setOnAction(event -> command.runPrompt());
        btnRunOnce.setMaxWidth(Double.MAX_VALUE);
        btnRunOnce.setTooltip(new Tooltip(
                "Run the model once using the selected annotations (points or rectangles)"));

        ToggleButton btnLiveMode = new ToggleButton("Live mode");
        command.getLiveModeProperty().bindBidirectional(btnLiveMode.selectedProperty());
        btnLiveMode.disableProperty().bind(command.getDisableRunning());
        btnLiveMode.setMaxWidth(Double.MAX_VALUE);
        btnLiveMode.setTooltip(new Tooltip(
                "Turn on live detection to run the model on every new foreground annotation (point or rectangle)"));

        btnRunOnce.disableProperty().bind(command.getDisableRunning().or(command.getLiveModeProperty()));

        Pane buttonPane = SAMUIUtils.createColumnPane(btnRunOnce, btnLiveMode);
        add(buttonPane, 0, row, GridPane.REMAINING, 1);
    }

}
