package com.oetd.sentencebuilder;

import org.hibernate.SessionFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Aniq Sunesara
// Prompt: "in sbservice, create an updated addfile function that will split the words, normalize them,
// and separate punctuations into their own tokens. Then update the Token table, Token_Transition table, and the Imported_File Table accordingly."

// Aniq Sunesara
// Prompt: "in SBService, add a function that takes a string of a sentence and string of a generation method as input.
// The function will then parse the sentence, update the counts of the tokens used, and adds the generated sentence and
// all its properties into the database via the GeneratedSentence ORM"
/** Import normalized words and standalone punctuation into the shared MySQL schema. */
public class SBService {
    // Keeps prepared statements small when importing an entire novel.
    private static final int SQL_BATCH_SIZE = 250;

    // A sequence of letters/marks/numbers, or one punctuation/symbol character.
    private static final Pattern TOKEN_PATTERN = Pattern.compile(
            "[\\p{L}\\p{M}\\p{N}]+|[^\\p{L}\\p{M}\\p{N}\\s]"
    );
    private static final Pattern WORD_TOKEN_PATTERN = Pattern.compile(
            "[\\p{L}\\p{M}\\p{N}]+"
    );

    private final SessionFactory sessionFactory;

    public SBService(SessionFactory sf) {
        this.sessionFactory = sf;
    }

    public void addFile(File file) throws IOException {
        if (file == null || !file.isFile()) {
            throw new IOException("Please select an existing text file.");
        }
        if (file.getName().length() > 255) {
            throw new IOException("File name exceeds the 255-character limit.");
        }

        byte[] bytes = Files.readAllBytes(file.toPath());
        String fileHash = sha256Hex(bytes);
        String content = new String(bytes, StandardCharsets.UTF_8);

        List<String> words = new ArrayList<>();
        Matcher matcher = TOKEN_PATTERN.matcher(content);
        while (matcher.find()) {
            String word = normalizeToken(matcher.group());
            if (word.length() > 255) {
                throw new IOException("Token is longer than 255 characters: " + word);
            }
            words.add(word);
        }

        Map<String, Long> counts = new LinkedHashMap<>();
        for (String word : words) {
            counts.merge(word, 1L, Long::sum);
        }

        // Sentence counts apply to the first/last WORD, not punctuation.
        Map<String, Long> sentenceStarts = new HashMap<>();
        Map<String, Long> sentenceEnds = new HashMap<>();
        String lastWord = null;
        boolean nextWordStartsSentence = true;
        for (String word : words) {
            if (WORD_TOKEN_PATTERN.matcher(word).matches()) {
                if (nextWordStartsSentence) {
                    sentenceStarts.merge(word, 1L, Long::sum);
                    nextWordStartsSentence = false;
                }
                lastWord = word;
            } else if (isSentenceTerminator(word) && !nextWordStartsSentence) {
                sentenceEnds.merge(lastWord, 1L, Long::sum);
                lastWord = null;
                nextWordStartsSentence = true;
            }
        }
        if (!nextWordStartsSentence) {
            sentenceEnds.merge(lastWord, 1L, Long::sum);
        }

        try {
            // The duplicate-file check, token updates, transitions, and import
            // history are all part of the SAME database transaction.
            sessionFactory.inTransaction(session -> {
                boolean alreadyImported = !session.createQuery(
                                "select f.fileId from ImportedFile f where f.fileHash = :hash",
                                Long.class)
                        .setParameter("hash", fileHash)
                        .setMaxResults(1)
                        .getResultList()
                        .isEmpty();
                if (alreadyImported) {
                    throw new DuplicateImportException(
                            "These file contents were already imported: " + file.getName());
                }

                // Reserve the hash before modifying token counts. On any failure,
                // the whole transaction (including this new row) is rolled back.
                session.persist(new ImportedFile(file.getName(), fileHash, words.size()));
                session.flush();

                // Native MySQL UPSERT follows the table's UNIQUE key/collation.
                // Java's string comparison alone cannot do that: e.g., a MySQL
                // accent-insensitive collation may consider 'mole' = 'môle'.
                session.doWork(connection -> {
                    Map<String, Long> tokenIds = upsertTokens(
                            connection, counts, sentenceStarts, sentenceEnds);
                    upsertTransitions(connection, words, tokenIds);
                });
            });
        } catch (DuplicateImportException ex) {
            throw new IOException(ex.getMessage(), ex);
        } catch (RuntimeException ex) {
            throw new IOException("Database import failed for " + file.getName(), ex);
        }
    }


