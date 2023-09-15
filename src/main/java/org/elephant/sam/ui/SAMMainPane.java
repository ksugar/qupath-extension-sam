package org.elephant.sam.ui;

import org.controlsfx.dialog.ProgressDialog;
import org.elephant.sam.commands.SAMMainCommand;
import org.elephant.sam.entities.SAMType;
import org.elephant.sam.entities.SAMWeights;
import org.elephant.sam.tasks.SAMProgressTask;

import javafx.beans.binding.Bindings;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Separator;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TabPane.TabClosingPolicy;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.text.TextAlignment;

/**
 * The main pane for the SAM command.
 */
public class SAMMainPane extends GridPane {

    private final SAMMainCommand command;

    /**
     * Create a new main pane for the SAM command.
     * 
     * @param command
     *            The SAM command.
     */
    public SAMMainPane(SAMMainCommand command) {
        super();

        this.command = command;

        int row = 0;

        addInstructionPrompt(row++);

        addSeparator(row++);

        addServerPrompt(row++);
        addModelPrompt(row++);
        addWeights(row++);
        addRegisterWeightsButton(row++);

        addSeparator(row++);

        addTabPane(row++);

        addSeparator(row++);

        addInfoPane(row++);
        addEventFilter(MouseEvent.MOUSE_MOVED, this::handleMouseMoved);

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

    private void handleMouseMoved(MouseEvent event) {
        Node node = event.getPickResult().getIntersectedNode();
        while (node != null) {
            if (node instanceof Control) {
                Tooltip tooltip = ((Control) node).getTooltip();
                if (tooltip != null) {
                    command.updateInfoText(tooltip.getText());
                    return;
                }
            }
            node = node.getParent();
        }
        // Reset the info text, unless it shows an error
        command.updateInfoText("");
    }

    private void addInstructionPrompt(int row) {
        ProgressIndicator progressIndicator = new ProgressIndicator();
        progressIndicator.setPrefSize(32, 32);
        progressIndicator.progressProperty().bind(command.getDetectionProgressProperty());
        command.getDetectionProgressProperty().set(ProgressIndicator.INDETERMINATE_PROGRESS);
        progressIndicator.visibleProperty().bind(command.getIsTaskRunning());

        Label label = new Label("AI-assisted annotation using\nMeta's 'Segment Anything Model'");
        label.setWrapText(true);
        label.setAlignment(Pos.CENTER);
        label.setTextAlignment(TextAlignment.CENTER);

        BorderPane instructionPane = new BorderPane(label);
        instructionPane.setRight(progressIndicator);
        add(instructionPane, 0, row, GridPane.REMAINING, 1);
    }

    private void addSeparator(int row) {
        Separator separator = new Separator();
        separator.setPadding(new Insets(5.0));
        add(separator, 0, row, GridPane.REMAINING, 1);
    }

    private void addServerPrompt(int row) {
        Label labelUrl = new Label();
        labelUrl.textProperty().bind(command.getServerURLProperty());
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
        add(label, 0, row);
        add(labelUrl, 1, row);
        add(btnEdit, 2, row);
    }

    private void promptToSetUrl() {
        String currentURL = command.getServerURLProperty().get();
        TextInputDialog dialog = new TextInputDialog(currentURL);
        dialog.setHeaderText("Input SAM server URL");
        dialog.getEditor().setPrefColumnCount(32);
        String newURL = dialog.showAndWait().orElse(currentURL);
        if (newURL == null || newURL.isBlank() || newURL.equals(currentURL))
            return;
        command.getServerURLProperty().set(newURL);
    }

    private void addModelPrompt(int row) {
        ComboBox<SAMType> combo = new ComboBox<>();
        combo.getItems().setAll(SAMType.values());
        combo.getSelectionModel().select(command.getSamTypeProperty().get());
        combo.valueProperty().bindBidirectional(command.getSamTypeProperty());
        combo.getSelectionModel().selectedItemProperty().addListener((options, oldValue, newValue) -> {
            command.submitFetchWeightsTask(newValue);
        });
        combo.setMaxWidth(Double.MAX_VALUE);
        Tooltip tooltip = new Tooltip("The SAM model to use.\n" +
                "These differ in size and speed (and maybe accuracy)");
        combo.setTooltip(tooltip);
        GridPane.setFillWidth(combo, true);

        Label label = new Label("SAM model");
        label.setLabelFor(combo);
        label.setTooltip(tooltip);
        add(label, 0, row);
        add(combo, 1, row, GridPane.REMAINING, 1);
    }

    private void addWeights(int row) {
        ComboBox<SAMWeights> combo = new ComboBox<>();
        combo.setItems(command.getAvailableWeightsList());
        command.getAvailableWeightsList().addListener(new ListChangeListener<SAMWeights>() {
            @Override
            public void onChanged(Change<? extends SAMWeights> c) {
                if (c.next() && c.wasAdded()) {
                    combo.getSelectionModel().selectFirst();
                }
            }
        });
        command.getSelectedWeightsProperty().bind(combo.getSelectionModel().selectedItemProperty());
        combo.setMaxWidth(Double.MAX_VALUE);
        Tooltip tooltip = new Tooltip("The SAM weights to use.");
        combo.setTooltip(tooltip);
        GridPane.setFillWidth(combo, true);

        Label label = new Label("SAM weights");
        label.setLabelFor(combo);
        label.setTooltip(tooltip);
        add(label, 0, row);
        add(combo, 1, row, GridPane.REMAINING, 1);
    }

    private void showProgressDialog(SAMProgressTask progressTask, String headerText) {
        ProgressDialog progressDialog = new ProgressDialog(progressTask);
        progressDialog.setHeaderText(headerText);
        progressDialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL);
        progressDialog.setResultConverter(buttonType -> {
            if (buttonType == ButtonType.CANCEL) {
                command.submitCancelDownloadTask();
            }
            return null;
        });
        progressDialog.showAndWait();
    }

