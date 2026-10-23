package com.oetd.sentencebuilder;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "next_token")
public class NextToken {
    @Id
    @GeneratedValue
    private Long next_token_id;

    private String token_text;
    private String next_token_text;

    private Long count;

    public record Key(String token_text, String next_token_text) {}

    public NextToken(String token_text, String next_token_text, Long count) {
        this.token_text = token_text;
        this.next_token_text = next_token_text;
        this.count = count;
    }

    protected NextToken() {}

    public Long getNext_token_id() {
        return next_token_id;
    }

    public String getToken_text() {
        return token_text;
    }

    public String getNext_token_text() {
        return next_token_text;
    }

    public Long getCount() {
        return count;
    }

    public void setNext_token_id(Long next_token_id) {
        this.next_token_id = next_token_id;
    }

    public void setToken_text(String token_text) {
        this.token_text = token_text;
    }

    public void setNext_token_text(String next_token_text) {
        this.next_token_text = next_token_text;
    }

    public void setCount(Long count) {
        this.count = count;
    }
}
