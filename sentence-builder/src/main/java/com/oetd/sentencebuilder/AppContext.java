package com.oetd.sentencebuilder;

import com.oetd.sentencebuilder.config.DatabaseConfig;
import org.hibernate.SessionFactory;

import java.io.IOException;

public class AppContext {
    private final SessionFactory sessionFactory;
    private final SBService sbService;
    private final MarkovGenerator markovGenerator;

    public AppContext() throws IOException {
        this.sessionFactory = DatabaseConfig.buildSessionFactory();
        this.sbService = new SBService(sessionFactory);
        this.markovGenerator = new MarkovGenerator(sessionFactory);
        this.markovGenerator.loadTransitionsFromDB();
    }

    public SessionFactory getSessionFactory() {
        return sessionFactory;
    }

    public SBService getSbService() {
        return sbService;
    }

    public MarkovGenerator getMarkovGenerator() {
        return markovGenerator;
    }
}
