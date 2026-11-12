package com.oetd.sentencebuilder.config;

import com.oetd.sentencebuilder.GeneratedSentence;
import com.oetd.sentencebuilder.ImportedFile;
import com.oetd.sentencebuilder.Token;
import com.oetd.sentencebuilder.TokenTransition;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;

import java.io.IOException;

public class DatabaseConfig {

    public static SessionFactory buildSessionFactory() throws IOException {
        String dbUrl = System.getenv("SB_DB_URL");
        String dbUser = System.getenv("SB_DB_USER");
        String dbPassword = System.getenv("SB_DB_PASSWORD");

        if (dbUrl == null || dbUser == null || dbPassword == null) {
            throw new IOException("Missing database environment variables");
        }

        final StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.connection.url", dbUrl)
                .applySetting("hibernate.connection.username", dbUser)
                .applySetting("hibernate.connection.password", dbPassword)
                .build();

        try {
            return new MetadataSources(registry)
                    .addAnnotatedClass(Token.class)
                    .addAnnotatedClass(TokenTransition.class)
                    .addAnnotatedClass(ImportedFile.class)
                    .addAnnotatedClass(GeneratedSentence.class)
                    .buildMetadata()
                    .buildSessionFactory();
        } catch (Exception e) {
            StandardServiceRegistryBuilder.destroy(registry);
            throw new IOException("Failed to configure database", e);
        }
    }
}
