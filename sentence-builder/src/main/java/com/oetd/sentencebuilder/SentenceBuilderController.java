package com.oetd.sentencebuilder;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import java.time.LocalDateTime;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;

public class SentenceBuilderController {

    @FXML
    private TableView<Token> tokenTableView;

    @FXML
    private TableColumn<Token, Long> idColumn;

    @FXML
    private TableColumn<Token, String> textColumn;

    @FXML
    private TableColumn<Token, Long> totalCountColumn;

    @FXML
    private TableColumn<Token, Long> startCountColumn;

    @FXML
    private TableColumn<Token, Long> endCountColumn;

    @FXML
    private TableColumn<Token, LocalDateTime> createdAtColumn;

    @FXML
    private Button importButton;

    @FXML
    private Button refreshButton;

    @FXML
    private Button generateButton;

    @FXML
    private TextField generatedTextField;

    private AppContext appContext;
    private Stage stage;

    private final FileChooser fileChooser = new FileChooser();
    private final ObservableList<Token> tokens = FXCollections.observableArrayList();

    public void setAppContext(AppContext appContext) {
        this.appContext = appContext;
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    @FXML
    public void initialize() {
        fileChooser.setTitle("Add source.");

        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        textColumn.setCellValueFactory(new PropertyValueFactory<>("text"));
        totalCountColumn.setCellValueFactory(new PropertyValueFactory<>("totalCount"));
        startCountColumn.setCellValueFactory(new PropertyValueFactory<>("startCount"));
        endCountColumn.setCellValueFactory(new PropertyValueFactory<>("endCount"));
        createdAtColumn.setCellValueFactory(new PropertyValueFactory<>("createdAt"));
    }

    @FXML
    private void handleImport() {
        File selectedFile = fileChooser.showOpenDialog(stage);
        if (selectedFile != null) {
            try {
                appContext.getSbService().addFile(selectedFile);
                refreshTokens();
                appContext.getMarkovGenerator().loadTransitionsFromDB();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @FXML
    private void handleRefresh() {
        refreshTokens();
        appContext.getMarkovGenerator().loadTransitionsFromDB();
    }

    @FXML
    private void handleGenerate() {
        Token selectedToken = tokenTableView.getSelectionModel().getSelectedItem();
        String sentence;

        if (selectedToken != null && selectedToken.getToken_text() != null) {
            sentence = appContext.getMarkovGenerator().generateSentence(selectedToken.getToken_text(), 20);
        } else {
            sentence = appContext.getMarkovGenerator().generateSentence(20);
        }

        generatedTextField.setText(sentence);
    }

    private void refreshTokens() {
        tokens.setAll(appContext.getSbService().getTokens());
        tokenTableView.setItems(tokens);
    }
}