    private void showRegisterWeightsDialog() {
        SAMRegisterWeightsDialog dialog = new SAMRegisterWeightsDialog(command.getSamTypeProperty().get());
        command.getSamTypeRegisterProperty().bind(dialog.getSamTypeProperty());
        dialog.setHeaderText("Input SAM weights URL");
        SAMWeights newWeights = dialog.showAndWait().orElse(null);
        if (newWeights != null) {
            SAMProgressTask progressTask = command.submitProgressTask();
            command.submitRegisterWeightsTask(newWeights, progressTask);
            showProgressDialog(progressTask, "Downloading: " + newWeights.getUrl());
        }
    }

    private void addRegisterWeightsButton(int row) {
        Button btnRegister = new Button("Register");
        btnRegister.setOnAction(event -> showRegisterWeightsDialog());
        btnRegister.setMaxWidth(Double.MAX_VALUE);
        btnRegister.setTooltip(new Tooltip("Register Weights from URL"));

        Pane buttonPane = SAMUIUtils.createColumnPane(btnRegister);
        add(buttonPane, 0, row, GridPane.REMAINING, 1);
    }

    private void addTabPane(int row) {
        TabPane tabPane = new TabPane(
                new Tab("Prompt", new SAMPromptPane(command)),
                new Tab("Auto mask", new SAMAutoMaskPane(command)));
        tabPane.setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);
        add(tabPane, 0, row, GridPane.REMAINING, 1);
    }

    private void addInfoPane(int row) {
        Label labelInfo = new Label();
        labelInfo.setMaxWidth(Double.MAX_VALUE);
        labelInfo.setWrapText(true);
        labelInfo.setAlignment(Pos.CENTER);
        labelInfo.setTextAlignment(TextAlignment.CENTER);
        labelInfo.textProperty().bind(command.getInfoTextProperty());
        labelInfo.setTextOverrun(OverrunStyle.ELLIPSIS);
        labelInfo.setPrefHeight(64);
        labelInfo.styleProperty().bind(Bindings.createStringBinding(() -> {
            if (command.getInfoTextErrorTimestampProperty().get() > 0)
                return "-fx-text-fill: -qp-script-error-color;";
            else
                return null;
        }, command.getInfoTextErrorTimestampProperty()));
        GridPane.setFillWidth(labelInfo, true);
        add(labelInfo, 0, row, GridPane.REMAINING, 1);
    }

}
