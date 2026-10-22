package com.oetd.sentencebuilder;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "token")
public class Token {
    @Id
    @GeneratedValue
    private Long token_id;

    private String token_text;
    private Long total_count;
    private Long start_count;
    private Long end_count;

    private LocalDateTime created_at;

    public Token(String text, Long tcount, Long scount, Long ecount){
        token_text = text;
        total_count = tcount;
        start_count = scount;
        end_count = ecount;
        created_at = LocalDateTime.now();
    }
    protected Token(){}

    public SimpleLongProperty idProperty(){ return new SimpleLongProperty(token_id); }
    public SimpleStringProperty textProperty(){ return new SimpleStringProperty(token_text); }
    public SimpleLongProperty totalCountProperty(){ return new SimpleLongProperty(total_count); }
    public SimpleLongProperty startCountProperty(){ return new SimpleLongProperty(start_count); }
    public SimpleLongProperty endCountProperty(){ return new SimpleLongProperty(end_count); }
    public SimpleObjectProperty<LocalDateTime> createdAtProperty(){ return new SimpleObjectProperty<>(created_at);}
}
