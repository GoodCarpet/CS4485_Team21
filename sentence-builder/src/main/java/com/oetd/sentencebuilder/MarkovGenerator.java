package com.oetd.sentencebuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;

// Class to handle Markov Chain text generation integrated with Aiven MySQL DB
public class MarkovGenerator {
    private DataBaseConnection db;
    // Key = Current Word, Value = Map of (Next Word -> Weight/Frequency)
    private Map<String, Map<String, Integer>> transitionMap;
    // Key = Start Word, Value = start_count (for picking a random starting word)
    private Map<String, Integer> startWordsMap;
    // Map to keep track of token_text -> token_id for saving generated sentences
    private Map<String, Long> tokenIdMap;
    private Random random;

    public MarkovGenerator() throws SQLException {
        this.db = new DataBaseConnection();
        this.transitionMap = new HashMap<>();
        this.startWordsMap = new HashMap<>();
        this.tokenIdMap = new HashMap<>();
        this.random = new Random();
    }

    // 1. Load actual token and transition data from the database
    public void loadTransitionsFromDB() {
        transitionMap.clear();
        startWordsMap.clear();
        tokenIdMap.clear();

        // Load tokens and starting word weights
        String tokenQuery = "SELECT token_id, token_text, start_count, end_count FROM Token";
        // Join Token_Transition with Token twice to get the actual strings for previous and next words
        String transitionQuery =
                "SELECT t1.token_text AS prev_word, t2.token_text AS next_word, " +
                        "       tt.occurrence_count, tt.user_selection_count " +
                        "FROM Token_Transition tt " +
                        "JOIN Token t1 ON tt.previous_token_id = t1.token_id " +
                        "JOIN Token t2 ON tt.next_token_id = t2.token_id";

        try {
            ResultSet tokenRs = db.askQuery(tokenQuery);
            int totalTokens = 0;
            while (tokenRs.next()) {
                long id = tokenRs.getLong("token_id");
                String text = tokenRs.getString("token_text");
                int startCount = tokenRs.getInt("start_count");

                tokenIdMap.put(text, id);
                System.out.println("Found token in DB: " + text + " (id: " + id + ")");
                if (startCount > 0) {
                    startWordsMap.put(text, startCount);
                }
                totalTokens++;
            }

            ResultSet transRs = db.askQuery(transitionQuery);
            int totalTransitions = 0;
            while (transRs.next()) {
                String prevWord = transRs.getString("prev_word");
                String nextWord = transRs.getString("next_word");
                int occCount = transRs.getInt("occurrence_count");
                int userCount = transRs.getInt("user_selection_count");

                // Total weight combines natural occurrence and user selection
                int weight = occCount + userCount;
                if (weight > 0) {
                    addTransition(prevWord, nextWord, weight);
                    totalTransitions++;
                }
            }

            System.out.println("Loaded " + totalTokens + " tokens and " + totalTransitions + " transitions from DB.");

        } catch (SQLException e) {
            System.out.println("Error loading transitions from DB: " + e.getMessage());
        }
    }

    // 2. Add word transition data into memory
    public void addTransition(String currentWord, String nextWord, int count) {
        transitionMap.putIfAbsent(currentWord, new HashMap<>());
        Map<String, Integer> nextWords = transitionMap.get(currentWord);
        nextWords.put(nextWord, nextWords.getOrDefault(nextWord, 0) + count);
    }

    // 3. Pick a random starting word based on start_count weights in the DB
    public String getRandomStartWord() {
        if (!startWordsMap.isEmpty()) {
            return pickWeightedRandom(startWordsMap);
        }
        // Fallback: pick any available word in transitionMap if no start_count > 0 exists
        if (!transitionMap.isEmpty()) {
            List<String> keys = new ArrayList<>(transitionMap.keySet());
            return keys.get(random.nextInt(keys.size()));
        }
        return null;
    }

    // 4. Pick the next word based on weighted random probability
    public String getNextWord(String currentWord) {
        Map<String, Integer> nextWords = transitionMap.get(currentWord);
        if (nextWords == null || nextWords.isEmpty()) {
            return null;
        }
        return pickWeightedRandom(nextWords);
    }

