package com.puffmc.changelog.util;

import com.puffmc.changelog.ChangelogPlugin;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Auto-detects changelog entry category based on content keywords
 */
public class CategoryDetector {
    private final ChangelogPlugin plugin;
    private final Map<String, List<String>> categoryKeywords;

    public CategoryDetector(ChangelogPlugin plugin) {
        this.plugin = plugin;
        this.categoryKeywords = new HashMap<>();
        loadKeywords();
    }

    /**
     * Loads category keywords from config
     */
    private void loadKeywords() {
        categoryKeywords.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("category-detection.keywords");
        
        if (section != null) {
            for (String category : section.getKeys(false)) {
                List<String> keywords = section.getStringList(category);
                categoryKeywords.put(category.toLowerCase(), keywords);
            }
        }
    }

    /**
     * Reloads keywords from config
     */
    public void reload() {
        loadKeywords();
    }

    /**
     * Detects category from content
     * @param content The entry content
     * @return Detected category or null if none found
     */
    public String detectCategory(String content) {
        if (!plugin.getConfig().getBoolean("category-detection.enabled", false)) {
            return null;
        }

        if (content == null || content.isEmpty()) {
            return null;
        }

        String lowerContent = content.toLowerCase();
        int maxMatches = 0;
        String bestCategory = null;

        for (Map.Entry<String, List<String>> entry : categoryKeywords.entrySet()) {
            String category = entry.getKey();
            List<String> keywords = entry.getValue();
            int matches = 0;

            for (String keyword : keywords) {
                if (lowerContent.contains(keyword.toLowerCase())) {
                    matches++;
                }
            }

            if (matches > maxMatches) {
                maxMatches = matches;
                bestCategory = category;
            }
        }

        if (maxMatches >= plugin.getConfig().getInt("category-detection.min-matches", 1)) {
            plugin.debug("Auto-detected category: " + bestCategory + " (matches: " + maxMatches + ")");
            return bestCategory;
        }

        return null;
    }

    /**
     * Checks if auto-detection is enabled
     * @return true if enabled
     */
    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("category-detection.enabled", false);
    }
}
