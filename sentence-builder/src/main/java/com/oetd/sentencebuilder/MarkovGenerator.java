package com.oetd.sentencebuilder;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;

import java.util.*;
import java.util.stream.Collectors;

public class MarkovGenerator {

    // 3+ Generation Algorithms for UI Dropdown Selection
    public enum Algorithm {
        WEIGHTED_MARKOV("Weighted Markov Chain"),
        GREEDY_MOST_FREQUENT("Greedy (Most Frequent Next Word)"),
        RANDOM_WALK("Random Walk (Uniform Next Word)"),
        PURE_RANDOM_WORDS("Pure Random Words (Unconnected)");

        private final String displayName;

        Algorithm(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    private SessionFactory sessionFactory;

    // Maps currentWord -> (nextWord -> transitionWeight)
    private final Map<String, Map<String, Integer>> transitionMap = new HashMap<>();
    // Maps startWord -> startCount
    private final Map<String, Integer> startWordsMap = new HashMap<>();
    // Maps word -> totalCount (for fallback and pure random modes)
    private final Map<String, Integer> allWordsMap = new HashMap<>();

    private final Random random = new Random();

    public MarkovGenerator() {
    }

    public MarkovGenerator(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    public void setSessionFactory(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    /**
     * Checks if a token is purely punctuation (e.g., ",", ".", "'", "\"")
     * so we can prevent sentences from starting with punctuation.
     */
    public boolean isPunctuation(String word) {
        if (word == null || word.trim().isEmpty()) {
            return true;
        }
        // Returns true if the token contains NO letters or digits
        return !word.matches(".*[a-zA-Z0-9].*");
    }

    /**
     * Loads all tokens and transitions from the database using Hibernate ORM.
     */
    public void loadTransitionsFromDB() {
        if (sessionFactory == null) {
            System.err.println("SessionFactory is not initialized in MarkovGenerator.");
            return;
        }

        transitionMap.clear();
        startWordsMap.clear();
        allWordsMap.clear();

        try (Session session = sessionFactory.openSession()) {
            List<Token> tokens = session.createQuery("from Token", Token.class).getResultList();
            Map<Long, String> idToTextMap = new HashMap<>();

            for (Token t : tokens) {
                if (t.getToken_id() != null && t.getToken_text() != null) {
                    String text = t.getToken_text().trim();
                    idToTextMap.put(t.getToken_id(), text);

                    int totalCount = t.getTotal_count() != null ? t.getTotal_count().intValue() : 1;
                    allWordsMap.put(text, Math.max(1, totalCount));

                    // Only add to startWordsMap if start_count > 0 AND it is NOT punctuation
                    if (t.getStart_count() != null && t.getStart_count() > 0 && !isPunctuation(text)) {
                        startWordsMap.put(text, t.getStart_count().intValue());
                    }
                }
            }

            List<TokenTransition> transitions = session.createQuery("from TokenTransition", TokenTransition.class).getResultList();
            for (TokenTransition tt : transitions) {
                if (tt.getId() != null) {
                    String prevWord = idToTextMap.get(tt.getId().getPreviousTokenId());
                    String nextWord = idToTextMap.get(tt.getId().getNextTokenId());

                    if (prevWord != null && nextWord != null) {
                        int occurrences = tt.getOccurrenceCount() != null ? tt.getOccurrenceCount().intValue() : 0;
                        int userSelections = tt.getUserSelectionCount() != null ? tt.getUserSelectionCount().intValue() : 0;
                        int weight = occurrences + userSelections;

                        if (weight > 0) {
                            addTransition(prevWord, nextWord, weight);
                        }
                    }
                }
            }

            System.out.println("Loaded " + tokens.size() + " tokens and " + transitions.size() + " transitions via Hibernate ORM.");
        } catch (Exception e) {
            System.err.println("Error loading transitions via Hibernate: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Adds or updates a word transition in memory.
     */
    public void addTransition(String currentWord, String nextWord, int count) {
        if (currentWord == null || nextWord == null || count <= 0) {
            return;
        }
        transitionMap.computeIfAbsent(currentWord, k -> new HashMap<>())
                .merge(nextWord, count, Integer::sum);
        allWordsMap.merge(currentWord, count, Integer::sum);
        allWordsMap.merge(nextWord, count, Integer::sum);
    }

    /**
     * Helper for UI Auto-Complete: Returns top N suggested next words sorted by frequency.
     */
    public List<String> getTopNextWords(String currentWord, int limit) {
        Map<String, Integer> nextWords = transitionMap.get(currentWord);
        if (nextWords == null || nextWords.isEmpty()) {
            return Collections.emptyList();
        }
        return nextWords.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(limit)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * Default sentence generation (Weighted Markov Chain).
     */
    public String generateSentence(int maxLength) {
        return generateSentence(Algorithm.WEIGHTED_MARKOV, maxLength);
    }

    /**
     * Generates a sentence using the specified Algorithm and a random valid starting word.
     */
    public String generateSentence(Algorithm algorithm, int maxLength) {
        if (transitionMap.isEmpty() && allWordsMap.isEmpty()) {
            return "No data available to generate a sentence.";
        }
        String startWord = pickStartWord(algorithm);
        if (startWord == null) {
            return "No valid starting words found in DB.";
        }
        return generateSentence(startWord, maxLength, algorithm);
    }

    /**
     * Generates a sentence starting with a specific word (Weighted Markov by default).
     */
    public String generateSentence(String startWord, int maxLength) {
        return generateSentence(startWord, maxLength, Algorithm.WEIGHTED_MARKOV);
    }

    /**
     * Generates a sentence starting with a specific word and chosen Algorithm.
     */
    public String generateSentence(String startWord, int maxLength, Algorithm algorithm) {
        if (startWord == null || startWord.trim().isEmpty()) {
            return generateSentence(algorithm, maxLength);
        }

        startWord = startWord.trim();

        // If user or randomizer picked a punctuation mark as startWord, pick a clean start word instead
        if (isPunctuation(startWord)) {
            startWord = pickStartWord(algorithm);
            if (startWord == null) {
                return "No valid non-punctuation starting words found.";
            }
        }

        if (algorithm != Algorithm.PURE_RANDOM_WORDS && !transitionMap.containsKey(startWord)) {
            return "Start word ('" + startWord + "') not found or has no outgoing transitions in DB.";
        }

        StringBuilder sentence = new StringBuilder(startWord);
        String currentWord = startWord;

        for (int i = 0; i < maxLength - 1; i++) {
            String nextWord = pickNextWord(currentWord, algorithm);
            if (nextWord == null) {
                break;
            }

            // Attach punctuation directly without a leading space (e.g., "word," instead of "word ,")
            if (isPunctuation(nextWord)) {
                sentence.append(nextWord);
            } else {
                sentence.append(" ").append(nextWord);
            }

            currentWord = nextWord;

            // Stop early if the token ends with sentence-ending punctuation
            if (currentWord.endsWith(".") || currentWord.endsWith("!") || currentWord.endsWith("?")) {
                break;
            }
        }

        return sentence.toString();
    }

    /**
     * Picks a valid starting word (never punctuation).
     */
    private String pickStartWord(Algorithm algorithm) {
        // 1. Prefer words with start_count > 0 that also have outgoing transitions
        Map<String, Integer> validStarts = new HashMap<>();
        for (Map.Entry<String, Integer> entry : startWordsMap.entrySet()) {
            String word = entry.getKey();
            if (!isPunctuation(word) && (transitionMap.containsKey(word) || algorithm == Algorithm.PURE_RANDOM_WORDS)) {
                validStarts.put(word, entry.getValue());
            }
        }

        // 2. Fallback: Any non-punctuation word in transitionMap
        if (validStarts.isEmpty()) {
            for (String word : transitionMap.keySet()) {
                if (!isPunctuation(word)) {
                    validStarts.put(word, allWordsMap.getOrDefault(word, 1));
                }
            }
        }

        // 3. Last fallback: Any non-punctuation word in allWordsMap
        if (validStarts.isEmpty()) {
            for (Map.Entry<String, Integer> entry : allWordsMap.entrySet()) {
                if (!isPunctuation(entry.getKey())) {
                    validStarts.put(entry.getKey(), entry.getValue());
                }
            }
        }

        if (validStarts.isEmpty()) {
            return null;
        }

        if (algorithm == Algorithm.GREEDY_MOST_FREQUENT) {
            return getMostFrequentWord(validStarts);
        } else if (algorithm == Algorithm.RANDOM_WALK || algorithm == Algorithm.PURE_RANDOM_WORDS) {
            List<String> keys = new ArrayList<>(validStarts.keySet());
            return keys.get(random.nextInt(keys.size()));
        } else {
            return getWeightedRandomWord(validStarts);
        }
    }

    /**
     * Picks the next word according to the chosen Algorithm.
     */
    private String pickNextWord(String currentWord, Algorithm algorithm) {
        if (algorithm == Algorithm.PURE_RANDOM_WORDS) {
            if (allWordsMap.isEmpty()) return null;
            List<String> allWords = new ArrayList<>(allWordsMap.keySet());
            return allWords.get(random.nextInt(allWords.size()));
        }

        Map<String, Integer> nextWords = transitionMap.get(currentWord);
        if (nextWords == null || nextWords.isEmpty()) {
            return null;
        }

        switch (algorithm) {
            case GREEDY_MOST_FREQUENT:
                return getMostFrequentWord(nextWords);
            case RANDOM_WALK:
                List<String> candidates = new ArrayList<>(nextWords.keySet());
                return candidates.get(random.nextInt(candidates.size()));
            case WEIGHTED_MARKOV:
            default:
                return getWeightedRandomWord(nextWords);
        }
    }



    private String getWeightedRandomWord(Map<String, Integer> wordWeights) {
        int totalWeight = 0;
        for (int weight : wordWeights.values()) {
            totalWeight += weight;
        }
        if (totalWeight <= 0) {
            return null;
        }

        int randVal = random.nextInt(totalWeight);
        int cumulative = 0;
        for (Map.Entry<String, Integer> entry : wordWeights.entrySet()) {
            cumulative += entry.getValue();
            if (randVal < cumulative) {
                return entry.getKey();
            }
        }
        return null;
    }

    private String getMostFrequentWord(Map<String, Integer> wordWeights) {
        String bestWord = null;
        int maxWeight = -1;
        for (Map.Entry<String, Integer> entry : wordWeights.entrySet()) {
            if (entry.getValue() > maxWeight) {
                maxWeight = entry.getValue();
                bestWord = entry.getKey();
            }
        }
        return bestWord;
    }

    public Map<String, Map<String, Integer>> getTransitionMap() {
        return transitionMap;
    }

    // Standalone test runner
    public static void main(String[] args) {
        String url = System.getenv("SB_DB_URL");
        String user = System.getenv("SB_DB_USER");
        String password = System.getenv("SB_DB_PASSWORD");

        StandardServiceRegistryBuilder builder = new StandardServiceRegistryBuilder();
        // Prevent dropping tables during standalone test
        builder.applySetting("hibernate.hbm2ddl.auto", "validate");

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
                generator.addTransition("The", "lazy", 2);
                generator.addTransition("quick", "brown", 5);
                generator.addTransition("lazy", "dog", 3);
                generator.addTransition("brown", "fox", 4);
                generator.addTransition("fox", ",", 3);
                generator.addTransition(",", "jumps", 3);
                generator.addTransition("jumps", "over", 4);
                generator.addTransition("over", "the", 4);
                generator.addTransition("the", "lazy", 4);
                generator.addTransition("dog", ".", 5);
            }

            System.out.println("\n=== [Testing All 4 Generation Algorithms] ===");
            for (Algorithm algo : Algorithm.values()) {
                System.out.println("[" + algo.getDisplayName() + "] -> " + generator.generateSentence(algo, 20));
            }

        } catch (Exception e) {
            StandardServiceRegistryBuilder.destroy(registry);
            e.printStackTrace();
        }
    }
}