package com.oetd.sentencebuilder;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.css.PseudoClass;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.Tab;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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

    @FXML
    private ComboBox<MarkovGenerator.Algorithm> algorithmComboBox;

    @FXML
    private HBox autoSuggestBox;

    // Aniq Sunesara
    // prompt: Add a tab onto the GUI that will display all the generated sentences and related data
    // make it so that when a certain entry is selected, duplicate sentences that have the same hash are also highlighted
    // Generated Sentences tab, displaying persisted Generated_Sentence rows.
    @FXML private Tab generatedSentencesTab;
    @FXML private TableView<GeneratedSentence> generatedSentencesTableView;
    @FXML private TableColumn<GeneratedSentence, Long> sentenceIdColumn;
    @FXML private TableColumn<GeneratedSentence, String> sentenceTextColumn;
    @FXML private TableColumn<GeneratedSentence, String> generationMethodColumn;
    @FXML private TableColumn<GeneratedSentence, Long> startingTokenIdColumn;
    @FXML private TableColumn<GeneratedSentence, String> startingTokenTextColumn;
    @FXML private TableColumn<GeneratedSentence, String> addedToCorpusColumn;
    @FXML private TableColumn<GeneratedSentence, String> generatedAtColumn;
    @FXML private TableColumn<GeneratedSentence, String> sentenceHashColumn;
    @FXML private TextArea generatedSentenceDetails;
    @FXML private Label generatedSentenceCountLabel;
    @FXML private Label matchingSentenceHashLabel;

    private final ObservableList<GeneratedSentence> generatedSentences =
            FXCollections.observableArrayList();
    private static final PseudoClass MATCHING_HASH_PSEUDO_CLASS =
            PseudoClass.getPseudoClass("matching-sentence-hash");
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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

        // Display every saved sentence and its associated database metadata.
        sentenceIdColumn.setCellValueFactory(cell ->
                new ReadOnlyObjectWrapper<>(cell.getValue().getSentenceId()));
        sentenceTextColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().getSentenceText()));
        generationMethodColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().getGenerationMethod()));
        startingTokenIdColumn.setCellValueFactory(cell -> {
            Token starting = cell.getValue().getStartingToken();
            return new ReadOnlyObjectWrapper<>(starting == null ? null : starting.getToken_id());
        });
        startingTokenTextColumn.setCellValueFactory(cell -> {
            Token starting = cell.getValue().getStartingToken();
            return new ReadOnlyStringWrapper(starting == null ? "" : starting.getToken_text());
        });
        addedToCorpusColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(Boolean.TRUE.equals(cell.getValue().getAddedToCorpus())
                        ? "Yes" : "No"));
        generatedAtColumn.setCellValueFactory(cell -> {
            LocalDateTime timestamp = cell.getValue().getGeneratedAt();
            return new ReadOnlyStringWrapper(timestamp == null ? "" : DATE_FORMAT.format(timestamp));
        });
        sentenceHashColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().getSentenceHash()));

        generatedSentencesTableView.setItems(generatedSentences);
        generatedSentencesTableView.setPlaceholder(
                new Label("No generated sentences saved yet."));
        // Every visible row with the selected sentence's SHA-256 is highlighted,
        // including the selected row. TableRow virtualization is handled by
        // updateItem(): recycled rows always re-evaluate their current item.
        generatedSentencesTableView.setRowFactory(table -> new TableRow<>() {
            @Override
            protected void updateItem(GeneratedSentence sentence, boolean empty) {
                super.updateItem(sentence, empty);
                GeneratedSentence selected = table.getSelectionModel().getSelectedItem();
                String selectedHash = selected == null ? null : selected.getSentenceHash();
                boolean matching = !empty && sentence != null && selectedHash != null
                        && !selectedHash.isBlank()
                        && selectedHash.equals(sentence.getSentenceHash());
                pseudoClassStateChanged(MATCHING_HASH_PSEUDO_CLASS, matching);
            }
        });
        generatedSentencesTableView.getSelectionModel().selectedItemProperty()
                .addListener((obs, previous, selected) -> {
                    showGeneratedSentenceDetails(selected);
                    updateMatchingSentenceHashLabel(selected);
                    // Refresh updates visible/recycled row pseudo-classes when selection changes.
                    generatedSentencesTableView.refresh();
                });

        // Read from the database when the tab becomes visible, so sentences
        // inserted by other team members will appear on the next visit.
        generatedSentencesTab.setOnSelectionChanged(event -> {
            if (generatedSentencesTab.isSelected() && appContext != null) {
                refreshGeneratedSentences();
            }
        });

        algorithmComboBox.setItems(FXCollections.observableArrayList(MarkovGenerator.Algorithm.values()));
        algorithmComboBox.setValue(MarkovGenerator.Algorithm.WEIGHTED_MARKOV);
        algorithmComboBox.setConverter(new StringConverter<MarkovGenerator.Algorithm>() {
            @Override
            public String toString(MarkovGenerator.Algorithm algorithm) {
                return algorithm == null ? "" : algorithm.getDisplayName();
            }

            @Override
            public MarkovGenerator.Algorithm fromString(String string) {
                return null;
            }
        });

        // Double-click a row in the table to view following words and their frequencies in a popup
        tokenTableView.setRowFactory(tv -> {
            TableRow<Token> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    Token selected = row.getItem();
                    if (selected != null && selected.getToken_text() != null) {
                        showFollowingWordsPopup(selected.getToken_text());
                    }
                }
            });
            return row;
        });

        // Press Enter in the text field to check for unknown words (e.g., "crossbow"),
        // prompt to add them with bidirectional transitions, and focus the last word in the table
        generatedTextField.setOnAction(event -> {
            String currentText = generatedTextField.getText();
            if (currentText != null && !currentText.trim().isEmpty()) {
                promptAndAddUnknownWords(currentText.trim());
                String lastWord = appContext.getMarkovGenerator().extractLastWord(currentText);
                focusTokenInTable(lastWord);
            }
        });
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
        if (tokens.isEmpty()) {
            refreshTokens();
        }

        Token selectedToken = tokenTableView.getSelectionModel().getSelectedItem();

        MarkovGenerator.Algorithm algorithm = algorithmComboBox.getValue();
        if (algorithm == null) {
            algorithm = MarkovGenerator.Algorithm.WEIGHTED_MARKOV;
        }

        String sentence;

        if (selectedToken != null && selectedToken.getToken_text() != null) {
            sentence = appContext.getMarkovGenerator().generateSentence(selectedToken.getToken_text(), 20, algorithm);
        } else {
            sentence = appContext.getMarkovGenerator().generateSentence(algorithm, 20);
        }
        generatedTextField.setText(sentence);
        // Aniq Sunesara
        // add the sentence into generated sentences table and update values of tokens
        appContext.getSbService().addGeneratedSentence(sentence, algorithm.getDisplayName());
        // Keep the saved-sentences tab up to date after a successful generation.
        refreshGeneratedSentences();

        // Focus the TableView on the last word of the generated sentence
        String lastWord = appContext.getMarkovGenerator().extractLastWord(sentence);
        focusTokenInTable(lastWord);
    }

    @FXML
    private void handleAutoSuggest() {
        autoSuggestBox.getChildren().clear();

        String rawText = generatedTextField.getText();
        if (rawText == null || rawText.trim().isEmpty()) {
            return;
        }

        boolean endsWithSpace = rawText.endsWith(" ");
        String[] words = rawText.trim().split("\\s+");
        if (words.length == 0) {
            return;
        }

        String lastWord = words[words.length - 1];
        MarkovGenerator generator = appContext.getMarkovGenerator();

        if (endsWithSpace) {
            // 1. If user just typed a space, check if the completed word is unknown (e.g., "crossbow")
            String cleanLast = lastWord.replaceAll("[^a-zA-Z0-9'-]", "");
            if (!cleanLast.isEmpty() && !generator.containsWord(cleanLast)) {
                Button addWordBtn = new Button("+ Add '" + cleanLast + "'");
                addWordBtn.setOnAction(e -> promptAndAddUnknownWords(rawText.trim()));
                autoSuggestBox.getChildren().add(addWordBtn);
            }

            // 2. Suggest top next words based on Markov transitions from the completed word
            List<String> nextSuggestions = generator.getTopNextWords(cleanLast, 5);
            for (String suggestion : nextSuggestions) {
                Button nextBtn = new Button(suggestion);
                nextBtn.setOnAction(e -> {
                    generatedTextField.setText(rawText + suggestion + " ");
                    generatedTextField.positionCaret(generatedTextField.getText().length());
                    focusTokenInTable(suggestion);
                    handleAutoSuggest();
                });
                autoSuggestBox.getChildren().add(nextBtn);
            }
            focusTokenInTable(cleanLast);
        } else {
            // User is currently typing a word: show prefix auto-complete matches
            Set<String> suggestions = new LinkedHashSet<>();
            List<Token> nextTokens = appContext.getSbService().searchTokens(lastWord, 3);
            for (Token nextToken : nextTokens) {
                if (nextToken.getToken_text() != null) {
                    suggestions.add(nextToken.getToken_text());
                }
            }

            for (String tokenText : suggestions) {
                Button nextWordButton = new Button(tokenText);
                autoSuggestBox.getChildren().add(nextWordButton);

                nextWordButton.setOnAction(event -> {
                    words[words.length - 1] = tokenText;
                    generatedTextField.setText(String.join(" ", words) + " ");
                    generatedTextField.positionCaret(generatedTextField.getText().length());
                    focusTokenInTable(tokenText);
                    handleAutoSuggest();
                });
            }

            // If no prefix matches exist for the typed word, offer a button to add it as a new word
            String cleanTyped = lastWord.replaceAll("[^a-zA-Z0-9'-]", "");
            if (suggestions.isEmpty() && !cleanTyped.isEmpty() && !generator.containsWord(cleanTyped)) {
                Button addCustomButton = new Button("+ Add '" + cleanTyped + "'");
                addCustomButton.setOnAction(event -> promptAndAddUnknownWords(rawText.trim()));
                autoSuggestBox.getChildren().add(addCustomButton);
            }
        }
    }

    /**
     * Checks the sentence for any words not currently in the vocabulary (e.g., "crossbow"),
     * asks the user if they want to add them with frequency 1, and updates bidirectional transitions.
     */
    private void promptAndAddUnknownWords(String sentence) {
        String[] rawTokens = sentence.trim().split("\\s+");
        MarkovGenerator generator = appContext.getMarkovGenerator();
        boolean addedAny = false;

        for (int i = 0; i < rawTokens.length; i++) {
            String cleanWord = rawTokens[i].replaceAll("[^a-zA-Z0-9'-]", "");
            if (cleanWord.isEmpty()) {
                continue;
            }

            if (!generator.containsWord(cleanWord)) {
                String prevWord = (i > 0) ? rawTokens[i - 1].replaceAll("[^a-zA-Z0-9'-]", "") : null;
                String nextWord = (i < rawTokens.length - 1) ? rawTokens[i + 1].replaceAll("[^a-zA-Z0-9'-]", "") : null;

                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.setTitle("New Word Detected");
                alert.setHeaderText("'" + cleanWord + "' is not in the token list.");
                alert.setContentText(
                        "Would you like to add '" + cleanWord + "' with an initial frequency of 1\n" +
                                "and update bidirectional transitions (" +
                                (prevWord != null && !prevWord.isEmpty() ? "'" + prevWord + "' -> " : "[START] -> ") +
                                "'" + cleanWord + "'" +
                                (nextWord != null && !nextWord.isEmpty() ? " -> '" + nextWord + "'" : " -> [END]") +
                                ")?"
                );

                Optional<ButtonType> result = alert.showAndWait();
                if (result.isPresent() && result.get() == ButtonType.OK) {
                    generator.addCustomWordWithTransitions(prevWord, cleanWord, nextWord, true);
                    addedAny = true;
                }
            }
        }

        if (addedAny) {
            refreshTokens();
            String lastWord = generator.extractLastWord(sentence);
            focusTokenInTable(lastWord);
        }
    }

    /**
     * Searches the TableView for the target word and scrolls/focuses on that row.
     */
    private void focusTokenInTable(String targetWord) {
        if (targetWord == null || targetWord.trim().isEmpty() || tokens.isEmpty()) {
            return;
        }
        String cleanTarget = targetWord.trim();

        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            if (t.getToken_text() != null && t.getToken_text().trim().equalsIgnoreCase(cleanTarget)) {
                tokenTableView.getSelectionModel().clearAndSelect(i);
                tokenTableView.scrollTo(i);
                tokenTableView.getFocusModel().focus(i);
                break;
            }
        }
    }

    /**
     * Displays a popup listing all following words and their transition frequencies for a selected token.
     */
    private void showFollowingWordsPopup(String word) {
        Map<String, Integer> following = appContext.getMarkovGenerator().getFollowingWordsWithCounts(word);

        Alert infoAlert = new Alert(Alert.AlertType.INFORMATION);
        infoAlert.setTitle("Following Words & Frequencies");
        infoAlert.setHeaderText("Transitions following '" + word + "' (" + following.size() + " unique next words):");

        if (following.isEmpty()) {
            infoAlert.setContentText("No outgoing transitions recorded for '" + word + "'.");
        } else {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, Integer> entry : following.entrySet()) {
                sb.append(String.format("%-20s : %d%n", entry.getKey(), entry.getValue()));
            }
            TextArea textArea = new TextArea(sb.toString());
            textArea.setEditable(false);
            textArea.setWrapText(false);
            textArea.setPrefWidth(360);
            textArea.setPrefHeight(260);
            infoAlert.getDialogPane().setExpandableContent(textArea);
            infoAlert.getDialogPane().setExpanded(true);
        }

        infoAlert.showAndWait();
    }

    @FXML
    private void handleRefreshGeneratedSentences() {
        refreshGeneratedSentences();
    }

    private void refreshGeneratedSentences() {
        if (appContext == null) {
            return;
        }
        try {
            generatedSentences.setAll(appContext.getSbService().getGeneratedSentences());
            generatedSentenceCountLabel.setText(generatedSentences.size() + " saved sentences");
            updateMatchingSentenceHashLabel(
                    generatedSentencesTableView.getSelectionModel().getSelectedItem());
            generatedSentencesTableView.refresh();
        } catch (RuntimeException exception) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Unable to load generated sentences");
            alert.setHeaderText("Could not retrieve saved sentences from the database.");
            alert.setContentText(exception.getMessage());
            alert.showAndWait();
        }
    }

    private void updateMatchingSentenceHashLabel(GeneratedSentence selected) {
        if (selected == null || selected.getSentenceHash() == null
                || selected.getSentenceHash().isBlank()) {
            matchingSentenceHashLabel.setText("Select a sentence to highlight matching hashes");
            return;
        }

        String hash = selected.getSentenceHash();
        long matches = generatedSentences.stream()
                .filter(sentence -> hash.equals(sentence.getSentenceHash()))
                .count();
        long otherMatches = Math.max(0L, matches - 1L);
        matchingSentenceHashLabel.setText(otherMatches == 0
                ? "No other sentences with this hash"
                : otherMatches + (otherMatches == 1 ? " other matching sentence" : " other matching sentences"));
    }

    private void showGeneratedSentenceDetails(GeneratedSentence sentence) {
        if (sentence == null) {
            generatedSentenceDetails.clear();
            return;
        }
        Token starting = sentence.getStartingToken();
        String startInfo = starting == null
                ? "(none)"
                : starting.getToken_text() + " (ID " + starting.getToken_id() + ")";
        LocalDateTime time = sentence.getGeneratedAt();
        generatedSentenceDetails.setText(
                "Sentence ID: " + sentence.getSentenceId() + "\n" +
                        "Sentence: " + sentence.getSentenceText() + "\n" +
                        "Generation method: " + sentence.getGenerationMethod() + "\n" +
                        "Starting token: " + startInfo + "\n" +
                        "Added to corpus: " + (Boolean.TRUE.equals(sentence.getAddedToCorpus()) ? "Yes" : "No") + "\n" +
                        "Generated at: " + (time == null ? "" : DATE_FORMAT.format(time)) + "\n" +
                        "SHA-256: " + sentence.getSentenceHash()
        );
    }

    private void refreshTokens() {
        tokens.setAll(appContext.getSbService().getTokens());
        tokenTableView.setItems(tokens);
    }
}