package org.elephant.sam.ui;

import com.sun.javafx.scene.control.skin.resources.ControlResources;

import org.elephant.sam.entities.SAMType;
import org.elephant.sam.entities.SAMWeights;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Pos;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

/**
 * Dialog to register SAM weights.
 */
public class SAMRegisterWeightsDialog extends Dialog<SAMWeights> {

    private final ObjectProperty<SAMType> samTypeProperty = new SimpleObjectProperty<>();
    private final StringProperty nameProperty = new SimpleStringProperty();
    private final StringProperty urlProperty = new SimpleStringProperty();

    private final GridPane grid;
    private final Label labelSAMType;
    private final Label labelName;
    private final Label labelURL;
    private final ComboBox<SAMType> comboSAMType;
    private final TextField textFieldName;
    private final TextField textFieldURL;

    /**
     * Create a new dialog.
     * 
     * @param defaultType
     *            The default SAM type.
     */
    public SAMRegisterWeightsDialog(final SAMType defaultType) {
        samTypeProperty.set(defaultType);
        final DialogPane dialogPane = getDialogPane();

        labelSAMType = new Label("SAM type: ");
        comboSAMType = new ComboBox<>();
        comboSAMType.getItems().setAll(SAMType.values());
        comboSAMType.getSelectionModel().select(samTypeProperty.get());
        samTypeProperty.bind(comboSAMType.getSelectionModel().selectedItemProperty());
        comboSAMType.setMaxWidth(Double.MAX_VALUE);
        comboSAMType.setTooltip(new Tooltip("The SAM type to register."));
        GridPane.setFillWidth(comboSAMType, true);

        labelName = new Label("Name: ");
        textFieldName = new TextField();
        nameProperty.bind(textFieldName.textProperty());
        textFieldName.setMaxWidth(Double.MAX_VALUE);
        textFieldName.setPrefColumnCount(32);
        textFieldName.setTooltip(
                new Tooltip("The SAM weights name to register. It needs to be unique in the same SAM type."));
        GridPane.setHgrow(textFieldName, Priority.ALWAYS);
        GridPane.setFillWidth(textFieldName, true);

        labelURL = new Label("URL: ");
        textFieldURL = new TextField();
        urlProperty.bind(textFieldURL.textProperty());
        textFieldURL.setMaxWidth(Double.MAX_VALUE);
        textFieldURL.setPrefColumnCount(32);
        textFieldURL.setTooltip(new Tooltip("The URL to the SAM weights file."));
        GridPane.setHgrow(textFieldURL, Priority.ALWAYS);
        GridPane.setFillWidth(textFieldURL, true);

        grid = new GridPane();
        grid.setHgap(10);
        grid.setMaxWidth(Double.MAX_VALUE);
        grid.setAlignment(Pos.CENTER_LEFT);

        dialogPane.contentTextProperty().addListener(o -> updateGrid());

        setTitle("Register SAM weights");
        dialogPane.setHeaderText(ControlResources.getString("Dialog.confirm.header"));
        dialogPane.getStyleClass().add("text-input-dialog");
        dialogPane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        updateGrid();

        setResultConverter((dialogButton) -> {
            ButtonData data = dialogButton == null ? null : dialogButton.getButtonData();
            return data == ButtonData.OK_DONE
                    ? new SAMWeights(samTypeProperty.get().modelName(), nameProperty.get(), urlProperty.get())
                    : null;
        });
    }

    private void updateGrid() {
        grid.getChildren().clear();

        grid.add(labelSAMType, 0, 0);
        grid.add(comboSAMType, 1, 0);
        grid.add(labelName, 0, 1);
        grid.add(textFieldName, 1, 1);
        grid.add(labelURL, 0, 2);
        grid.add(textFieldURL, 1, 2);
        getDialogPane().setContent(grid);

        Platform.runLater(() -> comboSAMType.requestFocus());
    }

    /**
     * Get the SAM type property.
     * 
     * @return The SAM type property.
     */
    public ObjectProperty<SAMType> getSamTypeProperty() {
        return samTypeProperty;
    }

}
