package com.oetd.sentencebuilder;

import jakarta.persistence.criteria.Predicate;
import org.hibernate.SessionFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class SBService {
    private final SessionFactory sessionFactory;


    public SBService(SessionFactory sf){
        sessionFactory = sf;
    }

    public void addFile(File file) throws IOException  {

        String content = Files.readString(file.toPath());

        // tokenize document
        String[] tokenKeys = content.split("\\s+");

        // if we have document ["hello", "world", "how", "are", "you"], we want all next_token relationships
        // [(hello, world), (world, how), (how, are), (are, you)]
        List<NextToken.Key> nextTokenKeys = IntStream.range(0, tokenKeys.length - 1)
                .mapToObj(i -> new NextToken.Key(tokenKeys[i], tokenKeys[i + 1]))
                .toList();

        sessionFactory.inTransaction(sess -> {

            List<Token> tokens = sess
                    .createQuery("FROM Token t WHERE t.token_text IN :texts", Token.class)
                    .setParameterList("texts", tokenKeys)
                    .getResultList();

            // instantiate new criteria builder
            var critBuilder = sess.getCriteriaBuilder();
            var nextTokenQuery = critBuilder.createQuery(NextToken.class);
            // set the NextToken table as the query root
            var root = nextTokenQuery.from(NextToken.class);

            // we want all of the next tokens who have corresponding keys with the ones we found in the document
            var predicates = nextTokenKeys.stream().map(key -> critBuilder.and(
                    critBuilder.equal(root.get("token_text"), key.token_text()),
                    critBuilder.equal(root.get("next_token_text"), key.next_token_text())
            )).toArray(Predicate[]::new);

            // select all next tokens from table corresponding to next tokens found in document
            nextTokenQuery.select(root).where(critBuilder.or(predicates));
            List<NextToken> nextTokens = sess.createQuery(nextTokenQuery).getResultList();

            Map<String, Token> tokenMap = tokens
                    .stream()
                    .collect(Collectors.toMap(Token::getToken_text, t -> t));

            Map<NextToken.Key, NextToken> nextTokenMap = nextTokens
                    .stream()
                    .collect(Collectors.toMap(nTok -> new NextToken.Key(nTok.getToken_text(), nTok.getNext_token_text()), nTok -> nTok));

            // for all tokens in document, increment the count existing corresponding db token
            // or create a new token with count 1
            for(String tok : tokenKeys){
                Token curToken = tokenMap.get(tok);
                if(curToken != null){
                    curToken.setTotal_count(curToken.getTotal_count() + 1);
                } else {
                    Token newToken = new Token(tok, 1L, 0L, 0L);
                    sess.persist(newToken);
                    tokenMap.put(tok, newToken);
                }
            }

            // same thing here but for next tokens
            for(NextToken.Key nextTokenKey : nextTokenKeys){
                NextToken curNextToken = nextTokenMap.get(nextTokenKey);
                if(curNextToken != null){
                    curNextToken.setCount(curNextToken.getCount() + 1);
                } else {
                    NextToken newNextToken = new NextToken(nextTokenKey.token_text(), nextTokenKey.next_token_text(), 1L);
                    sess.persist(newNextToken);
                    nextTokenMap.put(nextTokenKey, newNextToken);
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