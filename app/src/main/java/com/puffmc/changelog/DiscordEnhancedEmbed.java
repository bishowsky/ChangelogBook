package com.puffmc.changelog;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.awt.Color;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Generates enhanced Discord webhook embeds
 */
public class DiscordEnhancedEmbed {
    private final ChangelogPlugin plugin;

    public DiscordEnhancedEmbed(ChangelogPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Creates an enhanced embed for a changelog entry
     * @param entry Changelog entry
     * @return JSON object for embed
     */
    public JsonObject createEmbed(ChangelogEntry entry) {
        JsonObject embed = new JsonObject();

        String title = getEmbedTitle(entry);
        String description = stripColors(entry.getContent());
        
        embed.addProperty("title", title);
        embed.addProperty("description", truncate(description, 4096));
        embed.addProperty("color", getCategoryColor(entry.getCategory()));
        embed.addProperty("timestamp", formatTimestamp(entry.getTimestamp()));

        JsonObject footer = new JsonObject();
        footer.addProperty("text", "ID: " + entry.getId());
        embed.add("footer", footer);

        JsonObject author = new JsonObject();
        author.addProperty("name", entry.getAuthor());
        embed.add("author", author);

        JsonArray fields = new JsonArray();

        if (entry.getCategory() != null && !entry.getCategory().isEmpty()) {
            JsonObject categoryField = new JsonObject();
            categoryField.addProperty("name", "Category");
            categoryField.addProperty("value", getCategoryIcon(entry.getCategory()) + " " + entry.getCategory());
            categoryField.addProperty("inline", true);
            fields.add(categoryField);
        }

        JsonObject dateField = new JsonObject();
        dateField.addProperty("name", "Date");
        dateField.addProperty("value", formatDate(entry.getTimestamp()));
        dateField.addProperty("inline", true);
        fields.add(dateField);

        if (fields.size() > 0) {
            embed.add("fields", fields);
        }

        return embed;
    }

    /**
     * Creates a summary embed for multiple entries
     * @param entries List of entries
     * @param title Embed title
     * @return JSON object for embed
     */
    public JsonObject createSummaryEmbed(List<ChangelogEntry> entries, String title) {
        JsonObject embed = new JsonObject();

        embed.addProperty("title", title);
        embed.addProperty("color", parseColor("#5865F2"));
        embed.addProperty("timestamp", formatTimestamp(System.currentTimeMillis()));

        StringBuilder description = new StringBuilder();
        int limit = Math.min(entries.size(), 10);

        for (int i = 0; i < limit; i++) {
            ChangelogEntry entry = entries.get(i);
            String icon = getCategoryIcon(entry.getCategory());
            String content = truncate(stripColors(entry.getContent()), 50);
            description.append(icon).append(" **").append(entry.getCategory()).append("** - ")
                       .append(content).append("\n");
        }

        if (entries.size() > limit) {
            description.append("\n*... and ").append(entries.size() - limit).append(" more*");
        }

        embed.addProperty("description", description.toString());

        JsonObject footer = new JsonObject();
        footer.addProperty("text", "Total entries: " + entries.size());
        embed.add("footer", footer);

        return embed;
    }

    /**
     * Gets embed title for entry
     * @param entry Entry
     * @return Title
     */
    private String getEmbedTitle(ChangelogEntry entry) {
        String serverName = plugin.getConfig().getString("server-name", "Server");
        String category = entry.getCategory() != null ? entry.getCategory() : "Update";
        return serverName + " - " + category + " Changelog";
    }

    /**
     * Gets category icon emoji
     * @param category Category
     * @return Icon
     */
    private String getCategoryIcon(String category) {
        if (category == null) return "📝";
        
        String icon = plugin.getConfig().getString("categories." + category.toLowerCase() + ".discord-icon");
        if (icon != null && !icon.isEmpty()) {
            return icon;
        }

        switch (category.toLowerCase()) {
            case "added":
            case "new":
                return "✅";
            case "fixed":
            case "bugfix":
                return "🔧";
            case "changed":
            case "update":
                return "🔄";
            case "removed":
            case "deprecated":
                return "❌";
            case "security":
                return "🔒";
            default:
                return "📝";
        }
    }

    /**
     * Gets category color
     * @param category Category
     * @return Color as integer
     */
    private int getCategoryColor(String category) {
        if (category == null) return parseColor("#7289DA");

        String colorStr = plugin.getConfig().getString("categories." + category.toLowerCase() + ".discord-color");
        if (colorStr != null && !colorStr.isEmpty()) {
            return parseColor(colorStr);
        }

        switch (category.toLowerCase()) {
            case "added":
            case "new":
                return parseColor("#43B581");
            case "fixed":
            case "bugfix":
                return parseColor("#FAA61A");
            case "changed":
            case "update":
                return parseColor("#5865F2");
            case "removed":
            case "deprecated":
                return parseColor("#F04747");
            case "security":
                return parseColor("#ED4245");
            default:
                return parseColor("#7289DA");
        }
    }

    /**
     * Parses hex color to integer
     * @param hex Hex color (#RRGGBB)
     * @return Color integer
     */
    private int parseColor(String hex) {
        try {
            Color color = Color.decode(hex);
            return (color.getRed() << 16) | (color.getGreen() << 8) | color.getBlue();
        } catch (Exception e) {
            return 7506394;
        }
    }

    /**
     * Formats timestamp for ISO 8601
     * @param timestamp Timestamp
     * @return Formatted timestamp
     */
    private String formatTimestamp(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        return sdf.format(new Date(timestamp));
    }

    /**
     * Formats date for display
     * @param timestamp Timestamp
     * @return Formatted date
     */
    private String formatDate(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        return sdf.format(new Date(timestamp));
    }

    /**
     * Strips Minecraft color codes
     * @param text Text
     * @return Plain text
     */
    private String stripColors(String text) {
        return text.replaceAll("&[0-9a-fk-or]", "");
    }

    /**
     * Truncates text
     * @param text Text
     * @param length Max length
     * @return Truncated text
     */
    private String truncate(String text, int length) {
        if (text.length() <= length) {
            return text;
        }
        return text.substring(0, length) + "...";
    }
}
