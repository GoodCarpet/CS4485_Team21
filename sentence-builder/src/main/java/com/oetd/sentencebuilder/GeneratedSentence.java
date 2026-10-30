package com.oetd.sentencebuilder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
// Aniq Sunesara
// prompt: make a java ORM mapping file for both the Imported_File and Generated_Sentence tables
/** Maps the existing Generated_Sentence table. */
@Entity
@Table(name = "Generated_Sentence")
public class GeneratedSentence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sentence_id")
    private Long sentenceId;

    @Column(name = "sentence_text", nullable = false, columnDefinition = "TEXT")
    private String sentenceText;

    // The application calculates the SHA-256 digest before saving.
    @Column(name = "sentence_hash", nullable = false, length = 64,
            columnDefinition = "CHAR(64)")
    private String sentenceHash;

    @Column(name = "generation_method", nullable = false, length = 40)
    private String generationMethod;

    // Nullable FK: Generated_Sentence.starting_token_id -> Token.token_id.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "starting_token_id", referencedColumnName = "token_id")
    private Token startingToken;

    @Column(name = "added_to_corpus", nullable = false)
    private Boolean addedToCorpus = false;

    // The database supplies CURRENT_TIMESTAMP on INSERT.
    @Column(name = "generated_at", nullable = false, insertable = false,
            updatable = false, columnDefinition = "TIMESTAMP")
    private LocalDateTime generatedAt;

    protected GeneratedSentence() { }

    public GeneratedSentence(String sentenceText, String sentenceHash,
                             String generationMethod, Token startingToken) {
        this.sentenceText = sentenceText;
        this.sentenceHash = sentenceHash;
        this.generationMethod = generationMethod;
        this.startingToken = startingToken;
    }

    public Long getSentenceId() { return sentenceId; }
    public String getSentenceText() { return sentenceText; }
    public String getSentenceHash() { return sentenceHash; }
    public String getGenerationMethod() { return generationMethod; }
    public Token getStartingToken() { return startingToken; }
    public Boolean getAddedToCorpus() { return addedToCorpus; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }

    public void setSentenceText(String sentenceText) { this.sentenceText = sentenceText; }
    public void setSentenceHash(String sentenceHash) { this.sentenceHash = sentenceHash; }
    public void setGenerationMethod(String generationMethod) { this.generationMethod = generationMethod; }
    public void setStartingToken(Token startingToken) { this.startingToken = startingToken; }
    public void setAddedToCorpus(Boolean addedToCorpus) { this.addedToCorpus = addedToCorpus; }
}
