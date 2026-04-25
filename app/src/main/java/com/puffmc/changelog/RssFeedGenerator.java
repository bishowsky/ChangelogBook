package com.puffmc.changelog;

import com.puffmc.changelog.manager.ChangelogManager;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Generates RSS feed for changelog entries
 */
public class RssFeedGenerator {
    private final ChangelogPlugin plugin;
    private final ChangelogManager changelogManager;

    public RssFeedGenerator(ChangelogPlugin plugin, ChangelogManager changelogManager) {
        this.plugin = plugin;
        this.changelogManager = changelogManager;
    }

    /**
     * Generates RSS feed
     * @param outputFile Output file
     * @param limit Maximum number of entries
     * @return true if successful
     */
    public boolean generateFeed(File outputFile, int limit) {
        try {
            List<ChangelogEntry> entries = changelogManager.getEntries();
            entries.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));

            if (limit > 0 && entries.size() > limit) {
                entries = entries.subList(0, limit);
            }

            StringBuilder rss = new StringBuilder();
            rss.append("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n");
            rss.append("<rss version=\"2.0\">\n");
            rss.append("<channel>\n");

            String title = plugin.getConfig().getString("rss.title", "Changelog");
            String description = plugin.getConfig().getString("rss.description", "Server changelog updates");
            String link = plugin.getConfig().getString("rss.link", "https://example.com");

            rss.append("  <title>").append(escapeXml(title)).append("</title>\n");
            rss.append("  <description>").append(escapeXml(description)).append("</description>\n");
            rss.append("  <link>").append(escapeXml(link)).append("</link>\n");
            rss.append("  <language>en-us</language>\n");
            rss.append("  <lastBuildDate>").append(formatRssDate(System.currentTimeMillis())).append("</lastBuildDate>\n");
            rss.append("\n");

            for (ChangelogEntry entry : entries) {
                rss.append("  <item>\n");
                
                String itemTitle = entry.getCategory() != null ? 
                        entry.getCategory() + ": " + truncate(entry.getContent(), 50) :
                        truncate(entry.getContent(), 50);
                
                rss.append("    <title>").append(escapeXml(itemTitle)).append("</title>\n");
                rss.append("    <description>").append(escapeXml(stripColors(entry.getContent()))).append("</description>\n");
                rss.append("    <pubDate>").append(formatRssDate(entry.getTimestamp())).append("</pubDate>\n");
                rss.append("    <guid>").append(escapeXml(entry.getId())).append("</guid>\n");
                rss.append("    <author>").append(escapeXml(entry.getAuthor())).append("</author>\n");
                
                if (entry.getCategory() != null && !entry.getCategory().isEmpty()) {
                    rss.append("    <category>").append(escapeXml(entry.getCategory())).append("</category>\n");
                }
                
                rss.append("  </item>\n\n");
            }

            rss.append("</channel>\n");
            rss.append("</rss>");

            try (FileWriter writer = new FileWriter(outputFile)) {
                writer.write(rss.toString());
            }

            plugin.debug("Generated RSS feed with " + entries.size() + " entries");
            return true;
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to generate RSS feed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Auto-generates RSS feed at configured path
     * @return true if successful
     */
    public boolean autoGenerate() {
        if (!plugin.getConfig().getBoolean("rss.enabled", false)) {
            return false;
        }

        String path = plugin.getConfig().getString("rss.output-path", "changelog.xml");
        File outputFile = new File(plugin.getDataFolder(), path);
        int limit = plugin.getConfig().getInt("rss.max-entries", 50);

        return generateFeed(outputFile, limit);
    }

    /**
     * Formats timestamp for RSS pubDate
     * @param timestamp Timestamp
     * @return Formatted date
     */
    private String formatRssDate(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z");
        return sdf.format(new Date(timestamp));
    }

    /**
     * Escapes XML special characters
     * @param text Text to escape
     * @return Escaped text
     */
    private String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&apos;");
    }

    /**
     * Strips Minecraft color codes
     * @param text Text with color codes
     * @return Plain text
     */
    private String stripColors(String text) {
        return text.replaceAll("&[0-9a-fk-or]", "");
    }

    /**
     * Truncates text to specified length
     * @param text Text
     * @param length Max length
     * @return Truncated text
     */
    private String truncate(String text, int length) {
        String plain = stripColors(text);
        if (plain.length() <= length) {
            return plain;
        }
        return plain.substring(0, length) + "...";
    }
}
