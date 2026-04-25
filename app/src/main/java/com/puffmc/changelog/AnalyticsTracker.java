package com.puffmc.changelog;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Tracks analytics about plugin usage
 */
public class AnalyticsTracker {
    private final ChangelogPlugin plugin;
    private final File analyticsFile;
    private FileConfiguration analytics;
    private final Map<String, Long> commandUsage;
    private final Map<String, Long> categoryUsage;
    private long totalViews;
    private long totalCreations;
    private BukkitRunnable saveTask;

    public AnalyticsTracker(ChangelogPlugin plugin) {
        this.plugin = plugin;
        this.analyticsFile = new File(plugin.getDataFolder(), "analytics.yml");
        this.commandUsage = new HashMap<>();
        this.categoryUsage = new HashMap<>();
        loadAnalytics();
    }

    /**
     * Loads analytics from file
     */
    private void loadAnalytics() {
        if (!analyticsFile.exists()) {
            try {
                analyticsFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to create analytics file: " + e.getMessage());
                return;
            }
        }

        analytics = YamlConfiguration.loadConfiguration(analyticsFile);

        totalViews = analytics.getLong("totals.views", 0);
        totalCreations = analytics.getLong("totals.creations", 0);

        if (analytics.contains("commands")) {
            for (String cmd : analytics.getConfigurationSection("commands").getKeys(false)) {
                commandUsage.put(cmd, analytics.getLong("commands." + cmd));
            }
        }

        if (analytics.contains("categories")) {
            for (String cat : analytics.getConfigurationSection("categories").getKeys(false)) {
                categoryUsage.put(cat, analytics.getLong("categories." + cat));
            }
        }
    }

    /**
     * Saves analytics to file
     */
    private void saveAnalytics() {
        analytics.set("totals.views", totalViews);
        analytics.set("totals.creations", totalCreations);

        for (Map.Entry<String, Long> entry : commandUsage.entrySet()) {
            analytics.set("commands." + entry.getKey(), entry.getValue());
        }

        for (Map.Entry<String, Long> entry : categoryUsage.entrySet()) {
            analytics.set("categories." + entry.getKey(), entry.getValue());
        }

        try {
            analytics.save(analyticsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save analytics: " + e.getMessage());
        }
    }

    /**
     * Tracks a command usage
     * @param command Command name
     */
    public void trackCommand(String command) {
        commandUsage.put(command, commandUsage.getOrDefault(command, 0L) + 1);
    }

    /**
     * Tracks a changelog view
     */
    public void trackView() {
        totalViews++;
    }

    /**
     * Tracks a changelog creation
     * @param category Category
     */
    public void trackCreation(String category) {
        totalCreations++;
        if (category != null && !category.isEmpty()) {
            categoryUsage.put(category, categoryUsage.getOrDefault(category, 0L) + 1);
        }
    }

    /**
     * Gets total views
     * @return Total views
     */
    public long getTotalViews() {
        return totalViews;
    }

    /**
     * Gets total creations
     * @return Total creations
     */
    public long getTotalCreations() {
        return totalCreations;
    }

    /**
     * Gets command usage statistics
     * @return Map of command to usage count
     */
    public Map<String, Long> getCommandUsage() {
        return new HashMap<>(commandUsage);
    }

    /**
     * Gets category usage statistics
     * @return Map of category to usage count
     */
    public Map<String, Long> getCategoryUsage() {
        return new HashMap<>(categoryUsage);
    }

    /**
     * Gets most used command
     * @return Command name or null
     */
    public String getMostUsedCommand() {
        return commandUsage.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    /**
     * Gets most used category
     * @return Category name or null
     */
    public String getMostUsedCategory() {
        return categoryUsage.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    /**
     * Starts auto-save task
     */
    public void startAutoSave() {
        if (!plugin.getConfig().getBoolean("analytics.enabled", true)) {
            return;
        }

        int interval = plugin.getConfig().getInt("analytics.save-interval", 300);

        saveTask = new BukkitRunnable() {
            @Override
            public void run() {
                saveAnalytics();
            }
        };

        saveTask.runTaskTimerAsynchronously(plugin, interval * 20L, interval * 20L);
        plugin.getLogger().info("Analytics auto-save started (interval: " + interval + "s)");
    }

    /**
     * Stops auto-save task
     */
    public void stopAutoSave() {
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }
        saveAnalytics();
    }

    /**
     * Resets all analytics
     */
    public void resetAnalytics() {
        totalViews = 0;
        totalCreations = 0;
        commandUsage.clear();
        categoryUsage.clear();
        saveAnalytics();
        plugin.debug("Analytics reset");
    }

    /**
     * Gets analytics summary
     * @return Summary map
     */
    public Map<String, Object> getSummary() {
        Map<String, Object> summary = new HashMap<>();
        summary.put("total_views", totalViews);
        summary.put("total_creations", totalCreations);
        summary.put("most_used_command", getMostUsedCommand());
        summary.put("most_used_category", getMostUsedCategory());
        summary.put("command_count", commandUsage.size());
        summary.put("category_count", categoryUsage.size());
        return summary;
    }
}
