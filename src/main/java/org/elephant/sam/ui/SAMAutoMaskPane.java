package org.elephant.sam.ui;

import org.elephant.sam.commands.SAMMainCommand;
import org.elephant.sam.entities.SAMOutput;
import org.elephant.sam.entities.SAMType;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;

/**
 * The pane for the SAM auto mask.
 */
public class SAMAutoMaskPane extends GridPane {

        private final SAMMainCommand command;

        private final ObjectProperty<SAMType> samTypeProperty = new SimpleObjectProperty<>();

        /**
         * Flag to indicate that the SAM type is compatible with SAM3 prediction.
         */
        private final BooleanBinding isSAM3CompatibleBinding = Bindings.createBooleanBinding(
                        () -> samTypeProperty.get() != null && samTypeProperty.get().isSAM3Compatible(),
                        samTypeProperty);

        /**
         * Create a new pane for the SAM auto mask.
         *
         * @param command
         *                The SAM command.
         */
        public SAMAutoMaskPane(SAMMainCommand command) {
                super();

                this.command = command;
                this.samTypeProperty.bind(command.getSamTypeProperty());

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
                GridPane parameterPane = new GridPane();
                int prow = 0;
                Spinner<Integer> pointsPerSideSpinner = SAMUIUtils.createIntegerSpinner(
                                1, 10000, command.getPointsPerSideProperty(), 1,
                                "The number of points to be sampled along one side of the image. " +
                                                "The total number of points is points_per_side**2.");
                GridPane pointsPerSidePane = SAMUIUtils.createColumnPane(new Label("Points per side"),
                                pointsPerSideSpinner);
                parameterPane.add(pointsPerSidePane, 0, prow++, GridPane.REMAINING, 1);

                Spinner<Integer> pointsPerBatchSpinner = SAMUIUtils.createIntegerSpinner(
                                1, 10000, command.getPointsPerBatchProperty(), 1,
                                "Sets the number of points run simultaneously by the model. " +
                                                "Higher numbers may be faster but use more GPU memory.");
                GridPane pointsPerBatchPane = SAMUIUtils.createColumnPane(
                                new Label("Points per batch"), pointsPerBatchSpinner);
                parameterPane.add(pointsPerBatchPane, 0, prow++, GridPane.REMAINING, 1);

                Spinner<Double> predIoUThreshSpinner = SAMUIUtils.createDoubleSpinner(
                                0.0, 1.0, command.getPredIoUThreshProperty(), 0.01,
                                "A filtering threshold in [0,1], using the model's predicted mask quality.");
                GridPane predIoUThreshPane = SAMUIUtils.createColumnPane(
                                new Label("Pred IoU thresh"), predIoUThreshSpinner);
                parameterPane.add(predIoUThreshPane, 0, prow++, GridPane.REMAINING, 1);

                Spinner<Double> stabilityScoreThreshSpinner = SAMUIUtils.createDoubleSpinner(
                                0.0, 1.0, command.getStabilityScoreThreshProperty(), 0.01,
                                "A filtering threshold in [0,1], using the stability of the mask under changes to the cutoff "
                                                +
                                                "used to binarize the model's mask predictions.");
                GridPane stabilityScoreThreshPane = SAMUIUtils.createColumnPane(
                                new Label("Stability score thresh"), stabilityScoreThreshSpinner);
                parameterPane.add(stabilityScoreThreshPane, 0, prow++, GridPane.REMAINING, 1);

                Spinner<Double> stabilityScoreOffsetSpinner = SAMUIUtils.createDoubleSpinner(
                                0.0, 1.0, command.getStabilityScoreOffsetProperty(), 0.01,
                                "The amount to shift the cutoff when calculated the stability score.");
                GridPane stabilityScoreOffsetPane = SAMUIUtils.createColumnPane(
                                new Label("Stability score offset"), stabilityScoreOffsetSpinner);
                parameterPane.add(stabilityScoreOffsetPane, 0, prow++, GridPane.REMAINING, 1);

                Spinner<Double> boxNmsThreshSpinner = SAMUIUtils.createDoubleSpinner(
                                0.0, 1.0, command.getBoxNmsThreshProperty(), 0.01,
                                "The box IoU cutoff used by non-maximal suppression to filter duplicate masks.");
                GridPane boxNmsThreshPane = SAMUIUtils.createColumnPane(
                                new Label("Box NMS thresh"), boxNmsThreshSpinner);
                parameterPane.add(boxNmsThreshPane, 0, prow++, GridPane.REMAINING, 1);

                Spinner<Integer> cropNLayersSpinner = SAMUIUtils.createIntegerSpinner(
                                0, 10, command.getCropNLayersProperty(), 1,
                                "If >0, mask prediction will be run again on crops of the image. " +
                                                "Sets the number of layers to run, where each layer has 2**i_layer number of image crops.");
                GridPane cropNLayersPane = SAMUIUtils.createColumnPane(
                                new Label("Crop N layers"), cropNLayersSpinner);
                parameterPane.add(cropNLayersPane, 0, prow++, GridPane.REMAINING, 1);

                Spinner<Double> cropNmsThreshSpinner = SAMUIUtils.createDoubleSpinner(
                                0.0, 1.0, command.getCropNmsThreshProperty(), 0.01,
                                "The box IoU cutoff used by non-maximal suppression to filter duplicate masks between different crops.");
                GridPane cropNmsThreshPane = SAMUIUtils.createColumnPane(
                                new Label("Crop NMS thresh"), cropNmsThreshSpinner);
                parameterPane.add(cropNmsThreshPane, 0, prow++, GridPane.REMAINING, 1);

                Spinner<Double> cropOverlapRatioSpinner = SAMUIUtils.createDoubleSpinner(
                                0.0, 1.0, command.getCropOverlapRatioProperty(), 0.01,
                                "Sets the degree to which crops overlap. In the first crop layer, " +
                                                "crops will overlap by this fraction of the image length. " +
                                                "Later layers with more crops scale down this overlap.");
                GridPane cropOverlapRatioPane = SAMUIUtils.createColumnPane(
                                new Label("Crop overlap ratio"), cropOverlapRatioSpinner);
                parameterPane.add(cropOverlapRatioPane, 0, prow++, GridPane.REMAINING, 1);

                Spinner<Integer> cropNPointsDownscaleFactorSpinner = SAMUIUtils.createIntegerSpinner(
                                0, 100, command.getCropNPointsDownscaleFactorProperty(), 1,
                                "The number of points-per-side sampled in layer n is scaled down by crop_n_points_downscale_factor**n.");
                GridPane cropNPointsDownscaleFactorPane = SAMUIUtils.createColumnPane(
                                new Label("Crop N points downscale factor"), cropNPointsDownscaleFactorSpinner);
                parameterPane.add(cropNPointsDownscaleFactorPane, 0, prow++, GridPane.REMAINING, 1);

                Spinner<Integer> minMaskRegionAreaSpinner = SAMUIUtils.createIntegerSpinner(
                                0, 100000, command.getMinMaskRegionAreaProperty(), 1,
                                "If >0, postprocessing will be applied to remove disconnected regions and holes in masks with area "
                                                +
                                                "smaller than min_mask_region_area. Requires opencv.");
                GridPane minMaskRegionAreaPane = SAMUIUtils.createColumnPane(
                                new Label("Min mask region area"), minMaskRegionAreaSpinner);
                parameterPane.add(minMaskRegionAreaPane, 0, prow++, GridPane.REMAINING, 1);

                parameterPane.setHgap(SAMUIUtils.H_GAP);
                parameterPane.setVgap(SAMUIUtils.V_GAP);
                parameterPane.setMaxWidth(Double.MAX_VALUE);

                for (int i = 0; i < parameterPane.getColumnCount(); i++) {
                        ColumnConstraints constraints = new ColumnConstraints();
                        if (i == 0)
                                constraints.setHgrow(Priority.ALWAYS);
                        parameterPane.getColumnConstraints().add(constraints);
                }

                add(parameterPane, 0, row, GridPane.REMAINING, 1);
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

                CheckBox cbClearCurrentObjects = createCheckbox("Clear current objects",
                                command.getClearCurrentObjectsProperty(),
                                "Clear current objects to avoid overlaps");

                CheckBox cbDisplayNames = createCheckbox("Display names",
                                command.getQuPath().getOverlayOptions().showNamesProperty(),
                                "Display the annotation names in the viewer\n(this is a global preference)");

                CheckBox cbIncludeImageEdge = createCheckbox("Include image edge",
                                command.getIncludeImageEdgeProperty(),
                                "Include image edge in SAM auto mask generator");

                GridPane checkboxPane = SAMUIUtils.createColumnPane(cbRandomColors, cbAssignNames);
                checkboxPane.add(cbClearCurrentObjects, 0, 1, GridPane.REMAINING, 1);
                checkboxPane.setVgap(SAMUIUtils.V_GAP);
                checkboxPane.add(cbDisplayNames, 1, 1, GridPane.REMAINING, 1);
                checkboxPane.setVgap(SAMUIUtils.V_GAP);
                checkboxPane.add(cbIncludeImageEdge, 0, 2, GridPane.REMAINING, 1);
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
                Button btnResetParameters = new Button("Reset parameters");
                btnResetParameters.setOnAction(event -> command.resetAutoMaskParameters());
                btnResetParameters.setMaxWidth(Double.MAX_VALUE);
                btnResetParameters.setTooltip(new Tooltip("Reset parameters to default values"));

                Button btnRunOnce = new Button("Run");
                btnRunOnce.setOnAction(event -> command.runAutoMask());
                btnRunOnce.setMaxWidth(Double.MAX_VALUE);
                btnRunOnce.setTooltip(new Tooltip(
                                "Run the model once using the selected annotations (points or rectangles)"));
                btnRunOnce.disableProperty().bind(isSAM3CompatibleBinding.or(command.getDisableRunning()));

                Pane buttonPane = SAMUIUtils.createColumnPane(btnResetParameters, btnRunOnce);
                add(buttonPane, 0, row, GridPane.REMAINING, 1);
        }

}
