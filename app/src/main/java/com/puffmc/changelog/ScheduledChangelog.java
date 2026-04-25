package com.puffmc.changelog;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Manages scheduled changelog entries
 */
public class ScheduledChangelog {
    private final ChangelogPlugin plugin;
    private final File scheduledFile;
    private FileConfiguration scheduled;
    private final Map<String, ScheduledEntry> scheduledEntries;
    private BukkitRunnable checkTask;
    private int nextScheduleId = 1; // Counter for schedule IDs

    public ScheduledChangelog(ChangelogPlugin plugin) {
        this.plugin = plugin;
        this.scheduledFile = new File(plugin.getDataFolder(), "scheduled.yml");
        this.scheduledEntries = new HashMap<>();
        loadScheduled();
    }
    
    /**
     * Gets the next schedule ID
     * @return Next numeric ID as string
     */
    private synchronized String getNextScheduleId() {
        // Find highest existing numeric schedule ID
        int maxId = 0;
        for (String key : scheduledEntries.keySet()) {
            try {
                int id = Integer.parseInt(key);
                if (id > maxId) {
                    maxId = id;
                }
            } catch (NumberFormatException e) {
                // Not a numeric ID, skip
            }
        }
        
        // Set next ID counter
        nextScheduleId = maxId + 1;
        return String.valueOf(nextScheduleId++);
    }

    /**
     * Loads scheduled entries from file
     */
    private void loadScheduled() {
        if (!scheduledFile.exists()) {
            try {
                scheduledFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to create scheduled file: " + e.getMessage());
                return;
            }
        }

        scheduled = YamlConfiguration.loadConfiguration(scheduledFile);

        for (String id : scheduled.getKeys(false)) {
            String content = scheduled.getString(id + ".content");
            String author = scheduled.getString(id + ".author");
            String category = scheduled.getString(id + ".category");
            long publishTime = scheduled.getLong(id + ".publish_time");
            String customId = scheduled.getString(id + ".custom_id");

            if (content != null && author != null) {
                scheduledEntries.put(id, new ScheduledEntry(id, content, author, category, publishTime, customId));
            }
        }

        plugin.debug("Loaded " + scheduledEntries.size() + " scheduled entries");
    }

    /**
     * Saves scheduled entries to file
     */
    private void saveScheduled() {
        for (ScheduledEntry entry : scheduledEntries.values()) {
            scheduled.set(entry.getId() + ".content", entry.getContent());
            scheduled.set(entry.getId() + ".author", entry.getAuthor());
            scheduled.set(entry.getId() + ".category", entry.getCategory());
            scheduled.set(entry.getId() + ".publish_time", entry.getPublishTime());
            scheduled.set(entry.getId() + ".custom_id", entry.getCustomId());
        }

        try {
            scheduled.save(scheduledFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save scheduled entries: " + e.getMessage());
        }
    }

    /**
     * Schedules a new entry
     * @param content Entry content
     * @param author Author
     * @param category Category
     * @param publishTime When to publish (timestamp)
     * @param customId Custom ID (optional)
     * @return Schedule ID
     */
    public String scheduleEntry(String content, String author, String category, long publishTime, String customId) {
        String scheduleId = getNextScheduleId();
        ScheduledEntry entry = new ScheduledEntry(scheduleId, content, author, category, publishTime, customId);
        
        scheduledEntries.put(scheduleId, entry);
        saveScheduled();
        
        plugin.debug("Scheduled entry " + scheduleId + " for " + new Date(publishTime));
        return scheduleId;
    }

    /**
     * Cancels a scheduled entry
     * @param scheduleId Schedule ID
     * @return true if cancelled
     */
    public boolean cancelScheduled(String scheduleId) {
        if (scheduledEntries.remove(scheduleId) != null) {
            scheduled.set(scheduleId, null);
            saveScheduled();
            plugin.debug("Cancelled scheduled entry: " + scheduleId);
            return true;
        }
        return false;
    }

    /**
     * Gets all scheduled entries
     * @return Collection of scheduled entries
     */
    public Collection<ScheduledEntry> getScheduledEntries() {
        return scheduledEntries.values();
    }

    /**
     * Gets upcoming scheduled entries
     * @param limit Number of entries
     * @return List of upcoming entries
     */
    public List<ScheduledEntry> getUpcoming(int limit) {
        return scheduledEntries.values().stream()
                .filter(e -> e.getPublishTime() > System.currentTimeMillis())
                .sorted(Comparator.comparingLong(ScheduledEntry::getPublishTime))
                .limit(limit)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Starts the scheduler task
     */
    public void startScheduler() {
        if (!plugin.getConfig().getBoolean("scheduled-entries.enabled", false)) {
            return;
        }

        int checkInterval = plugin.getConfig().getInt("scheduled-entries.check-interval", 60);

        checkTask = new BukkitRunnable() {
            @Override
            public void run() {
                checkAndPublish();
            }
        };

        checkTask.runTaskTimerAsynchronously(plugin, 20L, checkInterval * 20L);
        plugin.getLogger().info("Scheduled entry checker started (interval: " + checkInterval + "s)");
    }

    /**
     * Stops the scheduler task
     */
    public void stopScheduler() {
        if (checkTask != null) {
            checkTask.cancel();
            checkTask = null;
        }
    }

    /**
     * Checks and publishes due entries
     */
    private void checkAndPublish() {
        long now = System.currentTimeMillis();
        List<String> toPublish = new ArrayList<>();

        for (ScheduledEntry entry : scheduledEntries.values()) {
            if (entry.getPublishTime() <= now) {
                toPublish.add(entry.getId());
            }
        }

        for (String scheduleId : toPublish) {
            publishScheduledEntry(scheduleId);
        }
    }

    /**
     * Publishes a scheduled entry
     * @param scheduleId Schedule ID
     */
    private void publishScheduledEntry(String scheduleId) {
        ScheduledEntry entry = scheduledEntries.get(scheduleId);
        if (entry == null) return;

        new BukkitRunnable() {
            @Override
            public void run() {
                String customId = entry.getCustomId();
                if (customId != null && !customId.isEmpty()) {
                    plugin.getChangelogManager().addEntry(customId, entry.getContent(), entry.getAuthor(), entry.getCategory());
                } else {
                    plugin.getChangelogManager().addEntry(entry.getContent(), entry.getAuthor(), entry.getCategory());
                }

                cancelScheduled(scheduleId);
                plugin.getLogger().info("Published scheduled entry: " + scheduleId);
            }
        }.runTask(plugin);
    }

    /**
     * Represents a scheduled entry
     */
    public static class ScheduledEntry {
        private final String id;
        private final String content;
        private final String author;
        private final String category;
        private final long publishTime;
        private final String customId;

        public ScheduledEntry(String id, String content, String author, String category, long publishTime, String customId) {
            this.id = id;
            this.content = content;
            this.author = author;
            this.category = category;
            this.publishTime = publishTime;
            this.customId = customId;
        }

        public String getId() {
            return id;
        }

        public String getContent() {
            return content;
        }

        public String getAuthor() {
            return author;
        }

        public String getCategory() {
            return category;
        }

        public long getPublishTime() {
            return publishTime;
        }

        public String getCustomId() {
            return customId;
        }
    }
}
