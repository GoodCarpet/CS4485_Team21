package com.oetd.sentencebuilder;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

public class SentenceBuilderController {
    @FXML
    private Label welcomeText;

    @FXML
    protected void onHelloButtonClick() {
        welcomeText.setText("Welcome to JavaFX Application!");
    }
}
