package com.oetd.sentencebuilder;

import org.hibernate.SessionFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class SBService {
    private final SessionFactory sessionFactory;

    public SBService(SessionFactory sf) {
        this.sessionFactory = sf;
    }

    // function that takes a file and parses through it and updates DB accordingly
    public void addFile(File file) throws IOException {
        String content = Files.readString(file.toPath());
        String trimmed = content.trim();
        if (trimmed.isEmpty()) return;

        // Preserve the original app's whitespace-based tokenization.
        // lowercase words for normalization purposes
        List<String> words = Arrays.stream(trimmed.split("\\s+"))
                .map(word -> word.toLowerCase(Locale.ROOT))
                .toList();
        if (words.stream().anyMatch(word -> word.length() > 255)) {
            throw new IOException("A token exceeds the database's 255-character limit");
        }

        Map<String, Long> counts = new LinkedHashMap<>();
        for (String word : words) counts.merge(word, 1L, Long::sum);

        sessionFactory.inTransaction(session -> {
            // Read only words present in this file.
            List<Token> existing = session.createQuery(
                            "from Token t where t.token_text in :words", Token.class)
                    .setParameterList("words", counts.keySet())
                    .getResultList();
            Map<String, Token> byText = existing.stream().collect(
                    Collectors.toMap(token -> token.getToken_text().toLowerCase(Locale.ROOT), token -> token));

            for (Map.Entry<String, Long> entry : counts.entrySet()) {
                String word = entry.getKey();
                long increment = entry.getValue();
                long starts = word.equals(words.getFirst()) ? 1 : 0;
                long ends = word.equals(words.getLast()) ? 1 : 0;

                Token token = byText.get(word);
                if (token == null) {
                    token = new Token(word, increment, starts, ends);
                    session.persist(token);
                    byText.put(word, token);
                } else {
                    token.setTotal_count(token.getTotal_count() + increment);
                    token.setStart_count(token.getStart_count() + starts);
                    token.setEnd_count(token.getEnd_count() + ends);
                }
            }

            // IDENTITY-generated token IDs become available after insertion.
            session.flush();

            Map<TokenTransition.Key, Long> transitions = new HashMap<>();
            for (int i = 0; i < words.size() - 1; i++) {
                Long previousId = byText.get(words.get(i)).getToken_id();
                Long nextId = byText.get(words.get(i + 1)).getToken_id();
                transitions.merge(new TokenTransition.Key(previousId, nextId), 1L, Long::sum);
            }

            // Each key is the composite primary key defined by Token_Transition.
            for (Map.Entry<TokenTransition.Key, Long> entry : transitions.entrySet()) {
                TokenTransition.Key key = entry.getKey();
                TokenTransition transition = session.find(TokenTransition.class, key);
                if (transition == null) {
                    session.persist(new TokenTransition(
                            key.getPreviousTokenId(), key.getNextTokenId(), entry.getValue()));
                } else {
                    transition.setOccurrenceCount(
                            transition.getOccurrenceCount() + entry.getValue());
                }
            }
        });
    }

    public List<Token> getTokens() {
        return sessionFactory.fromTransaction(session ->
                session.createQuery("from Token", Token.class).getResultList());
    }
}
