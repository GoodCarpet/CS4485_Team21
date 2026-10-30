package com.oetd.sentencebuilder;

import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;

import java.util.*;

// Minchan Kwon
// Class to handle Markov Chain text generation using Hibernate ORM (Token & TokenTransition)
public class MarkovGenerator {
    private final SessionFactory sessionFactory;
    // Key = Current Word, Value = Map of (Next Word -> Weight/Frequency)
    private final Map<String, Map<String, Integer>> transitionMap;
    // Key = Start Word, Value = start_count (for picking a random starting word)
    private final Map<String, Integer> startWordsMap;
    private final Random random;

    // Constructor accepting the shared Hibernate SessionFactory
    public MarkovGenerator(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
        this.transitionMap = new HashMap<>();
        this.startWordsMap = new HashMap<>();
        this.random = new Random();
    }

    // 1. Load token and transition data from DB using Hibernate ORM
    public void loadTransitionsFromDB() {
        transitionMap.clear();
        startWordsMap.clear();

        if (sessionFactory == null) {
            System.out.println("SessionFactory is null. Skipping ORM query.");
            return;
        }

        try {
            sessionFactory.inTransaction(session -> {
                // 1) Load all tokens and map token_id -> token_text
                List<Token> tokens = session.createQuery("from Token", Token.class).getResultList();
                Map<Long, String> idToWordMap = new HashMap<>();

                for (Token t : tokens) {
                    idToWordMap.put(t.getToken_id(), t.getToken_text());
                    if (t.getStart_count() != null && t.getStart_count() > 0) {
                        startWordsMap.put(t.getToken_text(), t.getStart_count().intValue());
                    }
                }

                // 2) Load transitions from TokenTransition entity
                List<TokenTransition> transitions = session.createQuery("from TokenTransition", TokenTransition.class).getResultList();
                int loadedTransitions = 0;

                for (TokenTransition tt : transitions) {
                    Long prevId = null;
                    Long nextId = null;

                    // Extract IDs via composite key
                    if (tt.getId() != null) {
                        prevId = tt.getId().getPreviousTokenId();
                        nextId = tt.getId().getNextTokenId();
                    }

                    String prevWord = idToWordMap.get(prevId);
                    String nextWord = idToWordMap.get(nextId);
                    Long occCount = tt.getOccurrenceCount();

                    if (prevWord != null && nextWord != null && occCount != null && occCount > 0) {
                        addTransition(prevWord, nextWord, occCount.intValue());
                        loadedTransitions++;
                    }
                }

                System.out.println("Loaded " + tokens.size() + " tokens and " + loadedTransitions + " transitions via Hibernate ORM.");
            });
        } catch (Exception e) {
            System.out.println("Error loading transitions via Hibernate: " + e.getMessage());
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

    // Standalone test method using Hibernate SessionFactory with Environment Variables
    public static void main(String[] args) {
        String url = System.getenv("SB_DB_URL");
        String user = System.getenv("SB_DB_USER");
        String password = System.getenv("SB_DB_PASSWORD");

        StandardServiceRegistryBuilder builder = new StandardServiceRegistryBuilder();
        if (url != null) {
            builder.applySetting("hibernate.connection.url", url);
            builder.applySetting("jakarta.persistence.jdbc.url", url);
        }
        if (user != null) {
            builder.applySetting("hibernate.connection.username", user);
            builder.applySetting("jakarta.persistence.jdbc.user", user);
        }
        if (password != null) {
            builder.applySetting("hibernate.connection.password", password);
            builder.applySetting("jakarta.persistence.jdbc.password", password);
        }

        final StandardServiceRegistry registry = builder.build();
        try (SessionFactory sessionFactory = new MetadataSources(registry)
                .addAnnotatedClass(Token.class)
                .addAnnotatedClass(TokenTransition.class)
                .buildMetadata()
                .buildSessionFactory()) {

            MarkovGenerator generator = new MarkovGenerator(sessionFactory);
            generator.loadTransitionsFromDB();

            if (generator.transitionMap.isEmpty()) {
                System.out.println("DB transition tables are currently empty! Testing with fallback sample data...");
                generator.addTransition("The", "quick", 5);
                generator.addTransition("quick", "brown", 5);
                generator.addTransition("brown", "fox.", 4);
                System.out.println("Sample Sentence: " + generator.generateSentence("The", 15));
            } else {
                System.out.println("\n=== [Generating Sentences via Hibernate ORM] ===");
                for (int i = 1; i <= 5; i++) {
                    System.out.println("Sentence " + i + ": " + generator.generateSentence(20));
                }
            }
        } catch (Exception e) {
            StandardServiceRegistryBuilder.destroy(registry);
            e.printStackTrace();
        }
    }
}