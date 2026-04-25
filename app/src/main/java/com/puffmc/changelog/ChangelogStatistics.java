package com.puffmc.changelog;

import com.puffmc.changelog.manager.ChangelogManager;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates statistics about changelog entries
 */
public class ChangelogStatistics {
    private final ChangelogPlugin plugin;
    private final ChangelogManager changelogManager;

    public ChangelogStatistics(ChangelogPlugin plugin, ChangelogManager changelogManager) {
        this.plugin = plugin;
        this.changelogManager = changelogManager;
    }

    /**
     * Sends statistics to command sender
     * @param sender The command sender
     */
    public void sendStatistics(CommandSender sender) {
        List<ChangelogEntry> entries = changelogManager.getEntries();

        if (entries.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No changelog entries found.");
            return;
        }

        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "Changelog Statistics");
        sender.sendMessage(ChatColor.GRAY + "--------------------------");
        sender.sendMessage("");

        // Total entries
        sender.sendMessage(ChatColor.GRAY + "Total Entries: " + ChatColor.WHITE + entries.size());

        // Entries by period
        long now = System.currentTimeMillis();
        long dayMs = 24 * 60 * 60 * 1000L;
        long weekMs = 7 * dayMs;
        long monthMs = 30 * dayMs;

        long today = entries.stream().filter(e -> (now - e.getTimestamp()) < dayMs).count();
        long thisWeek = entries.stream().filter(e -> (now - e.getTimestamp()) < weekMs).count();
        long thisMonth = entries.stream().filter(e -> (now - e.getTimestamp()) < monthMs).count();

        sender.sendMessage(ChatColor.GRAY + "Today: " + ChatColor.WHITE + today);
        sender.sendMessage(ChatColor.GRAY + "This Week: " + ChatColor.WHITE + thisWeek);
        sender.sendMessage(ChatColor.GRAY + "This Month: " + ChatColor.WHITE + thisMonth);
        sender.sendMessage("");

        // Most active author
        Map<String, Long> authorCounts = entries.stream()
                .collect(Collectors.groupingBy(ChangelogEntry::getAuthor, Collectors.counting()));

        if (!authorCounts.isEmpty()) {
            var topAuthor = authorCounts.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .orElse(null);

            if (topAuthor != null) {
                sender.sendMessage(ChatColor.GRAY + "Most Active Author: " + ChatColor.WHITE + 
                        topAuthor.getKey() + ChatColor.GRAY + " (" + topAuthor.getValue() + " entries)");
            }
        }

        // Entries by category
        Map<String, Long> categoryCounts = entries.stream()
                .filter(e -> e.getCategory() != null && !e.getCategory().isEmpty())
                .collect(Collectors.groupingBy(ChangelogEntry::getCategory, Collectors.counting()));

        if (!categoryCounts.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "Most Used Category: " + ChatColor.WHITE + 
                    categoryCounts.entrySet().stream()
                            .max(Map.Entry.comparingByValue())
                            .map(Map.Entry::getKey)
                            .orElse("None"));
        }

        // Average entries per week
        if (entries.size() > 1) {
            ChangelogEntry oldest = entries.stream()
                    .min(Comparator.comparingLong(ChangelogEntry::getTimestamp))
                    .orElse(null);

            if (oldest != null) {
                long ageMs = now - oldest.getTimestamp();
                long weeks = ageMs / weekMs;
                if (weeks > 0) {
                    double avgPerWeek = (double) entries.size() / weeks;
                    sender.sendMessage(ChatColor.GRAY + "Average per Week: " + ChatColor.WHITE + 
                            String.format("%.1f", avgPerWeek));
                }
            }
        }

        // Latest entry
        ChangelogEntry latest = entries.stream()
                .max(Comparator.comparingLong(ChangelogEntry::getTimestamp))
                .orElse(null);

        if (latest != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
            sender.sendMessage("");
            sender.sendMessage(ChatColor.GRAY + "Latest Entry: " + ChatColor.WHITE + 
                    sdf.format(new Date(latest.getTimestamp())));
            sender.sendMessage(ChatColor.GRAY + "By: " + ChatColor.WHITE + latest.getAuthor());
        }

        sender.sendMessage("");
        sender.sendMessage(ChatColor.GRAY + "--------------------------");
    }

    /**
     * Gets detailed statistics as a map
     * @return Statistics map
     */
    public Map<String, Object> getDetailedStatistics() {
        Map<String, Object> stats = new HashMap<>();
        List<ChangelogEntry> entries = changelogManager.getEntries();

        stats.put("total", entries.size());
        
        long now = System.currentTimeMillis();
        long dayMs = 24 * 60 * 60 * 1000L;
        long weekMs = 7 * dayMs;
        long monthMs = 30 * dayMs;

        stats.put("today", entries.stream().filter(e -> (now - e.getTimestamp()) < dayMs).count());
        stats.put("thisWeek", entries.stream().filter(e -> (now - e.getTimestamp()) < weekMs).count());
        stats.put("thisMonth", entries.stream().filter(e -> (now - e.getTimestamp()) < monthMs).count());

        Map<String, Long> authorCounts = entries.stream()
                .collect(Collectors.groupingBy(ChangelogEntry::getAuthor, Collectors.counting()));
        stats.put("authorCounts", authorCounts);

        Map<String, Long> categoryCounts = entries.stream()
                .filter(e -> e.getCategory() != null && !e.getCategory().isEmpty())
                .collect(Collectors.groupingBy(ChangelogEntry::getCategory, Collectors.counting()));
        stats.put("categoryCounts", categoryCounts);

        return stats;
    }
}
