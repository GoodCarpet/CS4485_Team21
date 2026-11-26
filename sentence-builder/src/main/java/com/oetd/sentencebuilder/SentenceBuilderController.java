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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Locale;

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

    // James Parker - 11/25/2026
    // Updated auto-complete handlers to use the dedicated autoCompleteTextArea and normalize input to lowercase so capitalized words match the database.
    // AI-assisted (Gemini) - Prompt: "Can you elaborate on what to do for the capitalization bug?"
    @FXML
    private TextArea autoCompleteTextArea;

    // Aniq Sunesara
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

    // James Parker
    // Imported Files tab, displaying file upload history.
    @FXML private Tab importedFilesTab;
    @FXML private TableView<ImportedFile> importedFilesTableView;
    @FXML private TableColumn<ImportedFile, Long> fileIdColumn;
    @FXML private TableColumn<ImportedFile, String> fileNameColumn;
    @FXML private TableColumn<ImportedFile, Long> fileTokenCountColumn;
    @FXML private TableColumn<ImportedFile, String> fileImportedAtColumn;
    @FXML private TableColumn<ImportedFile, String> fileHashColumn;
    @FXML private Label importedFileCountLabel;

    private final ObservableList<ImportedFile> importedFiles = FXCollections.observableArrayList();

    // James Parker
    // Batch Generator tab controls and pending sentences list
    @FXML private ComboBox<MarkovGenerator.Algorithm> batchAlgorithmComboBox;
    @FXML private TextField batchCountField;
    @FXML private Button addBatchButton;
    @FXML private TextArea batchOutputArea;

    private final java.util.List<String> pendingBatchSentences = new java.util.ArrayList<>();
    private MarkovGenerator.Algorithm lastBatchAlgorithm = MarkovGenerator.Algorithm.WEIGHTED_MARKOV;

    public SentenceBuilderController(AppContext appContext) {
        this.appContext = appContext;
    }

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

    // Tracks the last processed word index to avoid incrementing transition counts multiple times for the same word
    private int lastRecordedWordCount = 0;

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
                    generatedSentencesTableView.refresh();
                });

        generatedSentencesTab.setOnSelectionChanged(event -> {
            if (generatedSentencesTab.isSelected() && appContext != null) {
                refreshGeneratedSentences();
            }
        });

        fileIdColumn.setCellValueFactory(cell ->
                new ReadOnlyObjectWrapper<>(cell.getValue().getFileId()));
        fileNameColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().getFileName()));
        fileTokenCountColumn.setCellValueFactory(cell ->
                new ReadOnlyObjectWrapper<>(cell.getValue().getTokenCount()));
        fileImportedAtColumn.setCellValueFactory(cell -> {
            LocalDateTime timestamp = cell.getValue().getImportedAt();
            return new ReadOnlyStringWrapper(timestamp == null ? "" : DATE_FORMAT.format(timestamp));
        });
        fileHashColumn.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().getFileHash()));

        importedFilesTableView.setItems(importedFiles);
        importedFilesTableView.setPlaceholder(new Label("No files imported yet."));

        importedFilesTab.setOnSelectionChanged(event -> {
            if (importedFilesTab.isSelected() && appContext != null) {
                refreshImportedFiles();
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

        batchAlgorithmComboBox.setItems(FXCollections.observableArrayList(MarkovGenerator.Algorithm.values()));
        batchAlgorithmComboBox.setValue(MarkovGenerator.Algorithm.WEIGHTED_MARKOV);

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

        MarkovGenerator generator = appContext.getMarkovGenerator();
        List<String> suggestions = generator.getTopNextWords(null, 3);
        for (String suggestion : suggestions) {
            this.addSuggestionButton(suggestion);
        }

        refreshTokens();
    }

    @FXML
    private void handleImport() {
        File selectedFile = fileChooser.showOpenDialog(stage);
        if (selectedFile != null) {
            try {
                appContext.getSbService().addFile(selectedFile);
                refreshTokens();
                refreshImportedFiles();
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
    private void handleRefreshImportedFiles() {
        refreshImportedFiles();
    }

    // James Parker - 11/25/2026
    // Refreshes the Imported Files table and updates the file count label.
    // AI-assisted (Gemini) - Prompt: "Can you give me the code for the imported file history tab? Both for fxml and java please."
    private void refreshImportedFiles() {
        if (appContext == null) {
            return;
        }
        importedFiles.setAll(appContext.getSbService().getImportedFiles());
        importedFileCountLabel.setText(importedFiles.size() + " imported files");
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
        lastRecordedWordCount = sentence.trim().split("\\s+").length;

        appContext.getSbService().addGeneratedSentence(sentence, algorithm.getDisplayName());
        refreshGeneratedSentences();

        // Focus the TableView on the last word of the generated sentence
        String lastWord = appContext.getMarkovGenerator().extractLastWord(sentence);
        focusTokenInTable(lastWord);
    }

    @FXML
    private void handleBatchGenerate() {
        int count;
        try {
            count = Integer.parseInt(batchCountField.getText().trim());
            if (count <= 0) {
                return;
            }
        } catch (NumberFormatException e) {
            return;
        }

        MarkovGenerator.Algorithm algorithm = batchAlgorithmComboBox.getValue();
        if (algorithm == null) {
            algorithm = MarkovGenerator.Algorithm.WEIGHTED_MARKOV;
        }
        lastBatchAlgorithm = algorithm;

        pendingBatchSentences.clear();
        StringBuilder display = new StringBuilder();

        for (int i = 0; i < count; i++) {
            // Uses random starting words
            String sentence = appContext.getMarkovGenerator().generateSentence(algorithm, 20);
            pendingBatchSentences.add(sentence);
            display.append(sentence).append("\n");
        }

        batchOutputArea.setText(display.toString());
        addBatchButton.setDisable(false);
    }

    // James Parker - 11/25/2026
    // Saves the pending batch of generated sentences to the database and reloads token counts and Markov transitions.
    // AI-assisted (Gemini) - Prompt: "Batch generation is next, and that must have random starting words. Should that be its own new tab?"
    @FXML
    private void handleAddBatchToDatabase() {
        if (pendingBatchSentences.isEmpty()) {
            return;
        }

        int addedCount = 0;
        for (String sentence : pendingBatchSentences) {
            appContext.getSbService().addGeneratedSentence(sentence, lastBatchAlgorithm.getDisplayName());
            addedCount++;
        }

        pendingBatchSentences.clear();
        addBatchButton.setDisable(true);

        // Refresh UI tables and in-memory Markov transitions
        refreshTokens();
        refreshGeneratedSentences();
        appContext.getMarkovGenerator().loadTransitionsFromDB();
    }

    private void addSuggestionButton(String suggestion) {
        String rawText = autoCompleteTextArea.getText() == null ? "" : autoCompleteTextArea.getText();

        Button nextBtn = new Button(suggestion);

        nextBtn.setOnAction(e -> {
            String baseText = rawText.endsWith(" ") || rawText.isEmpty() ? rawText : rawText + " ";
            String[] prevWords = baseText.trim().isEmpty() ? new String[0] : baseText.trim().split("\\s+");
            String prevWord = prevWords.length > 0
                    ? prevWords[prevWords.length - 1].replaceAll("[^a-zA-Z0-9'-]", "").toLowerCase(Locale.ROOT)
                    : null;

            // Increment user selection count in memory and DB when user selects a suggested word
            if (prevWord != null && !prevWord.isEmpty()) {
                appContext.getMarkovGenerator().addCustomWordWithTransitions(prevWord, suggestion, null, true);
            }

            String updatedText = baseText + suggestion + " ";
            autoCompleteTextArea.setText(updatedText);
            autoCompleteTextArea.positionCaret(updatedText.length());
            lastRecordedWordCount = updatedText.trim().split("\\s+").length;

            focusTokenInTable(suggestion);
            handleAutoSuggest();
        });

        autoSuggestBox.getChildren().add(nextBtn);
    }
    // Minchan Kwon - 11/23/2026
// Triggers next-word suggestions only on word boundaries (space, period, comma), increments existing transition counts, and offers adding unknown words.
// AI-assisted (Gemini) - Prompt: "Update handleAutoSuggest to trigger only on space, period, or comma, increment counts for existing words, and prompt to add unknown words."
    @FXML
    private void handleAutoSuggest() {
        autoSuggestBox.getChildren().clear();

        String rawText = autoCompleteTextArea.getText();
        if (rawText == null || rawText.trim().isEmpty()) {
            lastRecordedWordCount = 0;
            return;
        }

        // Requirement: Do NOT predict words while mid-typing.
        // Only trigger next-word suggestions when encountering a space (' '), period ('.'), or comma (',').
        char lastChar = rawText.charAt(rawText.length() - 1);
        boolean isWordBoundary = (lastChar == ' ' || lastChar == '.' || lastChar == ',');

        if (!isWordBoundary) {
            return;
        }

        String[] words = rawText.trim().split("\\s+");
        if (words.length == 0) {
            return;
        }

        if (words.length < lastRecordedWordCount) {
            lastRecordedWordCount = words.length;
        }

        MarkovGenerator generator = appContext.getMarkovGenerator();
        String completedToken = words[words.length - 1];
        String cleanCompleted = completedToken.replaceAll("[^a-zA-Z0-9'-]", "").toLowerCase(Locale.ROOT);
        String prevWord = (words.length >= 2)
                ? words[words.length - 2].replaceAll("[^a-zA-Z0-9'-]", "").toLowerCase(Locale.ROOT)
                : null;

        if (!cleanCompleted.isEmpty() && !generator.containsWord(cleanCompleted)) {
            Button addWordBtn = new Button("+ Add '" + cleanCompleted + "'");
            addWordBtn.setOnAction(e -> promptAndAddUnknownWords(rawText.trim()));
            autoSuggestBox.getChildren().add(addWordBtn);
        } else if (!cleanCompleted.isEmpty() && words.length > lastRecordedWordCount) {
            // 2. Requirement: If the typed word IS already in the database,
            // increment the count of how often it follows the previous word
            if (prevWord != null && !prevWord.isEmpty()) {
                generator.addCustomWordWithTransitions(prevWord, cleanCompleted, null, true);
            }
            lastRecordedWordCount = words.length;
        }

        // 3. Determine lookup key: if ending with ',' or '.', predict from the punctuation token if tracked,
        // otherwise predict from the completed word
        String lookupKey = (lastChar == '.' || lastChar == ',') ? String.valueOf(lastChar) : cleanCompleted;
        List<String> nextSuggestions = generator.getTopNextWords(lookupKey, 5);
        if (nextSuggestions.isEmpty() && (lastChar == '.' || lastChar == ',')) {
            nextSuggestions = generator.getTopNextWords(cleanCompleted, 5);
        }

        for (String suggestion : nextSuggestions) {
            this.addSuggestionButton(suggestion);
        }
        if (!cleanCompleted.isEmpty()) {
            focusTokenInTable(cleanCompleted);
        }
    }

    /**
     * Checks the sentence for any words not currently in the vocabulary (e.g., "crossbow"),
     * asks the user if they want to add them with frequency 1, and updates bidirectional transitions.
     */
// Minchan Kwon - 11/21/2026
// Detects unknown words in user input and opens a confirmation dialog to add them with bidirectional transitions.
// AI-assisted (Gemini) - Prompt: "Create a JavaFX confirmation dialog to add unknown words with initial frequency 1 and update bidirectional transitions."
    private void promptAndAddUnknownWords(String sentence) {
        String[] rawTokens = sentence.trim().split("\\s+");
        MarkovGenerator generator = appContext.getMarkovGenerator();
        boolean addedAny = false;

        for (int i = 0; i < rawTokens.length; i++) {
            String cleanWord = rawTokens[i].replaceAll("[^a-zA-Z0-9'-]", "").toLowerCase(Locale.ROOT);
            if (cleanWord.isEmpty()) {
                continue;
            }

            if (!generator.containsWord(cleanWord)) {
                String prevWord = (i > 0) ? rawTokens[i - 1].replaceAll("[^a-zA-Z0-9'-]", "").toLowerCase(Locale.ROOT) : null;
                String nextWord = (i < rawTokens.length - 1) ? rawTokens[i + 1].replaceAll("[^a-zA-Z0-9'-]", "").toLowerCase(Locale.ROOT) : null;

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
            lastRecordedWordCount = rawTokens.length;
            String lastWord = generator.extractLastWord(sentence);
            focusTokenInTable(lastWord);
        }
    }

    /**
     * Searches the TableView for the target word and scrolls/focuses on that row.
     */

// Minchan Kwon - 11/21/2026
// Searches the token TableView for the last word and scrolls/focuses on that row.
// AI-assisted (Gemini) - Prompt: "Write a method to find a token in JavaFX TableView by text and auto-scroll/focus on it."
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
    // Minchan Kwon - 11/21/2026
// Opens an alert popup showing all following words and transition frequencies when a token row is double-clicked.
// AI-assisted (Gemini) - Prompt: "Show a JavaFX popup listing all following words and their frequencies when double-clicking a TableView row."
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