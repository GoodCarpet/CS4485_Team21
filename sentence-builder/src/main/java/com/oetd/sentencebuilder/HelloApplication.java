package com.oetd.sentencebuilder;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.*;

public class HelloApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {

        try {
            DataBaseConnection db = new DataBaseConnection();
            //db.updateStatement("Insert into Token (token_str) values ('world');");
            ResultSet rs = db.askQuery("select token_str from Token;");
            while(rs.next()){
                System.out.println(rs.getString(1));
            }

        } catch (SQLException e){
            System.out.println("db error: " + e.getMessage());
        }


        FXMLLoader fxmlLoader = new FXMLLoader(HelloApplication.class.getResource("hello-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 320, 240);
        stage.setTitle("Hello!");
        stage.setScene(scene);
        stage.show();
    }
}
