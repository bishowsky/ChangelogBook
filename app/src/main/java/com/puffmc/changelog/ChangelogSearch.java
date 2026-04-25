package com.puffmc.changelog;

import com.puffmc.changelog.manager.ChangelogManager;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles searching through changelog entries
 */
public class ChangelogSearch {
    private final ChangelogManager changelogManager;

    public ChangelogSearch(ChangelogManager changelogManager) {
        this.changelogManager = changelogManager;
    }

    /**
     * Searches changelog entries
     * @param query Search query
     * @return List of matching entries
     */
    public List<ChangelogEntry> search(String query) {
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>();
        }

        List<ChangelogEntry> entries = changelogManager.getEntries();
        String lowerQuery = query.toLowerCase();

        // Check if query has filters
        if (query.contains(":")) {
            return searchWithFilters(entries, query);
        }

        // Simple text search
        return entries.stream()
                .filter(entry -> entry.getContent().toLowerCase().contains(lowerQuery))
                .collect(Collectors.toList());
    }

    /**
     * Searches with advanced filters
     * Supported filters:
     * - author:username
     * - category:categoryName
     * - tag:tagName
     * - date:YYYY-MM-DD
     * @param entries Entries to search
     * @param query Query with filters
     * @return Filtered entries
     */
    private List<ChangelogEntry> searchWithFilters(List<ChangelogEntry> entries, String query) {
        List<ChangelogEntry> results = new ArrayList<>(entries);

        String[] parts = query.split("\\s+");
        for (String part : parts) {
            if (part.contains(":")) {
                String[] filter = part.split(":", 2);
                if (filter.length == 2) {
                    String filterType = filter[0].toLowerCase();
                    String filterValue = filter[1].toLowerCase();

                    switch (filterType) {
                        case "author":
                            results = results.stream()
                                    .filter(e -> e.getAuthor().toLowerCase().contains(filterValue))
                                    .collect(Collectors.toList());
                            break;

                        case "category":
                            results = results.stream()
                                    .filter(e -> e.getCategory() != null && 
                                            e.getCategory().toLowerCase().contains(filterValue))
                                    .collect(Collectors.toList());
                            break;

                        case "date":
                            results = results.stream()
                                    .filter(e -> formatDate(e.getTimestamp()).contains(filterValue))
                                    .collect(Collectors.toList());
                            break;

                        case "tag":
                            // Tags will be implemented later
                            results = new ArrayList<>();
                            break;
                    }
                }
            } else {
                // Regular text search for parts without filters
                final String searchTerm = part.toLowerCase();
                results = results.stream()
                        .filter(e -> e.getContent().toLowerCase().contains(searchTerm))
                        .collect(Collectors.toList());
            }
        }

        return results;
    }

    /**
     * Displays search results to sender
     * @param sender Command sender
     * @param results Search results
     * @param query Original query
     */
    public void displayResults(CommandSender sender, List<ChangelogEntry> results, String query) {
        sender.sendMessage(ChatColor.GOLD + "Search Results for: " + ChatColor.WHITE + query);
        sender.sendMessage(ChatColor.GRAY + "Found " + results.size() + " entries");
        sender.sendMessage("");

        if (results.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No entries found.");
            return;
        }

        int limit = Math.min(results.size(), 10);
        for (int i = 0; i < limit; i++) {
            ChangelogEntry entry = results.get(i);
            String preview = entry.getContent();
            if (preview.length() > 50) {
                preview = preview.substring(0, 50) + "...";
            }

            String categoryDisplay = "";
            if (entry.getCategory() != null && !entry.getCategory().isEmpty()) {
                categoryDisplay = ChatColor.GRAY + "[" + entry.getCategory() + "] ";
            }

            sender.sendMessage(ChatColor.GOLD + "#" + (i + 1) + " " + categoryDisplay + 
                    ChatColor.WHITE + preview);
            sender.sendMessage(ChatColor.GRAY + "  ID: " + entry.getId() + " | By: " + entry.getAuthor());
        }

        if (results.size() > limit) {
            sender.sendMessage("");
            sender.sendMessage(ChatColor.GRAY + "... and " + (results.size() - limit) + " more results");
        }
    }

    /**
     * Formats timestamp to date string
     * @param timestamp Timestamp
     * @return Formatted date
     */
    private String formatDate(long timestamp) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
        return sdf.format(new java.util.Date(timestamp));
    }
}