    /**
     * Records a generated sentence and includes it in the token corpus.
     *
     * The sentence is stored as supplied (apart from outer whitespace), while its
     * tokens are NFKC-normalized and lowercased using the same rules as addFile.
     * Updates to Token, Token_Transition, and Generated_Sentence are atomic.
     *
     * @return the persisted GeneratedSentence, including its generated sentenceId
     */
    public GeneratedSentence addGeneratedSentence(
            String sentenceText, String generationMethod) {

        if (sentenceText == null || sentenceText.isBlank()) {
            throw new IllegalArgumentException("Generated sentence cannot be empty.");
        }
        if (generationMethod == null || generationMethod.isBlank()) {
            throw new IllegalArgumentException("Generation method cannot be empty.");
        }

        String displayText = sentenceText.strip();
        String method = generationMethod.strip();
        if (method.length() > 40) {
            throw new IllegalArgumentException("Generation method exceeds 40 characters.");
        }
        // MySQL TEXT has a limit of about 64 KiB. Reject oversize text before
        // modifying any tables, using its encoded size rather than Java length.
        if (displayText.getBytes(StandardCharsets.UTF_8).length > 65_000) {
            throw new IllegalArgumentException("Generated sentence is too long for the TEXT column.");
        }

        List<String> words = new ArrayList<>();
        Matcher matcher = TOKEN_PATTERN.matcher(displayText);
        while (matcher.find()) {
            String word = normalizeToken(matcher.group());
            if (word.length() > 255) {
                throw new IllegalArgumentException("Token is longer than 255 characters: " + word);
            }
            words.add(word);
        }

        // Every generated sentence must contain a word/number (not only marks).
        String firstWord = null;
        Map<String, Long> counts = new LinkedHashMap<>();
        Map<String, Long> sentenceStarts = new HashMap<>();
        Map<String, Long> sentenceEnds = new HashMap<>();
        String lastWord = null;
        boolean nextWordStartsSentence = true;

        for (String word : words) {
            counts.merge(word, 1L, Long::sum);
            if (WORD_TOKEN_PATTERN.matcher(word).matches()) {
                if (firstWord == null) {
                    firstWord = word;
                }
                if (nextWordStartsSentence) {
                    sentenceStarts.merge(word, 1L, Long::sum);
                    nextWordStartsSentence = false;
                }
                lastWord = word;
            } else if (isSentenceTerminator(word) && !nextWordStartsSentence) {
                sentenceEnds.merge(lastWord, 1L, Long::sum);
                lastWord = null;
                nextWordStartsSentence = true;
            }
        }
        if (firstWord == null) {
            throw new IllegalArgumentException("Generated sentence must contain at least one word or number.");
        }
        if (!nextWordStartsSentence) {
            sentenceEnds.merge(lastWord, 1L, Long::sum);
        }

        // The schema documents sentence_hash as SHA-256 of normalized text.
        // Joining normalized tokens makes spacing/case differences equivalent.
        String sentenceHash = sha256Hex(
                String.join(" ", words).getBytes(StandardCharsets.UTF_8));
        String startingWord = firstWord;

        try {
            return sessionFactory.fromTransaction(session -> {
                // Native SQL updates share Hibernate's active transaction.
                Map<String, Long> tokenIds = session.doReturningWork(connection -> {
                    Map<String, Long> ids = upsertTokens(
                            connection, counts, sentenceStarts, sentenceEnds);
                    upsertTransitions(connection, words, ids);
                    return ids;
                });

                Long firstId = tokenIds.get(startingWord);
                if (firstId == null) {
                    throw new IllegalStateException("Could not resolve starting token ID.");
                }
                Token startingToken = session.find(Token.class, firstId);
                if (startingToken == null) {
                    throw new IllegalStateException("Starting token was not found: " + firstId);
                }

                GeneratedSentence generated = new GeneratedSentence(
                        displayText, sentenceHash, method, startingToken);
                // Counts and transitions were updated, so this sentence has
                // actually been included in the corpus.
                generated.setAddedToCorpus(true);
                session.persist(generated);
                session.flush(); // Assign sentence_id before returning.
                return generated;
            });
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Failed to save generated sentence.", ex);
        }
    }

