package com.puffmc.changelog.api;

import com.puffmc.changelog.ChangelogEntry;
import com.puffmc.changelog.ChangelogPlugin;
import com.puffmc.changelog.manager.ChangelogManager;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Public API for developers to interact with ChangelogBook
 * 
 * Usage:
 * ChangelogAPI api = ChangelogAPI.getInstance();
 * List<ChangelogEntry> entries = api.getEntries();
 */
public class ChangelogAPI {
    private static ChangelogAPI instance;
    private final ChangelogPlugin plugin;
    private final ChangelogManager changelogManager;

    private ChangelogAPI(ChangelogPlugin plugin, ChangelogManager changelogManager) {
        this.plugin = plugin;
        this.changelogManager = changelogManager;
    }

    /**
     * Initializes the API
     * @param plugin Plugin instance
     * @param changelogManager Changelog manager
     */
    public static void initialize(ChangelogPlugin plugin, ChangelogManager changelogManager) {
        if (instance == null) {
            instance = new ChangelogAPI(plugin, changelogManager);
        }
    }

    /**
     * Gets the API instance
     * @return API instance or null if not initialized
     */
    public static ChangelogAPI getInstance() {
        return instance;
    }

    /**
     * Gets all changelog entries
     * @return List of entries
     */
    public List<ChangelogEntry> getEntries() {
        return changelogManager.getEntries();
    }

    /**
     * Gets an entry by ID
     * @param id Entry ID
     * @return Entry or null
     */
    public ChangelogEntry getEntry(String id) {
        return changelogManager.getEntries().stream()
                .filter(e -> e.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    /**
     * Gets entries by category
     * @param category Category name
     * @return List of entries
     */
    public List<ChangelogEntry> getEntriesByCategory(String category) {
        return changelogManager.getEntries().stream()
                .filter(e -> category.equalsIgnoreCase(e.getCategory()))
                .collect(Collectors.toList());
    }

    /**
     * Gets entries by author
     * @param author Author name
     * @return List of entries
     */
    public List<ChangelogEntry> getEntriesByAuthor(String author) {
        return changelogManager.getEntries().stream()
                .filter(e -> author.equalsIgnoreCase(e.getAuthor()))
                .collect(Collectors.toList());
    }

    /**
     * Gets recent entries
     * @param limit Maximum number of entries
     * @return List of recent entries
     */
    public List<ChangelogEntry> getRecentEntries(int limit) {
        return changelogManager.getEntries().stream()
                .sorted((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Gets entries within a time range
     * @param startTime Start timestamp
     * @param endTime End timestamp
     * @return List of entries
     */
    public List<ChangelogEntry> getEntriesInRange(long startTime, long endTime) {
        return changelogManager.getEntries().stream()
                .filter(e -> e.getTimestamp() >= startTime && e.getTimestamp() <= endTime)
                .collect(Collectors.toList());
    }

    /**
     * Adds a new changelog entry
     * @param content Entry content
     * @param author Author name
     * @param category Category
     * @return Entry ID
     */
    public String addEntry(String content, String author, String category) {
        ChangelogEntry entry = changelogManager.addEntry(content, author, category);
        return entry != null ? entry.getId() : null;
    }

    /**
     * Adds a new changelog entry with custom ID
     * @param customId Custom ID
     * @param content Entry content
     * @param author Author name
     * @param category Category
     * @return Entry ID or null if failed
     */
    public String addEntryWithCustomId(String customId, String content, String author, String category) {
        if (!changelogManager.isValidCustomId(customId)) {
            return null;
        }
        
        if (changelogManager.entryExists(customId)) {
            return null;
        }

        ChangelogEntry entry = changelogManager.addEntry(customId, content, author, category);
        return entry != null ? entry.getId() : null;
    }

    /**
     * Checks if an entry exists
     * @param id Entry ID
     * @return true if exists
     */
    public boolean entryExists(String id) {
        return changelogManager.entryExists(id);
    }

    /**
     * Gets total entry count
     * @return Number of entries
     */
    public int getTotalEntries() {
        return changelogManager.getEntries().size();
    }

    /**
     * Gets the plugin version
     * @return Plugin version
     */
    public String getPluginVersion() {
        return plugin.getDescription().getVersion();
    }
}
