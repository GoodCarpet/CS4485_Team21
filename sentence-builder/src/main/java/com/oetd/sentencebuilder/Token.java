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

    public Long getToken_id() {
        return token_id;
    }

    public String getToken_text() {
        return token_text;
    }

    public Long getTotal_count() {
        return total_count;
    }

    public Long getStart_count() {
        return start_count;
    }

    public Long getEnd_count() {
        return end_count;
    }

    public LocalDateTime getCreated_at() {
        return created_at;
    }

    public void setToken_id(Long token_id) {
        this.token_id = token_id;
    }

    public void setToken_text(String token_text) {
        this.token_text = token_text;
    }

    public void setTotal_count(Long total_count) {
        this.total_count = total_count;
    }

    public void setStart_count(Long start_count) {
        this.start_count = start_count;
    }

    public void setEnd_count(Long end_count) {
        this.end_count = end_count;
    }

    public void setCreated_at(LocalDateTime created_at) {
        this.created_at = created_at;
    }

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
