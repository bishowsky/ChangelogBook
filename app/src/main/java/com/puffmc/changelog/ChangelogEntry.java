package com.puffmc.changelog;

import java.util.UUID;

public class ChangelogEntry {
    private final String id;
    private String content;
    private final String author;
    private final long timestamp;
    private boolean deleted;
    private long createdAt;
    private long modifiedAt;

    // Constructor for existing entries
    public ChangelogEntry(String id, String content, String author, long timestamp) {
        this.id = id;
        this.content = content;
        this.author = author;
        this.timestamp = timestamp;
        this.deleted = false;
        this.createdAt = timestamp;
        this.modifiedAt = timestamp;
    }

    // Constructor for new entries (generates UUID)
    public ChangelogEntry(String content, String author, long timestamp) {
        this.id = UUID.randomUUID().toString();
        this.content = content;
        this.author = author;
        this.timestamp = timestamp;
        this.deleted = false;
        this.createdAt = timestamp;
        this.modifiedAt = timestamp;
    }

    public String getId() {
        return id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
        this.modifiedAt = System.currentTimeMillis();
    }

    public String getAuthor() {
        return author;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getModifiedAt() {
        return modifiedAt;
    }

    public void setModifiedAt(long modifiedAt) {
        this.modifiedAt = modifiedAt;
    }

    @Override
    public String toString() {
        return "ChangelogEntry{" +
                "id='" + id + '\'' +
                ", author='" + author + '\'' +
                ", timestamp=" + timestamp +
                ", deleted=" + deleted +
                '}';
    }
}
