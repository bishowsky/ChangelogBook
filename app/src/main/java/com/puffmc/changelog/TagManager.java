package com.puffmc.changelog;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Manages tags for changelog entries
 */
public class TagManager {
    private final ChangelogPlugin plugin;
    private final File tagsFile;
    private FileConfiguration tagsConfig;
    private final Map<String, Set<String>> entryTags;

    public TagManager(ChangelogPlugin plugin) {
        this.plugin = plugin;
        this.tagsFile = new File(plugin.getDataFolder(), "tags.yml");
        this.entryTags = new HashMap<>();
        loadTags();
    }

    /**
     * Loads tags from file
     */
    private void loadTags() {
        if (!tagsFile.exists()) {
            try {
                tagsFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to create tags file: " + e.getMessage());
                return;
            }
        }

        tagsConfig = YamlConfiguration.loadConfiguration(tagsFile);
        entryTags.clear();

        for (String entryId : tagsConfig.getKeys(false)) {
            List<String> tags = tagsConfig.getStringList(entryId);
            entryTags.put(entryId, new HashSet<>(tags));
        }
    }

    /**
     * Saves tags to file
     */
    private void saveTags() {
        for (Map.Entry<String, Set<String>> entry : entryTags.entrySet()) {
            tagsConfig.set(entry.getKey(), new ArrayList<>(entry.getValue()));
        }

        try {
            tagsConfig.save(tagsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save tags: " + e.getMessage());
        }
    }

    /**
     * Adds a tag to an entry
     * @param entryId Entry ID
     * @param tag Tag to add
     */
    public void addTag(String entryId, String tag) {
        entryTags.computeIfAbsent(entryId, k -> new HashSet<>()).add(tag.toLowerCase());
        saveTags();
        plugin.debug("Added tag '" + tag + "' to entry " + entryId);
    }

    /**
     * Removes a tag from an entry
     * @param entryId Entry ID
     * @param tag Tag to remove
     * @return true if removed
     */
    public boolean removeTag(String entryId, String tag) {
        Set<String> tags = entryTags.get(entryId);
        if (tags != null && tags.remove(tag.toLowerCase())) {
            saveTags();
            plugin.debug("Removed tag '" + tag + "' from entry " + entryId);
            return true;
        }
        return false;
    }

    /**
     * Gets all tags for an entry
     * @param entryId Entry ID
     * @return Set of tags
     */
    public Set<String> getTags(String entryId) {
        return entryTags.getOrDefault(entryId, new HashSet<>());
    }

    /**
     * Checks if an entry has a specific tag
     * @param entryId Entry ID
     * @param tag Tag to check
     * @return true if entry has the tag
     */
    public boolean hasTag(String entryId, String tag) {
        Set<String> tags = entryTags.get(entryId);
        return tags != null && tags.contains(tag.toLowerCase());
    }

    /**
     * Gets all entries with a specific tag
     * @param tag Tag to search for
     * @return List of entry IDs
     */
    public List<String> getEntriesWithTag(String tag) {
        List<String> result = new ArrayList<>();
        String lowerTag = tag.toLowerCase();

        for (Map.Entry<String, Set<String>> entry : entryTags.entrySet()) {
            if (entry.getValue().contains(lowerTag)) {
                result.add(entry.getKey());
            }
        }

        return result;
    }

    /**
     * Gets all unique tags
     * @return Set of all tags
     */
    public Set<String> getAllTags() {
        Set<String> allTags = new HashSet<>();
        for (Set<String> tags : entryTags.values()) {
            allTags.addAll(tags);
        }
        return allTags;
    }

    /**
     * Gets tag usage count
     * @return Map of tag to count
     */
    public Map<String, Integer> getTagUsageCount() {
        Map<String, Integer> counts = new HashMap<>();
        
        for (Set<String> tags : entryTags.values()) {
            for (String tag : tags) {
                counts.put(tag, counts.getOrDefault(tag, 0) + 1);
            }
        }

        return counts;
    }

    /**
     * Removes all tags for an entry
     * @param entryId Entry ID
     */
    public void clearTags(String entryId) {
        if (entryTags.remove(entryId) != null) {
            tagsConfig.set(entryId, null);
            saveTags();
            plugin.debug("Cleared all tags for entry " + entryId);
        }
    }
}
