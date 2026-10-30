package com.oetd.sentencebuilder;

import org.hibernate.SessionFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SBService {
    private static final int QUERY_BATCH_SIZE = 500;

    // One word/number token OR one punctuation/symbol character.
    // Examples: "Hello, world!" -> ["hello", ",", "world", "!"]
    //           "don't"         -> ["don", "'", "t"]
    private static final Pattern TOKEN_PATTERN = Pattern.compile(
            "[\\p{L}\\p{M}\\p{N}]+|[^\\p{L}\\p{M}\\p{N}\\s]"
    );

    // Punctuation is stored as a Token, but only words/numbers receive
    // sentence-start or sentence-end counts.
    private static final Pattern WORD_TOKEN_PATTERN = Pattern.compile(
            "[\\p{L}\\p{M}\\p{N}]+"
    );

    private final SessionFactory sessionFactory;

    public SBService(SessionFactory sf) {
        this.sessionFactory = sf;
    }
    // Aniq Sunesara
    // in sbservice, create an updated addfile function that will split the words, normalize them, and separate punctuations into their own tokens. Then update the Token table, Token_Transition table, and the Imported_File Table accordingly.
    public void addFile(File file) throws IOException {
        if (file == null || !file.isFile()) {
            throw new IOException("Please select an existing text file.");
        }
        if (file.getName().length() > 255) {
            throw new IOException("File name exceeds Imported_File.file_name (255 characters).");
        }

        // Read once, so the text and the SHA-256 fingerprint use the same bytes.
        byte[] bytes = Files.readAllBytes(file.toPath());
        String content = new String(bytes, StandardCharsets.UTF_8);
        String fileHash = sha256Hex(bytes);

        List<String> words = new ArrayList<>();
        Matcher matcher = TOKEN_PATTERN.matcher(content);
        while (matcher.find()) {
            String token = normalizeToken(matcher.group());
            if (token.length() > 255) {
                throw new IOException("A token exceeds Token.token_text (255 characters): " + token);
            }
            words.add(token);
        }

        // Count words AND punctuation; repeated text is aggregated by normalized token.
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String word : words) {
            counts.merge(word, 1L, Long::sum);
        }

        // A sentence starts at its first word/number and ends at its last
        // word/number before '.', '!', or '?'. A final sentence without ending
        // punctuation is also counted. Punctuation remains in Token and
        // Token_Transition but does not receive start_count or end_count.
        Map<String, Long> sentenceStarts = new HashMap<>();
        Map<String, Long> sentenceEnds = new HashMap<>();
        String lastWordInSentence = null;
        boolean waitingForSentenceStart = true;

        for (String token : words) {
            if (WORD_TOKEN_PATTERN.matcher(token).matches()) {
                if (waitingForSentenceStart) {
                    sentenceStarts.merge(token, 1L, Long::sum);
                    waitingForSentenceStart = false;
                }
                lastWordInSentence = token;
            } else if (isSentenceTerminator(token) && !waitingForSentenceStart) {
                sentenceEnds.merge(lastWordInSentence, 1L, Long::sum);
                lastWordInSentence = null;
                waitingForSentenceStart = true;
            }
        }
        if (!waitingForSentenceStart) {
            // Count a last sentence even if the file ends without . ! or ?
            sentenceEnds.merge(lastWordInSentence, 1L, Long::sum);
        }

        try {
            // All three tables change together, or none of them change.
            sessionFactory.inTransaction(session -> {
                // File hashes are unique in Imported_File. Do not double-count a reimport.
                boolean alreadyImported = !session.createQuery(
                                "select f.fileId from ImportedFile f where f.fileHash = :hash",
                                Long.class)
                        .setParameter("hash", fileHash)
                        .setMaxResults(1)
                        .getResultList()
                        .isEmpty();
                if (alreadyImported) {
                    throw new DuplicateImportException(
                            "This file's contents have already been imported: " + file.getName());
                }

                // Load existing token rows. Batch to avoid enormous SQL IN clauses.
                Map<String, Token> byText = new HashMap<>();
                List<String> uniqueWords = new ArrayList<>(counts.keySet());
                for (int start = 0; start < uniqueWords.size(); start += QUERY_BATCH_SIZE) {
                    List<String> batch = uniqueWords.subList(
                            start, Math.min(start + QUERY_BATCH_SIZE, uniqueWords.size()));
                    List<Token> existing = session.createQuery(
                                    "from Token t where t.token_text in :texts", Token.class)
                            .setParameterList("texts", batch)
                            .getResultList();
                    for (Token token : existing) {
                        byText.put(normalizeToken(token.getToken_text()), token);
                    }
                }

                // Update Token: occurrence frequencies and first/last word per SENTENCE.
                for (Map.Entry<String, Long> entry : counts.entrySet()) {
                    String word = entry.getKey();
                    long increment = entry.getValue();
                    long startIncrement = sentenceStarts.getOrDefault(word, 0L);
                    long endIncrement = sentenceEnds.getOrDefault(word, 0L);

                    Token token = byText.get(word);
                    if (token == null) {
                        token = new Token(word, increment, startIncrement, endIncrement);
                        session.persist(token);
                        byText.put(word, token);
                    } else {
                        token.setTotal_count(token.getTotal_count() + increment);
                        token.setStart_count(token.getStart_count() + startIncrement);
                        token.setEnd_count(token.getEnd_count() + endIncrement);
                    }
                }

                // Token uses MySQL AUTO_INCREMENT; flush before referencing token IDs.
                session.flush();

                // Consecutive tokens, including punctuation, become bigram transitions.
                Map<TokenTransition.Key, Long> transitionCounts = new HashMap<>();
                for (int i = 0; i + 1 < words.size(); i++) {
                    long previousId = byText.get(words.get(i)).getToken_id();
                    long nextId = byText.get(words.get(i + 1)).getToken_id();
                    TokenTransition.Key key = new TokenTransition.Key(previousId, nextId);
                    transitionCounts.merge(key, 1L, Long::sum);
                }

                // Update Token_Transition using its composite primary key.
                for (Map.Entry<TokenTransition.Key, Long> entry : transitionCounts.entrySet()) {
                    TokenTransition.Key key = entry.getKey();
                    TokenTransition transition = session.find(TokenTransition.class, key);
                    if (transition == null) {
                        session.persist(new TokenTransition(
                                key.getPreviousTokenId(),
                                key.getNextTokenId(),
                                entry.getValue()));
                    } else {
                        transition.setOccurrenceCount(
                                transition.getOccurrenceCount() + entry.getValue());
                        // Preserve existing user_selection_count.
                    }
                }

                // Log the completed import in Imported_File in the SAME transaction.
                session.persist(new ImportedFile(
                        file.getName(), fileHash, (long) words.size()));
            });
        } catch (DuplicateImportException ex) {
            throw new IOException(ex.getMessage(), ex);
        } catch (RuntimeException ex) {
            throw new IOException("Database import failed for " + file.getName(), ex);
        }
    }

    private static boolean isSentenceTerminator(String token) {
        return ".".equals(token) || "!".equals(token) || "?".equals(token);
    }

    private static String normalizeToken(String token) {
        return Normalizer.normalize(token, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private static final class DuplicateImportException extends RuntimeException {
        DuplicateImportException(String message) {
            super(message);
        }
    }

    public List<Token> getTokens() {
        return sessionFactory.fromTransaction(session ->
                session.createQuery("from Token", Token.class).getResultList());
    }
}
