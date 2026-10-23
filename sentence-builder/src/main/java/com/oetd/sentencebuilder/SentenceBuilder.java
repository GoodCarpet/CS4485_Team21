package com.oetd.sentencebuilder;


import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import org.hibernate.Session;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.hibernate.SessionFactory;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataBuilder;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class SentenceBuilder extends Application {

    @Override
    public void start(Stage stage) throws IOException {

        final StandardServiceRegistry registry = new StandardServiceRegistryBuilder().build();
        SessionFactory sessionFactory = null;

        try {
            sessionFactory =  new MetadataSources(registry)
                    //Add tables to be persisted
                    .addAnnotatedClass(Token.class)
                    .addAnnotatedClass(NextToken.class)
                    .buildMetadata()
                    .buildSessionFactory();
        } catch (Exception e) {
            StandardServiceRegistryBuilder.destroy(registry);
            throw new IOException("Failed to configure database", e);
        }


        SBService service = new SBService(sessionFactory);

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Add source.");

       Button importButton = new Button("Select File to Import");

        importButton.setOnAction(e -> {
            File selectedFile = fileChooser.showOpenDialog(stage);
            if (selectedFile != null) {
                try {
                    service.addFile(selectedFile);
                } catch (IOException err) {
                    throw new RuntimeException(err);
                }
            }
        });

        TableView<Token> table = new TableView<>();

        TableColumn<Token, Long> idColumn = new TableColumn<>("id");
        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));

        TableColumn<Token, String> textColumn = new TableColumn<>("text");
        textColumn.setCellValueFactory(new PropertyValueFactory<>("text"));

        TableColumn<Token, Long> totalCountColumn = new TableColumn<>("totalCount");
        totalCountColumn.setCellValueFactory(new PropertyValueFactory<>("totalCount"));

        TableColumn<Token, Long> startCountColumn = new TableColumn<>("startCount");
        startCountColumn.setCellValueFactory(new PropertyValueFactory<>("startCount"));

        TableColumn<Token, Long> endCountColumn = new TableColumn<>("endCount");
        endCountColumn.setCellValueFactory(new PropertyValueFactory<>("endCount"));

        TableColumn<Token, LocalDateTime> createdAtColumn = new TableColumn<>("createdAt");
        createdAtColumn.setCellValueFactory(new PropertyValueFactory<>("createdAt"));

        table.getColumns().addAll(idColumn, textColumn, totalCountColumn, startCountColumn, endCountColumn, createdAtColumn);

        ObservableList<Token> tokens = FXCollections.observableList(service.getTokens());

        table.setItems(tokens);

        Button refreshButton = new Button("Refresh");
        refreshButton.setOnAction(e -> {
            tokens.setAll(service.getTokens());
        });

        TextField textField = new TextField("Change me!");

        List<Token> generated = new ArrayList<>();

        Button generateButton = new Button("Generate");

        generateButton.setOnAction(e -> {

            if(generated.isEmpty()){
                Token mostFreq = tokens.stream().sorted((a, b) -> b.getTotal_count().compareTo(a.getTotal_count())).findFirst().orElse(null);
                generated.add(mostFreq);
            } else {
                Token lastToken = generated.getLast();
                NextToken nextToken = service.getNextToken(lastToken);
                Token nt = service.getTokenByText(nextToken.getNext_token_text());
                generated.add(nt);
            }

            StringBuilder sb = new StringBuilder();

            for(Token tok : generated){
                sb.append(tok.getToken_text());
                sb.append(' ');
            }

            textField.setText(sb.toString());
        });


        // Setup layouts and scene
        VBox vBox = new VBox(importButton, refreshButton, generateButton, textField, table);
        Scene scene = new Scene(vBox, 400, 200);

        stage.setScene(scene);
        stage.setTitle("Sentence Builder");
        stage.show();
    }
}
