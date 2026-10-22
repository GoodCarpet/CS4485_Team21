package com.oetd.sentencebuilder;

import org.hibernate.SessionFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

public class SBService {
    private final SessionFactory sessionFactory;

    public SBService(SessionFactory sf){
        sessionFactory = sf;
    }

    public void addFile(File file) throws IOException  {
        String content = Files.readString(file.toPath());
        String[] toks = content.split("\\s+");

        for(String tok : toks) {

           Token token = new Token(tok, (long) 0, (long) 0, (long) 0);

           sessionFactory.inTransaction(sess -> {
               sess.persist(token);
           });
        }

        return;
    }

    public List<Token> getTokens() {
        return sessionFactory.fromTransaction(sess -> {
            return sess.createQuery("FROM Token", Token.class).getResultList();
        });
    }
}