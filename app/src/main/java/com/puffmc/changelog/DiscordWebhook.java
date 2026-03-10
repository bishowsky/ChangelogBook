package com.puffmc.changelog;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.ChatColor;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Level;

/**
 * Discord Webhook integration for changelog notifications
 */
public class DiscordWebhook {
    private final ChangelogPlugin plugin;

    public DiscordWebhook(ChangelogPlugin plugin) {
        this.plugin = plugin;
    }

    // --- Config accessors: always read live values so reload works correctly ---

    private boolean isDiscordEnabled() {
        return plugin.getConfig().getBoolean("discord.enabled", false);
    }

    private String getWebhookUrl() {
        return plugin.getConfig().getString("discord.webhook-url", "");
    }

    private String getMentionRole() {
        return plugin.getConfig().getString("discord.mention-role", "");
    }

    private boolean isNotifyOnAdd() {
        return plugin.getConfig().getBoolean("discord.notify-on-add", true);
    }

    /**
     * Checks if Discord webhook is enabled and configured
     */
    public boolean isEnabled() {
        if (!isDiscordEnabled()) return false;
        String url = getWebhookUrl();
        if (url == null || url.isEmpty() || url.equals("https://discord.com/api/webhooks/...")) {
            return false;
        }
        try {
            new java.net.URL(url);
            if (!url.startsWith("https://discord.com/api/webhooks/") &&
                !url.startsWith("https://discordapp.com/api/webhooks/")) {
                plugin.getLogger().warning("Invalid Discord webhook URL (must start with https://discord.com/api/webhooks/)");
                return false;
            }
            return true;
        } catch (java.net.MalformedURLException e) {
            plugin.getLogger().warning("Malformed Discord webhook URL: " + e.getMessage());
            return false;
        }
    }