    private static Map<String, Long> upsertTokens(
            Connection connection,
            Map<String, Long> counts,
            Map<String, Long> starts,
            Map<String, Long> ends) throws SQLException {

        List<Map.Entry<String, Long>> entries = new ArrayList<>(counts.entrySet());
        Map<String, Long> tokenIds = new HashMap<>();

        for (int from = 0; from < entries.size(); from += SQL_BATCH_SIZE) {
            List<Map.Entry<String, Long>> batch = entries.subList(
                    from, Math.min(from + SQL_BATCH_SIZE, entries.size()));

            StringJoiner values = new StringJoiner(",");
            for (int i = 0; i < batch.size(); i++) {
                // The deployed Token table requires an explicit created_at value.
                // CURRENT_TIMESTAMP is evaluated by MySQL, so no Java parameter is needed.
                values.add("(?,?,?,?,CURRENT_TIMESTAMP)");
            }

            // MySQL 8.4 syntax: row alias after VALUES, not deprecated VALUES(col).
            // On duplicate keys, only frequency counters change; created_at stays intact.
            String upsertSql =
                    "INSERT INTO `Token` (`token_text`, `total_count`, `start_count`, `end_count`, `created_at`) " +
                            "VALUES " + values + " AS incoming " +
                            "ON DUPLICATE KEY UPDATE " +
                            "`total_count` = `Token`.`total_count` + incoming.`total_count`, " +
                            "`start_count` = `Token`.`start_count` + incoming.`start_count`, " +
                            "`end_count` = `Token`.`end_count` + incoming.`end_count`";

            try (PreparedStatement statement = connection.prepareStatement(upsertSql)) {
                int parameter = 1;
                for (Map.Entry<String, Long> entry : batch) {
                    String tokenText = entry.getKey();
                    statement.setString(parameter++, tokenText);
                    statement.setLong(parameter++, entry.getValue());
                    statement.setLong(parameter++, starts.getOrDefault(tokenText, 0L));
                    statement.setLong(parameter++, ends.getOrDefault(tokenText, 0L));
                }
                statement.executeUpdate();
            }

            // Resolve EVERY input spelling with a MySQL JOIN. The comparison
            // uses MySQL equality, just like Token.uq_token_text, so collation-
            // equivalent variants map to the same database token_id.
            StringJoiner inputRows = new StringJoiner(" UNION ALL ");
            for (int i = 0; i < batch.size(); i++) {
                inputRows.add(i == 0 ? "SELECT ? AS original_text" : "SELECT ?");
            }
            String lookupSql =
                    "SELECT src.original_text, t.token_id FROM (" + inputRows + ") AS src " +
                            "JOIN `Token` AS t ON t.token_text = src.original_text";
            try (PreparedStatement statement = connection.prepareStatement(lookupSql)) {
                int parameter = 1;
                for (Map.Entry<String, Long> entry : batch) {
                    statement.setString(parameter++, entry.getKey());
                }
                try (ResultSet results = statement.executeQuery()) {
                    while (results.next()) {
                        tokenIds.put(results.getString(1), results.getLong(2));
                    }
                }
            }
        }

        // Don't write transitions against IDs we couldn't resolve.
        if (tokenIds.size() != counts.size()) {
            throw new SQLException("Unable to resolve every token ID using the database collation (resolved "
                    + tokenIds.size() + " of " + counts.size() + ").");
        }
        return tokenIds;
    }

