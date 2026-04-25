package com.puffmc.changelog.util;

import com.puffmc.changelog.ChangelogPlugin;
import com.puffmc.changelog.DatabaseManager;
import org.bukkit.ChatColor;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Performs health checks on plugin components
 */
public class HealthChecker {
    private final ChangelogPlugin plugin;

    public HealthChecker(ChangelogPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Performs all health checks
     * @return List of health status messages
     */
    public List<String> performHealthCheck() {
        List<String> results = new ArrayList<>();

        results.add(ChatColor.GOLD + "ChangelogBook Health Check");
        results.add(ChatColor.GRAY + "Version: " + ChatColor.WHITE + plugin.getDescription().getVersion());
        results.add("");

        // Database check
        results.add(checkDatabase());

        // Discord check
        results.add(checkDiscord());

        // Cache check
        results.add(checkCache());

        // Config check
        results.add(checkConfig());

        // Update check
        results.add(checkUpdates());

        // Memory check
        results.add(checkMemory());

        return results;
    }

    private String checkDatabase() {
        DatabaseManager dbManager = plugin.getDatabaseManager();
        
        if (!dbManager.isUsingMySQL()) {
            return formatStatus("Database", "YAML", ChatColor.YELLOW);
        }

        try (Connection conn = dbManager.getClass().getDeclaredMethod("getConnection").invoke(dbManager) != null ? 
                (Connection) dbManager.getClass().getDeclaredMethod("getConnection").invoke(dbManager) : null) {
            
            if (conn != null && !conn.isClosed()) {
                return formatStatus("Database", "MySQL Connected", ChatColor.GREEN);
            } else {
                return formatStatus("Database", "MySQL Disconnected", ChatColor.RED);
            }
        } catch (Exception e) {
            return formatStatus("Database", "Connection Error", ChatColor.RED);
        }
    }

    private String checkDiscord() {
        if (plugin.getDiscordWebhook() == null) {
            return formatStatus("Discord", "Not configured", ChatColor.GRAY);
        }

        if (plugin.getDiscordWebhook().isEnabled()) {
            return formatStatus("Discord", "Webhook Active", ChatColor.GREEN);
        } else {
            return formatStatus("Discord", "Disabled", ChatColor.YELLOW);
        }
    }

    private String checkCache() {
        // Check reward manager cache
        long cacheSize = 0;
        try {
            var rewardManager = plugin.getRewardManager();
            if (rewardManager != null) {
                // Estimate cache usage
                cacheSize = plugin.getChangelogManager().getEntries().size();
            }
        } catch (Exception e) {
            return formatStatus("Cache", "Error", ChatColor.RED);
        }

        if (cacheSize > 9000) {
            return formatStatus("Cache", cacheSize + " entries (High)", ChatColor.YELLOW);
        } else {
            return formatStatus("Cache", cacheSize + " entries", ChatColor.GREEN);
        }
    }

    private String checkConfig() {
        try {
            plugin.getConfig().getString("language");
            plugin.getDataConfig();
            return formatStatus("Config", "Loaded", ChatColor.GREEN);
        } catch (Exception e) {
            return formatStatus("Config", "Error", ChatColor.RED);
        }
    }

    private String checkUpdates() {
        if (!plugin.getConfig().getBoolean("update-checker.enabled", true)) {
            return formatStatus("Updates", "Disabled", ChatColor.GRAY);
        }

        // Check if update is available
        var updateChecker = plugin.getUpdateChecker();
        if (updateChecker != null) {
            return formatStatus("Updates", "Checker Active", ChatColor.GREEN);
        } else {
            return formatStatus("Updates", "Not initialized", ChatColor.YELLOW);
        }
    }

    private String checkMemory() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / 1024 / 1024;
        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
        
        double percentUsed = (double) usedMemory / maxMemory * 100;

        String memoryInfo = usedMemory + "MB / " + maxMemory + "MB (" + String.format("%.1f", percentUsed) + "%)";

        if (percentUsed > 90) {
            return formatStatus("Memory", memoryInfo, ChatColor.RED);
        } else if (percentUsed > 75) {
            return formatStatus("Memory", memoryInfo, ChatColor.YELLOW);
        } else {
            return formatStatus("Memory", memoryInfo, ChatColor.GREEN);
        }
    }

    private String formatStatus(String component, String status, ChatColor color) {
        return ChatColor.GRAY + component + ": " + color + status;
    }
}
