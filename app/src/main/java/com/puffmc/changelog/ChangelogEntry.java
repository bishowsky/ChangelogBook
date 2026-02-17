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
    private String category;  // Optional category (fix/added/removed/etc.)

    // Constructor for existing entries
    public ChangelogEntry(String id, String content, String author, long timestamp) {
        this.id = id;
        this.content = content;
        this.author = author;
        this.timestamp = timestamp;
        this.deleted = false;
        this.createdAt = timestamp;
        this.modifiedAt = timestamp;
        this.category = null;
    }

    // Constructor for existing entries with category
    public ChangelogEntry(String id, String content, String author, long timestamp, String category) {
        this.id = id;
        this.content = content;
        this.author = author;
        this.timestamp = timestamp;
        this.deleted = false;
        this.createdAt = timestamp;
        this.modifiedAt = timestamp;
        this.category = category;
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
        this.category = null;
    }

    // Constructor for new entries with category
    public ChangelogEntry(String content, String author, long timestamp, String category) {
        this.id = UUID.randomUUID().toString();
        this.content = content;
        this.author = author;
        this.timestamp = timestamp;
        this.deleted = false;
        this.createdAt = timestamp;
        this.modifiedAt = timestamp;
        this.category = category;
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

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    @Override
    public String toString() {
        return "ChangelogEntry{" +
                "id='" + id + '\'' +
                ", author='" + author + '\'' +
                ", timestamp=" + timestamp +
                ", deleted=" + deleted +
                ", category='" + category + '\'' +
                '}';
    }
}
