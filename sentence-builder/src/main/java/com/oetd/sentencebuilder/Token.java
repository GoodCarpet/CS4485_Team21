package com.oetd.sentencebuilder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;

import java.time.LocalDateTime;

@Entity
@Table(name = "Token")
public class Token {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // auto incremented
    @Column(name = "token_id")
    private Long token_id;

    @Column(name = "token_text", nullable = false, length = 255) // text
    private String token_text;

    @Column(name = "total_count", nullable = false) // number of occurrences
    private Long total_count;

    @Column(name = "start_count", nullable = false) // number of times word starts a sentence
    private Long start_count;

    @Column(name = "end_count", nullable = false) // number of times word ends a sentence
    private Long end_count;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false, columnDefinition = "TIMESTAMP")
    private LocalDateTime created_at; // time of the first time added

    protected Token() {}

    public Token(String text, Long total, Long start, Long end) {
        this.token_text = text;
        this.total_count = total;
        this.start_count = start;
        this.end_count = end;
    }

    public Long getToken_id() { return token_id; }
    public String getToken_text() { return token_text; }
    public Long getTotal_count() { return total_count; }
    public Long getStart_count() { return start_count; }
    public Long getEnd_count() { return end_count; }
    public LocalDateTime getCreated_at() { return created_at; }

    public void setToken_id(Long value) { token_id = value; }
    public void setToken_text(String value) { token_text = value; }
    public void setTotal_count(Long value) { total_count = value; }
    public void setStart_count(Long value) { start_count = value; }
    public void setEnd_count(Long value) { end_count = value; }
    public void setCreated_at(LocalDateTime value) { created_at = value; }

    public SimpleLongProperty idProperty() { return new SimpleLongProperty(token_id); }
    public SimpleStringProperty textProperty() { return new SimpleStringProperty(token_text); }
    public SimpleLongProperty totalCountProperty() { return new SimpleLongProperty(total_count); }
    public SimpleLongProperty startCountProperty() { return new SimpleLongProperty(start_count); }
    public SimpleLongProperty endCountProperty() { return new SimpleLongProperty(end_count); }
    public SimpleObjectProperty<LocalDateTime> createdAtProperty() {
        return new SimpleObjectProperty<>(created_at);
    }
}
