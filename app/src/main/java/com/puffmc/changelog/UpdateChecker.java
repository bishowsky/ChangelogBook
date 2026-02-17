package com.puffmc.changelog;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.logging.Level;

/**
 * Checks for plugin updates from GitHub Releases API.
 * Implements caching to prevent excessive API calls.
 */
public class UpdateChecker {
    private final Plugin plugin;
    private final FileConfiguration config;
    private final LogManager logManager;
    
    private String latestVersion;
    private String downloadUrl;
    private long lastCheckTime;
    // ✅ FIX #7: Remove final from cacheTime to allow reload
    private long cacheTime;
    private boolean updateAvailable;

    public UpdateChecker(Plugin plugin, FileConfiguration config, LogManager logManager) {
        this.plugin = plugin;
        this.config = config;
        this.logManager = logManager;
        this.lastCheckTime = 0;
        this.cacheTime = config.getLong("update-checker.check-interval-hours", 6) * 3600000L; // Convert hours to ms
        this.updateAvailable = false;
    }

    /**
     * Checks for updates from GitHub Releases.
     * Uses cached result if within cache time.
     */
    public void checkForUpdates() {
        checkForUpdates(false);
    }

    /**
     * Checks for updates from GitHub Releases.
     * 
     * @param forceRefresh if true, ignores cache and forces a fresh check
     */
    public void checkForUpdates(boolean forceRefresh) {
        if (!config.getBoolean("update-checker.enabled", true)) {
            return;
        }
        
        // ✅ FIX #7: Refresh cache time from config on each check (allows reload)
        cacheTime = config.getLong("update-checker.check-interval-hours", 6) * 3600000L;

        // Return cached result if still valid
        if (!forceRefresh && (System.currentTimeMillis() - lastCheckTime) < cacheTime) {
            return;
        }

        String repo = config.getString("update-checker.github-repo", "bishowsky/ChangelogBook");
        String apiUrl = "https://api.github.com/repos/" + repo + "/releases/latest";

        try {
            URL url = new URL(apiUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json");
            connection.setRequestProperty("User-Agent", "ChangelogBook-UpdateChecker");

            int responseCode = connection.getResponseCode();
            
            if (responseCode == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                // Parse JSON response
                JsonObject jsonObject = JsonParser.parseString(response.toString()).getAsJsonObject();
                
                if (jsonObject.has("tag_name")) {
                    latestVersion = jsonObject.get("tag_name").getAsString();
                    // Remove 'v' prefix if present (e.g., v1.0.0 -> 1.0.0)
                    if (latestVersion.startsWith("v")) {
                        latestVersion = latestVersion.substring(1);
                    }
                }
                
                if (jsonObject.has("html_url")) {
                    downloadUrl = jsonObject.get("html_url").getAsString();
                }

                // Compare versions
                String currentVersion = getCurrentVersion();
                updateAvailable = compareVersions(currentVersion, latestVersion) < 0;
                
                lastCheckTime = System.currentTimeMillis();
                
                if (config.getBoolean("debug", false)) {
                    logManager.log(Level.INFO, "Update check completed. Current: " + currentVersion + ", Latest: " + latestVersion + ", Update available: " + updateAvailable);
                }
                
            } else if (responseCode == 403) {
                // ✅ FIX: Enhanced rate limit handling with reset time
                String rateLimitReset = connection.getHeaderField("X-RateLimit-Reset");
                String rateLimitRemaining = connection.getHeaderField("X-RateLimit-Remaining");
                
                if (rateLimitReset != null) {
                    try {
                        long resetTime = Long.parseLong(rateLimitReset) * 1000L;
                        long minutesUntilReset = (resetTime - System.currentTimeMillis()) / 60000L;
                        logManager.log(Level.WARNING, "GitHub API rate limit exceeded. Resets in " + minutesUntilReset + " minutes.");
                    } catch (NumberFormatException e) {
                        logManager.log(Level.WARNING, "GitHub API rate limit exceeded. Update check skipped.");
                    }
                } else {
                    logManager.log(Level.WARNING, "GitHub API rate limit exceeded. Update check skipped.");
                }
                
                if (rateLimitRemaining != null && rateLimitRemaining.equals("0")) {
                    logManager.log(Level.INFO, "API calls remaining: 0. Consider increasing check-interval-hours in config.yml");
                }
            } else {
                logManager.log(Level.WARNING, "Failed to check for updates. HTTP response code: " + responseCode);
            }
            
            connection.disconnect();
            
        } catch (Exception e) {
            if (config.getBoolean("debug", false)) {
                logManager.log(Level.SEVERE, "Error checking for updates: " + e.getMessage());
            }
        }
    }

    /**
     * Compares two semantic version strings.
     * 
     * @param current current version (e.g., "1.0.0")
     * @param latest latest version (e.g., "1.1.0")
     * @return negative if current < latest, 0 if equal, positive if current > latest
     */
    private int compareVersions(String current, String latest) {
        if (current == null || latest == null) {
            return 0;
        }

        String[] currentParts = current.split("\\.");
        String[] latestParts = latest.split("\\.");
        
        int length = Math.max(currentParts.length, latestParts.length);
        
        for (int i = 0; i < length; i++) {
            int currentPart = i < currentParts.length ? parseVersionPart(currentParts[i]) : 0;
            int latestPart = i < latestParts.length ? parseVersionPart(latestParts[i]) : 0;
            
            if (currentPart < latestPart) {
                return -1;
            } else if (currentPart > latestPart) {
                return 1;
            }
        }
        
        return 0;
    }

    /**
     * Parses a version part to an integer, handling non-numeric suffixes.
     * 
     * @param part version part (e.g., "1", "0", "2-SNAPSHOT")
     * @return numeric value of the version part
     */
    private int parseVersionPart(String part) {
        try {
            // Remove any non-numeric suffix (e.g., "1-SNAPSHOT" -> "1")
            return Integer.parseInt(part.replaceAll("[^0-9].*", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Gets the current plugin version.
     * 
     * @return current version string
     */
    public String getCurrentVersion() {
        return plugin.getDescription().getVersion();
    }

    /**
     * Gets the latest version from GitHub.
     * 
     * @return latest version string, or null if not checked yet
     */
    public String getLatestVersion() {
        return latestVersion;
    }

    /**
     * Gets the download URL for the latest release.
     * 
     * @return download URL, or null if not available
     */
    public String getDownloadUrl() {
        return downloadUrl;
    }

    /**
     * Checks if an update is available.
     * 
     * @return true if newer version exists, false otherwise
     */
    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    /**
     * Checks if the update checker is enabled in config.
     * 
     * @return true if enabled, false otherwise
     */
    public boolean isEnabled() {
        return config.getBoolean("update-checker.enabled", true);
    }
}