    // Helper method for weighted random selection
    private String pickWeightedRandom(Map<String, Integer> weightMap) {
        int totalWeight = 0;
        for (int weight : weightMap.values()) {
            totalWeight += weight;
        }
        if (totalWeight <= 0) return null;

        int randWeight = random.nextInt(totalWeight);
        int cumulativeWeight = 0;

        for (Map.Entry<String, Integer> entry : weightMap.entrySet()) {
            cumulativeWeight += entry.getValue();
            if (randWeight < cumulativeWeight) {
                return entry.getKey();
            }
        }
        return null;
    }

    // 5. Generate a sentence starting from a specific word
    public String generateSentence(String startWord, int maxLength) {
        if (startWord == null || !transitionMap.containsKey(startWord)) {
            return "Start word ('" + startWord + "') not found or has no outgoing transitions in DB.";
        }

        StringBuilder sentence = new StringBuilder(startWord);
        String currentWord = startWord;

        for (int i = 0; i < maxLength - 1; i++) {
            String nextWord = getNextWord(currentWord);
            if (nextWord == null) {
                break;
            }
            sentence.append(" ").append(nextWord);
            currentWord = nextWord;

            if (nextWord.endsWith(".") || nextWord.endsWith("!") || nextWord.endsWith("?")) {
                break;
            }
        }
        return sentence.toString();
    }

    // 6. Generate a sentence using a random starting word from DB
    public String generateSentence(int maxLength) {
        String startWord = getRandomStartWord();
        if (startWord == null) {
            return "No data available in the database to generate a sentence.";
        }
        return generateSentence(startWord, maxLength);
    }

    // 7. Save the generated sentence back into the Generated_Sentence table
    public void saveGeneratedSentenceToDB(String sentenceText, String startWord) {
        Long startTokenId = tokenIdMap.get(startWord);
        String hash = sha256(sentenceText);
        String sql = "INSERT INTO Generated_Sentence (sentence_text, sentence_hash, generation_method, starting_token_id, added_to_corpus) " +
                "VALUES (?, ?, 'Markov Chain', ?, 0)";
        try (PreparedStatement pstmt = db.dbconn.prepareStatement(sql)) {
            pstmt.setString(1, sentenceText);
            pstmt.setString(2, hash);
            if (startTokenId != null) {
                pstmt.setLong(3, startTokenId);
            } else {
                pstmt.setNull(3, Types.BIGINT);
            }
            pstmt.executeUpdate();
            System.out.println("Saved generated sentence to DB successfully.");
        } catch (SQLException e) {
            System.out.println("Error saving sentence to DB: " + e.getMessage());
        }
    }

    // Helper for generating SHA-256 hash for sentence_hash column
    private String sha256(String base) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(base.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    // Main method for testing DB data loading and sentence generation
    public static void main(String[] args) {
        try {
            MarkovGenerator generator = new MarkovGenerator();

            // Load actual transitions from Aiven DB
            generator.loadTransitionsFromDB();

            // Check if DB already has data; if empty, run with fallback sample data
            if (generator.transitionMap.isEmpty()) {
                System.out.println("DB is currently empty! Testing with fallback sample data instead...");
                generator.addTransition("The", "quick", 5);
                generator.addTransition("The", "lazy", 3);
                generator.addTransition("quick", "brown", 5);
                generator.addTransition("lazy", "dog.", 3);
                generator.addTransition("brown", "fox", 4);
                generator.addTransition("fox", "jumps", 4);
                generator.addTransition("jumps", "over", 4);
                generator.addTransition("over", "the", 4);
                generator.addTransition("the", "lazy", 4);

                for (int i = 1; i <= 3; i++) {
                    System.out.println("Sample Sentence " + i + ": " + generator.generateSentence("The", 15));
                }
            } else {
                System.out.println("\n=== [Generating Sentences from Real DB Data] ===");
                for (int i = 1; i <= 5; i++) {
                    System.out.println("DB Sentence " + i + ": " + generator.generateSentence(20));
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}