    private static void upsertTransitions(
            Connection connection, List<String> words, Map<String, Long> tokenIds)
            throws SQLException {

        // Count repeated transitions before touching the database.
        Map<TransitionKey, Long> transitions = new LinkedHashMap<>();
        for (int i = 0; i + 1 < words.size(); i++) {
            Long previousId = tokenIds.get(words.get(i));
            Long nextId = tokenIds.get(words.get(i + 1));
            if (previousId == null || nextId == null) {
                throw new SQLException("A token was not assigned a database ID.");
            }
            transitions.merge(new TransitionKey(previousId, nextId), 1L, Long::sum);
        }

        List<Map.Entry<TransitionKey, Long>> entries =
                new ArrayList<>(transitions.entrySet());
        for (int from = 0; from < entries.size(); from += SQL_BATCH_SIZE) {
            List<Map.Entry<TransitionKey, Long>> batch = entries.subList(
                    from, Math.min(from + SQL_BATCH_SIZE, entries.size()));
            StringJoiner values = new StringJoiner(",");
            for (int i = 0; i < batch.size(); i++) {
                values.add("(?,?,?,?)");
            }
            String sql = "INSERT INTO `Token_Transition` " +
                    "(`previous_token_id`, `next_token_id`, `occurrence_count`, `user_selection_count`) " +
                    "VALUES " + values + " AS incoming " +
                    "ON DUPLICATE KEY UPDATE " +
                    "`occurrence_count` = `Token_Transition`.`occurrence_count` + " +
                    "incoming.`occurrence_count`";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                int parameter = 1;
                for (Map.Entry<TransitionKey, Long> entry : batch) {
                    statement.setLong(parameter++, entry.getKey().previousTokenId());
                    statement.setLong(parameter++, entry.getKey().nextTokenId());
                    statement.setLong(parameter++, entry.getValue());
                    statement.setLong(parameter++, 0L); // New imports do not represent user selections.
                }
                statement.executeUpdate();
            }
        }
        // On duplicate transition keys, update only occurrence_count.
        // Preserve any pre-existing user_selection_count.
    }

    // SQL transition upserts only need IDs, not the Hibernate entity class.
    // This also works after renaming NextToken to TokenTransition.
    private record TransitionKey(long previousTokenId, long nextTokenId) {}

    private static boolean isSentenceTerminator(String word) {
        return ".".equals(word) || "!".equals(word) || "?".equals(word);
    }

    private static String normalizeToken(String word) {
        return Normalizer.normalize(word, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private static final class DuplicateImportException extends RuntimeException {
        private DuplicateImportException(String message) {
            super(message);
        }
    }

    public List<Token> getTokens() {
        return sessionFactory.fromTransaction(session ->
                session.createQuery("from Token", Token.class).getResultList());
    }

    public Token getTokenByText(String token_text){
        return sessionFactory.fromTransaction(sess -> {
            return sess.createQuery("FROM Token t where t.token_text = :text", Token.class).setParameter("text", token_text).setMaxResults(1).getResultStream().findFirst().orElse(null);
        });
    }

    public List<Token> searchTokens(String currentString, int count){
        return sessionFactory.fromTransaction(sess -> {
            return sess
                    .createQuery("FROM Token t where t.token_text LIKE :text", Token.class)
                    .setParameter("text", currentString + "%")
                    .setMaxResults(count)
                    .getResultStream()
                    .toList();
        });
    }

    public Token getNextToken(Token prevToken) {
        return sessionFactory.fromTransaction(sess -> {
            CriteriaBuilder cb = sess.getCriteriaBuilder();
            var query = cb.createQuery(TokenTransition.class);
            var root = query.from(TokenTransition.class);

            query.select(root)
                 .where(cb.equal(root.get("id").get("previousTokenId"), prevToken.getToken_id()))
                 .orderBy(cb.desc(root.get("occurrenceCount")));

            TokenTransition transition = sess.createQuery(query)
                    .setMaxResults(1)
                    .getResultStream()
                    .findFirst()
                    .orElse(null);

            if (transition == null) {
                return null;
            }

            Long nextTokenId = transition.getId().getNextTokenId();
            return sess.find(Token.class, nextTokenId);
        });
    }
}

