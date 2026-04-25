package com.puffmc.changelog;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Manages player suggestions for changelog entries
 */
public class SuggestionManager {
    private final ChangelogPlugin plugin;
    private final File suggestionsFile;
    private FileConfiguration suggestions;
    private final Map<String, Suggestion> activeSuggestions;
    private int nextSuggestionId = 1; // Counter for suggestion IDs

    public SuggestionManager(ChangelogPlugin plugin) {
        this.plugin = plugin;
        this.suggestionsFile = new File(plugin.getDataFolder(), "suggestions.yml");
        this.activeSuggestions = new LinkedHashMap<>();
        loadSuggestions();
    }
    
    /**
     * Gets the next suggestion ID
     * @return Next numeric ID as string
     */
    private synchronized String getNextSuggestionId() {
        // Find highest existing numeric suggestion ID
        int maxId = 0;
        for (String key : activeSuggestions.keySet()) {
            try {
                int id = Integer.parseInt(key);
                if (id > maxId) {
                    maxId = id;
                }
            } catch (NumberFormatException e) {
                // Not a numeric ID, skip
            }
        }
        
        // Set next ID counter
        nextSuggestionId = maxId + 1;
        return String.valueOf(nextSuggestionId++);
    }

    /**
     * Loads suggestions from file
     */
    private void loadSuggestions() {
        if (!suggestionsFile.exists()) {
            try {
                suggestionsFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to create suggestions file: " + e.getMessage());
                return;
            }
        }

        suggestions = YamlConfiguration.loadConfiguration(suggestionsFile);
        activeSuggestions.clear();

        for (String id : suggestions.getKeys(false)) {
            String player = suggestions.getString(id + ".player");
            String content = suggestions.getString(id + ".content");
            long timestamp = suggestions.getLong(id + ".timestamp");
            String status = suggestions.getString(id + ".status", "pending");

            activeSuggestions.put(id, new Suggestion(id, player, content, timestamp, status));
        }
    }

    /**
     * Saves suggestions to file
     */
    private void saveSuggestions() {
        for (Suggestion suggestion : activeSuggestions.values()) {
            suggestions.set(suggestion.getId() + ".player", suggestion.getPlayer());
            suggestions.set(suggestion.getId() + ".content", suggestion.getContent());
            suggestions.set(suggestion.getId() + ".timestamp", suggestion.getTimestamp());
            suggestions.set(suggestion.getId() + ".status", suggestion.getStatus());
        }

        try {
            suggestions.save(suggestionsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save suggestions: " + e.getMessage());
        }
    }

    /**
     * Adds a new suggestion
     * @param player Player name
     * @param content Suggestion content
     * @return Suggestion ID
     */
    public String addSuggestion(String player, String content) {
        String id = getNextSuggestionId();
        Suggestion suggestion = new Suggestion(id, player, content, System.currentTimeMillis(), "pending");
        
        activeSuggestions.put(id, suggestion);
        saveSuggestions();
        
        plugin.debug("New suggestion added: " + id + " by " + player);
        return id;
    }

    /**
     * Gets all suggestions with a specific status
     * @param status Status filter (pending, approved, rejected)
     * @return List of suggestions
     */
    public List<Suggestion> getSuggestionsByStatus(String status) {
        List<Suggestion> result = new ArrayList<>();
        for (Suggestion suggestion : activeSuggestions.values()) {
            if (suggestion.getStatus().equalsIgnoreCase(status)) {
                result.add(suggestion);
            }
        }
        return result;
    }

    /**
     * Gets all suggestions
     * @return List of all suggestions
     */
    public List<Suggestion> getAllSuggestions() {
        return new ArrayList<>(activeSuggestions.values());
    }

    /**
     * Approves a suggestion
     * @param id Suggestion ID
     * @return true if approved
     */
    public boolean approveSuggestion(String id) {
        Suggestion suggestion = activeSuggestions.get(id);
        if (suggestion != null) {
            suggestion.setStatus("approved");
            saveSuggestions();
            return true;
        }
        return false;
    }

    /**
     * Rejects a suggestion
     * @param id Suggestion ID
     * @return true if rejected
     */
    public boolean rejectSuggestion(String id) {
        Suggestion suggestion = activeSuggestions.get(id);
        if (suggestion != null) {
            suggestion.setStatus("rejected");
            saveSuggestions();
            return true;
        }
        return false;
    }

    /**
     * Deletes a suggestion
     * @param id Suggestion ID
     * @return true if deleted
     */
    public boolean deleteSuggestion(String id) {
        if (activeSuggestions.remove(id) != null) {
            suggestions.set(id, null);
            saveSuggestions();
            return true;
        }
        return false;
    }

    /**
     * Gets a suggestion by ID
     * @param id Suggestion ID
     * @return Suggestion or null
     */
    public Suggestion getSuggestion(String id) {
        return activeSuggestions.get(id);
    }

    /**
     * Represents a player suggestion
     */
    public static class Suggestion {
        private final String id;
        private final String player;
        private final String content;
        private final long timestamp;
        private String status;

        public Suggestion(String id, String player, String content, long timestamp, String status) {
            this.id = id;
            this.player = player;
            this.content = content;
            this.timestamp = timestamp;
            this.status = status;
        }

        public String getId() {
            return id;
        }

        public String getPlayer() {
            return player;
        }

        public String getContent() {
            return content;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }
}
