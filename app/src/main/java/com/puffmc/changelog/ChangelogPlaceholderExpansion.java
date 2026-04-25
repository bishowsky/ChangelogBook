package com.puffmc.changelog;

import com.puffmc.changelog.manager.ChangelogManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * PlaceholderAPI expansion for ChangelogBook
 * Provides placeholders like %changelogbook_total%, %changelogbook_new%, etc.
 */
public class ChangelogPlaceholderExpansion extends PlaceholderExpansion {
    private final ChangelogPlugin plugin;
    private final ChangelogManager changelogManager;

    public ChangelogPlaceholderExpansion(ChangelogPlugin plugin, ChangelogManager changelogManager) {
        this.plugin = plugin;
        this.changelogManager = changelogManager;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "changelogbook";
    }

    @Override
    public @NotNull String getAuthor() {
        return plugin.getDescription().getAuthors().toString();
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        List<ChangelogEntry> entries = changelogManager.getEntries();
        long count;

        switch (params.toLowerCase()) {
            case "total":
                return String.valueOf(entries.size());

            case "new":
            case "new_24h":
                long dayMs = 24 * 60 * 60 * 1000L;
                count = entries.stream()
                        .filter(e -> (System.currentTimeMillis() - e.getTimestamp()) < dayMs)
                        .count();
                return String.valueOf(count);

            case "new_week":
                long weekMs = 7 * 24 * 60 * 60 * 1000L;
                count = entries.stream()
                        .filter(e -> (System.currentTimeMillis() - e.getTimestamp()) < weekMs)
                        .count();
                return String.valueOf(count);

            case "new_month":
                long monthMs = 30 * 24 * 60 * 60 * 1000L;
                count = entries.stream()
                        .filter(e -> (System.currentTimeMillis() - e.getTimestamp()) < monthMs)
                        .count();
                return String.valueOf(count);

            case "latest_id":
                return entries.stream()
                        .max((a, b) -> Long.compare(a.getTimestamp(), b.getTimestamp()))
                        .map(ChangelogEntry::getId)
                        .orElse("none");

            case "latest_author":
                return entries.stream()
                        .max((a, b) -> Long.compare(a.getTimestamp(), b.getTimestamp()))
                        .map(ChangelogEntry::getAuthor)
                        .orElse("none");

            case "latest_category":
                return entries.stream()
                        .max((a, b) -> Long.compare(a.getTimestamp(), b.getTimestamp()))
                        .map(ChangelogEntry::getCategory)
                        .orElse("none");

            case "latest_date":
                ChangelogEntry latest = entries.stream()
                        .max((a, b) -> Long.compare(a.getTimestamp(), b.getTimestamp()))
                        .orElse(null);
                if (latest != null) {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                    return sdf.format(new Date(latest.getTimestamp()));
                }
                return "none";

            case "top_author":
                Map<String, Long> authorCounts = entries.stream()
                        .collect(Collectors.groupingBy(ChangelogEntry::getAuthor, Collectors.counting()));
                return authorCounts.entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey)
                        .orElse("none");

            case "top_category":
                Map<String, Long> categoryCounts = entries.stream()
                        .filter(e -> e.getCategory() != null && !e.getCategory().isEmpty())
                        .collect(Collectors.groupingBy(ChangelogEntry::getCategory, Collectors.counting()));
                return categoryCounts.entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey)
                        .orElse("none");
        }

        // Category-specific counts: %changelogbook_count_<category>%
        if (params.startsWith("count_")) {
            String category = params.substring(6);
            long categoryCount = entries.stream()
                    .filter(e -> category.equalsIgnoreCase(e.getCategory()))
                    .count();
            return String.valueOf(categoryCount);
        }

        // Player-specific placeholders
        if (player != null) {
            switch (params.toLowerCase()) {
                case "player_entries":
                    count = entries.stream()
                            .filter(e -> e.getAuthor().equalsIgnoreCase(player.getName()))
                            .count();
                    return String.valueOf(count);

                case "player_last_entry":
                    ChangelogEntry playerLatest = entries.stream()
                            .filter(e -> e.getAuthor().equalsIgnoreCase(player.getName()))
                            .max((a, b) -> Long.compare(a.getTimestamp(), b.getTimestamp()))
                            .orElse(null);
                    if (playerLatest != null) {
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                        return sdf.format(new Date(playerLatest.getTimestamp()));
                    }
                    return "never";
            }
        }

        return null;
    }
}
