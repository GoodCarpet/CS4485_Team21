package com.oetd.sentencebuilder;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class SentenceBuilder extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        AppContext context = new AppContext();

        FXMLLoader loader = new FXMLLoader(getClass().getResource("sentence-builder-view.fxml"));
        loader.setControllerFactory(_controllerClass -> {
            return new SentenceBuilderController(context);
        });
        Parent root = loader.load();

        SentenceBuilderController controller = loader.getController();
        controller.setStage(stage);

        Scene scene = new Scene(root, 1100, 680);
        stage.setScene(scene);
        stage.setTitle("Sentence Builder");
        stage.show();
    }
}
