package com.oetd.sentencebuilder;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;

public class SentenceBuilder extends Application {

    @Override
    public void start(Stage stage) throws IOException {

        // get DB connection variable via environment variables
        String dbUrl = System.getenv("SB_DB_URL");
        String dbUser = System.getenv("SB_DB_USER");
        String dbPassword = System.getenv("SB_DB_PASSWORD");

        if (dbUrl == null || dbUser == null || dbPassword == null) {
            throw new IOException("Missing database environment variables");
        }

        final StandardServiceRegistry registry =
                new StandardServiceRegistryBuilder()
                        .applySetting("hibernate.connection.url", dbUrl)
                        .applySetting("hibernate.connection.username", dbUser)
                        .applySetting("hibernate.connection.password", dbPassword)
                        // Prevent Hibernate from dropping existing tables on startup
                        .applySetting("hibernate.hbm2ddl.auto", "validate")
                        .build();

        SessionFactory sessionFactory = null;

        try {
            sessionFactory = new MetadataSources(registry)
                    // Add tables to be persisted
                    .addAnnotatedClass(Token.class)
                    .addAnnotatedClass(TokenTransition.class)
                    .buildMetadata()
                    .buildSessionFactory();
        } catch (Exception e) {
            StandardServiceRegistryBuilder.destroy(registry);
            throw new IOException("Failed to configure database", e);
        }

        SBService service = new SBService(sessionFactory);

        // Initialize MarkovGenerator with the shared Hibernate SessionFactory
        MarkovGenerator markovGenerator = new MarkovGenerator(sessionFactory);
        markovGenerator.loadTransitionsFromDB();

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Add source.");

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

        Button importButton = new Button("Select File to Import");
        importButton.setOnAction(e -> {
            File selectedFile = fileChooser.showOpenDialog(stage);
            if (selectedFile != null) {
                try {
                    service.addFile(selectedFile);
                    tokens.setAll(service.getTokens());
                    markovGenerator.loadTransitionsFromDB();
                } catch (IOException err) {
                    throw new RuntimeException(err);
                }
            }
        });

        Button refreshButton = new Button("Refresh");
        refreshButton.setOnAction(e -> {
            tokens.setAll(service.getTokens());
            markovGenerator.loadTransitionsFromDB();
        });

        TextField textField = new TextField("Change me!");

        Button generateButton = new Button("Generate");
        generateButton.setOnAction(e -> {
            // Option 1: If a user clicked/selected a word in the table, use it as the starting word!
            Token selectedToken = table.getSelectionModel().getSelectedItem();
            String sentence;

            if (selectedToken != null && selectedToken.getToken_text() != null) {
                sentence = markovGenerator.generateSentence(selectedToken.getToken_text(), 20);
            } else {
                // Option 2: Otherwise, pick a weighted random starting word from DB
                sentence = markovGenerator.generateSentence(20);
            }

            textField.setText(sentence);
        });

        // Setup layouts and scene
        VBox vBox = new VBox(importButton, refreshButton, generateButton, textField, table);
        Scene scene = new Scene(vBox, 600, 400);

        stage.setScene(scene);
        stage.setTitle("Sentence Builder");
        stage.show();
    }
}