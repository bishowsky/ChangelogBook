package com.puffmc.changelog;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Manages impact levels for changelog entries
 */
public class ImpactLevel {
    private final ChangelogPlugin plugin;
    private final File impactFile;
    private FileConfiguration impact;
    private final Map<String, Impact> entryImpacts;

    public ImpactLevel(ChangelogPlugin plugin) {
        this.plugin = plugin;
        this.impactFile = new File(plugin.getDataFolder(), "impacts.yml");
        this.entryImpacts = new HashMap<>();
        loadImpacts();
    }

    /**
     * Loads impacts from file
     */
    private void loadImpacts() {
        if (!impactFile.exists()) {
            try {
                impactFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to create impacts file: " + e.getMessage());
                return;
            }
        }

        impact = YamlConfiguration.loadConfiguration(impactFile);

        for (String entryId : impact.getKeys(false)) {
            String level = impact.getString(entryId + ".level");
            String reason = impact.getString(entryId + ".reason");
            int severity = impact.getInt(entryId + ".severity", 1);

            if (level != null) {
                entryImpacts.put(entryId, new Impact(level, reason, severity));
            }
        }
    }

    /**
     * Saves impacts to file
     */
    private void saveImpacts() {
        for (Map.Entry<String, Impact> entry : entryImpacts.entrySet()) {
            String entryId = entry.getKey();
            Impact impactData = entry.getValue();

            impact.set(entryId + ".level", impactData.getLevel());
            impact.set(entryId + ".reason", impactData.getReason());
            impact.set(entryId + ".severity", impactData.getSeverity());
        }

        try {
            impact.save(impactFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save impacts: " + e.getMessage());
        }
    }

    /**
     * Sets impact level for an entry
     * @param entryId Entry ID
     * @param level Impact level (CRITICAL, HIGH, MEDIUM, LOW, MINOR)
     * @param reason Reason for impact level
     */
    public void setImpact(String entryId, String level, String reason) {
        int severity = calculateSeverity(level);
        entryImpacts.put(entryId, new Impact(level.toUpperCase(), reason, severity));
        saveImpacts();
        plugin.debug("Set impact level " + level + " for entry " + entryId);
    }

    /**
     * Gets impact for an entry
     * @param entryId Entry ID
     * @return Impact or null
     */
    public Impact getImpact(String entryId) {
        return entryImpacts.get(entryId);
    }

    /**
     * Removes impact from an entry
     * @param entryId Entry ID
     * @return true if removed
     */
    public boolean removeImpact(String entryId) {
        if (entryImpacts.remove(entryId) != null) {
            impact.set(entryId, null);
            saveImpacts();
            return true;
        }
        return false;
    }

    /**
     * Gets entries by impact level
     * @param level Impact level
     * @return List of entry IDs
     */
    public List<String> getEntriesByImpact(String level) {
        List<String> result = new ArrayList<>();
        String upperLevel = level.toUpperCase();

        for (Map.Entry<String, Impact> entry : entryImpacts.entrySet()) {
            if (entry.getValue().getLevel().equals(upperLevel)) {
                result.add(entry.getKey());
            }
        }

        return result;
    }

    /**
     * Gets entries with minimum severity
     * @param minSeverity Minimum severity (1-5)
     * @return List of entry IDs
     */
    public List<String> getEntriesBySeverity(int minSeverity) {
        List<String> result = new ArrayList<>();

        for (Map.Entry<String, Impact> entry : entryImpacts.entrySet()) {
            if (entry.getValue().getSeverity() >= minSeverity) {
                result.add(entry.getKey());
            }
        }

        return result;
    }

    /**
     * Calculates severity from level
     * @param level Impact level
     * @return Severity (1-5)
     */
    private int calculateSeverity(String level) {
        switch (level.toUpperCase()) {
            case "CRITICAL":
                return 5;
            case "HIGH":
                return 4;
            case "MEDIUM":
                return 3;
            case "LOW":
                return 2;
            case "MINOR":
            default:
                return 1;
        }
    }

    /**
     * Gets color code for impact level
     * @param level Impact level
     * @return Color code
     */
    public String getImpactColor(String level) {
        switch (level.toUpperCase()) {
            case "CRITICAL":
                return "&4";
            case "HIGH":
                return "&c";
            case "MEDIUM":
                return "&e";
            case "LOW":
                return "&a";
            case "MINOR":
            default:
                return "&7";
        }
    }

    /**
     * Represents an impact level
     */
    public static class Impact {
        private final String level;
        private final String reason;
        private final int severity;

        public Impact(String level, String reason, int severity) {
            this.level = level;
            this.reason = reason;
            this.severity = severity;
        }

        public String getLevel() {
            return level;
        }

        public String getReason() {
            return reason;
        }

        public int getSeverity() {
            return severity;
        }
    }
}
