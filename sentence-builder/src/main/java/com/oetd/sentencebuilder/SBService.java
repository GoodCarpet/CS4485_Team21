package com.oetd.sentencebuilder;

import org.hibernate.SessionFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SBService {
    private final SessionFactory sessionFactory;

    public SBService(SessionFactory sf){
        sessionFactory = sf;
    }

    public void addFile(File file) throws IOException  {

        String content = Files.readString(file.toPath());
        String[] toks = content.split("\\s+");

        sessionFactory.inTransaction(sess -> {
            List<Token> tokens = sess
                    .createQuery("FROM Token t WHERE t.token_text IN :texts", Token.class)
                    .setParameterList("texts", toks)
                    .getResultList();

            Map<String, Token> tokTextCount = tokens
                    .stream()
                    .collect(Collectors.toMap(Token::getToken_text, t -> t));

            for(String tok : toks){
                Token curToken = tokTextCount.get(tok);
                if(curToken != null){
                    curToken.setTotal_count(curToken.getTotal_count() + 1);
                } else {
                    Token newToken = new Token(tok, 1L, 0L, 0L);
                    sess.persist(newToken);
                    tokTextCount.put(tok, newToken);
                }
            }

        });

        return;
    }

    public List<Token> getTokens() {
        return sessionFactory.fromTransaction(sess -> {
            return sess.createQuery("FROM Token", Token.class).getResultList();
        });
    }
}