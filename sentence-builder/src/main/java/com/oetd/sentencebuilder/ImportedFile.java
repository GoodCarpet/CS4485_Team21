package com.oetd.sentencebuilder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
// Aniq Sunesara
// prompt: make a java ORM mapping file for both the Imported_File and Generated_Sentence tables

/** Maps the existing Imported_File table. */
@Entity
@Table(name = "Imported_File")
public class ImportedFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "file_id")
    private Long fileId;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    // Optional SHA-256 digest; MySQL enforces uniqueness for non-NULL values.
    @Column(name = "file_hash", length = 64, columnDefinition = "CHAR(64)", unique = true)
    private String fileHash;

    @Column(name = "token_count", nullable = false)
    private Long tokenCount = 0L;

    // The database supplies CURRENT_TIMESTAMP on INSERT.
    @Column(name = "imported_at", nullable = false, insertable = false,
            updatable = false, columnDefinition = "TIMESTAMP")
    private LocalDateTime importedAt;

    protected ImportedFile() { }

    public ImportedFile(String fileName, String fileHash, long tokenCount) {
        this.fileName = fileName;
        this.fileHash = fileHash;
        this.tokenCount = tokenCount;
    }

    public Long getFileId() { return fileId; }
    public String getFileName() { return fileName; }
    public String getFileHash() { return fileHash; }
    public Long getTokenCount() { return tokenCount; }
    public LocalDateTime getImportedAt() { return importedAt; }

    public void setFileName(String fileName) { this.fileName = fileName; }
    public void setFileHash(String fileHash) { this.fileHash = fileHash; }
    public void setTokenCount(Long tokenCount) { this.tokenCount = tokenCount; }
}
