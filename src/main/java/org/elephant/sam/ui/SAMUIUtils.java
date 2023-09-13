package org.elephant.sam.ui;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.scene.Node;
import javafx.scene.control.Spinner;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import qupath.lib.gui.tools.GuiTools;

/**
 * Utilities for the SAM UI.
 */
public class SAMUIUtils {

    /**
     * Horizontal gap between elements.
     */
    public final static double H_GAP = 5;

    /**
     * Vertical gap between elements.
     */
    public final static double V_GAP = 5;

    /**
     * Create a pane with the given nodes in a row.
     * 
     * @param nodes
     *            The nodes to add to the pane.
     * @return The pane.
     */
    public static GridPane createColumnPane(Node... nodes) {
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

    /**
     * Create a column constraint with the given percentage.
     * 
     * @param percentage
     *            The percentage.
     * @return The column constraint.
     */
    public static ColumnConstraints createPercentageConstraint(double percentage) {
        ColumnConstraints constraint = new ColumnConstraints();
        constraint.setPercentWidth(percentage);
        constraint.setHgrow(Priority.ALWAYS);
        return constraint;
    }

    /**
     * Create a spinner for an integer property.
     * 
     * @param min
     *            The minimum value.
     * @param max
     *            The maximum value.
     * @param property
     *            The integer property.
     * @param amountToStepBy
     *            The amount to step by.
     * @param tooltipText
     *            The tooltip text.
     * @return The spinner.
     */
    public static Spinner<Integer> createIntegerSpinner(int min, int max, IntegerProperty property, int amountToStepBy,
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
        spinner.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (!newValue.equals(oldValue))
                property.set(newValue);
        });
        property.addListener((obs, oldValue, newValue) -> {
            if (!newValue.equals(oldValue))
                spinner.getValueFactory().setValue(newValue.intValue());
        });
        return spinner;
    }

    /**
     * Create a spinner for a double property.
     * 
     * @param min
     *            The minimum value.
     * @param max
     *            The maximum value.
     * @param property
     *            The double property.
     * @param amountToStepBy
     *            The amount to step by.
     * @param tooltipText
     *            The tooltip text.
     * @return The spinner.
     */
    public static Spinner<Double> createDoubleSpinner(double min, double max, DoubleProperty property,
            double amountToStepBy, String tooltipText) {
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
        spinner.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (!newValue.equals(oldValue))
                property.set(newValue);
        });
        property.addListener((obs, oldValue, newValue) -> {
            if (!newValue.equals(oldValue))
                spinner.getValueFactory().setValue(newValue.doubleValue());
        });
        return spinner;
    }

}
