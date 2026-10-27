package com.oetd.sentencebuilder;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "Token_Transition")
public class TokenTransition {
    @Embeddable
    public static class Key implements Serializable {
        @Column(name = "previous_token_id", nullable = false) // token 1
        private Long previousTokenId;

        @Column(name = "next_token_id", nullable = false) // token 2
        private Long nextTokenId;

        protected Key() {}

        public Key(Long previousTokenId, Long nextTokenId) { // key is token 1 then token 2 IDs
            this.previousTokenId = previousTokenId;
            this.nextTokenId = nextTokenId;
        }

        public Long getPreviousTokenId() { return previousTokenId; }
        public Long getNextTokenId() { return nextTokenId; }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof Key other)) return false;
            return Objects.equals(previousTokenId, other.previousTokenId)
                    && Objects.equals(nextTokenId, other.nextTokenId);
        }

        @Override
        public int hashCode() { return Objects.hash(previousTokenId, nextTokenId); }
    }

    @EmbeddedId
    private Key id;

    @Column(name = "occurrence_count", nullable = false)
    private Long occurrenceCount;

    @Column(name = "user_selection_count", nullable = false)
    private Long userSelectionCount;

    protected TokenTransition() {}

    public TokenTransition(Long previousTokenId, Long nextTokenId, long occurrenceCount) {
        this.id = new Key(previousTokenId, nextTokenId);
        this.occurrenceCount = occurrenceCount;
        this.userSelectionCount = 0L;
    }

    public Key getId() { return id; }
    public Long getOccurrenceCount() { return occurrenceCount; }
    public Long getUserSelectionCount() { return userSelectionCount; }
    public void setOccurrenceCount(Long count) { this.occurrenceCount = count; }
    public void setUserSelectionCount(Long count) { this.userSelectionCount = count; }
}