    /**
     * Sends a changelog entry notification to Discord
     * @param entry The changelog entry
     * @param author The author name
     */
    public void sendChangelogNotification(ChangelogEntry entry, String author) {
        if (!isEnabled() || !isNotifyOnAdd()) {
            return;
        }

        // Execute async to avoid blocking main thread
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                JsonObject payload = buildChangelogEmbed(entry, author);
                sendWebhook(payload);
                plugin.debug("Discord webhook sent successfully for entry: " + entry.getId());
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to send Discord webhook: " + e.getMessage());
                if (plugin.isDebugMode()) {
                    e.printStackTrace();
                }
            }
        });
    }

    /**
     * Builds a Discord embed for a changelog entry
     */
    private JsonObject buildChangelogEmbed(ChangelogEntry entry, String author) {
        JsonObject payload = new JsonObject();
        
        // Add mention if configured
        String mentionRole = getMentionRole();
        if (mentionRole != null && !mentionRole.isEmpty() && !mentionRole.equals("none")) {
            payload.addProperty("content", mentionRole);
        }

        // Build embed
        JsonObject embed = new JsonObject();
        String title = plugin.getDiscordConfig().getString("embed.title", "📋 New Changelog Entry");
        embed.addProperty("title", title);
        
        // Strip minecraft color codes for Discord
        String cleanContent = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', entry.getContent()));
        
        // Add category prefix if present
        if (entry.getCategory() != null && !entry.getCategory().isEmpty()) {
            String categoryName = getCategoryDisplayName(entry.getCategory());
            String categoryIcon = getCategoryIcon(entry.getCategory());
            
            String prefix = "";
            if (categoryIcon != null && !categoryIcon.isEmpty()) {
                prefix += categoryIcon + " ";
            }
            prefix += "**" + categoryName + "**\n\n";
            
            embed.addProperty("description", prefix + cleanContent);
        } else {
            embed.addProperty("description", cleanContent);
        }
        
        // Color based on category
        embed.addProperty("color", getCategoryDiscordColor(entry.getCategory()));
        
        // Add fields
        JsonArray fields = new JsonArray();
        
        // Author field
        String authorFieldName = plugin.getDiscordConfig().getString("embed.author-field", "👤 Author");
        JsonObject authorField = new JsonObject();
        authorField.addProperty("name", authorFieldName);
        authorField.addProperty("value", author);
        authorField.addProperty("inline", true);
        fields.add(authorField);
        
        // Timestamp field
        String dateFieldName = plugin.getDiscordConfig().getString("embed.date-field", "📅 Date");
        JsonObject timestampField = new JsonObject();
        timestampField.addProperty("name", dateFieldName);
        timestampField.addProperty("value", formatTimestamp(entry.getTimestamp()));
        timestampField.addProperty("inline", true);
        fields.add(timestampField);
        
        embed.add("fields", fields);
        
        // Footer
        String footerText = plugin.getDiscordConfig().getString("embed.footer", "ChangelogBook • Server Update");
        JsonObject footer = new JsonObject();
        footer.addProperty("text", footerText);
        embed.add("footer", footer);
        
        // Timestamp
        embed.addProperty("timestamp", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").format(new Date(entry.getTimestamp())));
        
        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        payload.add("embeds", embeds);
        
        return payload;
    }

    /**
     * Sends the webhook payload to Discord
     */
    private void sendWebhook(JsonObject payload) throws Exception {
        // ✅ FIX #4: Resource leak - use try-finally to ensure connection cleanup
        HttpURLConnection connection = null;
        try {
            URL url = new URL(getWebhookUrl());
            connection = (HttpURLConnection) url.openConnection();
            
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("User-Agent", "ChangelogBook-Webhook");
            connection.setDoOutput(true);
            
            byte[] input = payload.toString().getBytes(StandardCharsets.UTF_8);
            
            try (OutputStream os = connection.getOutputStream()) {
                os.write(input, 0, input.length);
            }
            
            int responseCode = connection.getResponseCode();
            
            if (responseCode == 204) {
                // Success - Discord returns 204 No Content
                plugin.debug("Discord webhook sent successfully (204)");
            } else if (responseCode == 429) {
                // Rate limited
                plugin.getLogger().warning("Discord webhook rate limited! Consider reducing notification frequency.");
            } else if (responseCode >= 400) {
                plugin.getLogger().warning("Discord webhook failed with code: " + responseCode);
            }
            
        } finally {
            // Always disconnect, even if exception occurred
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Gets Discord color code for category.
     * Colors are configured in discord.yml under category-colors.
     */
    private int getCategoryDiscordColor(String category) {
        // 1. Check discord.yml category-colors section
        if (category != null && !category.isEmpty()) {
            String discordYmlColor = plugin.getDiscordConfig().getString("category-colors." + category.toLowerCase(), "");
            if (!discordYmlColor.isEmpty()) {
                try {
                    return Integer.parseInt(discordYmlColor, 16);
                } catch (NumberFormatException ignored) {}
            }
        }

        // 2. Built-in fallback colors
        if (category != null) {
            switch (category.toLowerCase()) {
                case "fix": case "fixed": return 0xFEE75C;   // Yellow
                case "added": case "add": case "new": return 0x57F287; // Green
                case "removed": case "remove": case "deleted": return 0xED4245; // Red
                case "changed": case "change": return 0x5865F2; // Blurple
                case "security": return 0xEB459E; // Pink
            }
        }

        // 3. Default color from discord.yml
        String defaultColor = plugin.getDiscordConfig().getString("category-colors.default", "5865F2");
        try {
            return Integer.parseInt(defaultColor, 16);
        } catch (NumberFormatException e) {
            return 0x5865F2;
        }
    }

    /**
     * Gets category display name
     */
    private String getCategoryDisplayName(String category) {
        if (category == null || category.isEmpty()) {
            return "Update";
        }
        
        String displayName = plugin.getConfig().getString("categories." + category.toLowerCase() + ".name", "");
        if (!displayName.isEmpty()) {
            return displayName;
        }
        
        // Capitalize first letter as fallback
        return category.substring(0, 1).toUpperCase() + category.substring(1);
    }

    /**
     * Gets category icon
     */
    private String getCategoryIcon(String category) {
        if (category == null || category.isEmpty()) {
            return "";
        }
        
        return plugin.getConfig().getString("categories." + category.toLowerCase() + ".icon", "");
    }

    /**
     * Formats timestamp for Discord
     */
    private String formatTimestamp(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm");
        return sdf.format(new Date(timestamp));
    }
}
