package com.puffmc.changelog.manager;

import com.puffmc.changelog.ChangelogEntry;
import com.puffmc.changelog.ChangelogPlugin;
import com.puffmc.changelog.DatabaseManager;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.text.SimpleDateFormat;
import java.util.*;

public class ChangelogManager {
    private final ChangelogPlugin plugin;
    private final Map<UUID, Long> lastSeenMap = new HashMap<>();
    private final List<ChangelogEntry> entries = new ArrayList<>();
    private final DatabaseManager databaseManager;

    public ChangelogManager(ChangelogPlugin plugin) {
        this.plugin = plugin;
        this.databaseManager = new DatabaseManager(plugin);
        loadData();
    }

    /**
     * Loads all data from the selected storage method
     */
    public void loadData() {
        if (databaseManager.isUsingMySQL()) {
            loadFromDatabase();
        } else {
            loadFromYaml();
        }
    }

    /**
     * Loads data from MySQL database asynchronously
     */
    private void loadFromDatabase() {
        entries.clear();
        lastSeenMap.clear();
        
        entries.addAll(databaseManager.loadEntries());
        
        for (DatabaseManager.PlayerLastSeen lastSeen : databaseManager.loadLastSeen()) {
            try {
                UUID uuid = UUID.fromString(lastSeen.getUuid());
                lastSeenMap.put(uuid, lastSeen.getTimestamp());
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid UUID in database: " + lastSeen.getUuid());
            }
        }
    }

    /**
     * Loads data from YAML files
     */
    private void loadFromYaml() {
        loadEntriesFromYaml();
        loadLastSeenFromYaml();
    }
    
    /**
     * Loads all changelog entries from the data file
     */
    private void loadEntriesFromYaml() {
        entries.clear();
        FileConfiguration data = plugin.getDataConfig();
        ConfigurationSection entriesSection = data.getConfigurationSection("entries");
        
        if (entriesSection != null) {
            for (String key : entriesSection.getKeys(false)) {
                ConfigurationSection entrySection = entriesSection.getConfigurationSection(key);
                if (entrySection != null) {
                    String id = key;
                    String content = entrySection.getString("content", "");
                    String author = entrySection.getString("author", "Unknown");
                    long timestamp = entrySection.getLong("timestamp", System.currentTimeMillis());
                    boolean deleted = entrySection.getBoolean("deleted", false);
                    
                    if (!deleted) {
                        ChangelogEntry entry = new ChangelogEntry(id, content, author, timestamp);
                        entries.add(entry);
                    }
                }
            }
        }
        
        // Sort entries by timestamp (newest first)
        entries.sort((e1, e2) -> Long.compare(e2.getTimestamp(), e1.getTimestamp()));
    }

    /**
     * Loads last seen timestamps for players from YAML
     */
    private void loadLastSeenFromYaml() {
        lastSeenMap.clear();
        FileConfiguration data = plugin.getDataConfig();
        ConfigurationSection lastSeenSection = data.getConfigurationSection("last_seen");
        
        if (lastSeenSection != null) {
            for (String uuidStr : lastSeenSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    long timestamp = lastSeenSection.getLong(uuidStr);
                    lastSeenMap.put(uuid, timestamp);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in last_seen: " + uuidStr);
                }
            }
        }
    }

    /**
     * Saves all data to the appropriate storage
     */
    public void saveData() {
        if (databaseManager.isUsingMySQL()) {
            return;
        }
        
        saveToYaml();
    }
    
    /**
     * Saves all data to YAML files asynchronously
     */
    private void saveToYaml() {
        new BukkitRunnable() {
            @Override
            public void run() {
                FileConfiguration data = plugin.getDataConfig();
                
                // Save entries
                data.set("entries", null);
                for (ChangelogEntry entry : entries) {
                    String path = "entries." + entry.getId();
                    data.set(path + ".content", entry.getContent());
                    data.set(path + ".author", entry.getAuthor());
                    data.set(path + ".timestamp", entry.getTimestamp());
                    data.set(path + ".deleted", entry.isDeleted());
                }
                
                // Save last seen timestamps
                data.set("last_seen", null);
                for (Map.Entry<UUID, Long> entry : lastSeenMap.entrySet()) {
                    data.set("last_seen." + entry.getKey().toString(), entry.getValue());
                }
                
                plugin.saveDataConfig();
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * Adds a new changelog entry
     * @param content The content of the entry
     * @param author The author of the entry
     * @return The created entry
     */
    public ChangelogEntry addEntry(String content, String author) {
        ChangelogEntry entry = new ChangelogEntry(content, author, System.currentTimeMillis());
        
        if (databaseManager.isUsingMySQL()) {
            // Async database save
            new BukkitRunnable() {
                @Override
                public void run() {
                    databaseManager.addEntry(entry);
                }
            }.runTaskAsynchronously(plugin);
        } else {
            saveData();
        }
        
        entries.add(0, entry);
        return entry;
    }

    /**
     * Edits an existing changelog entry
     * @param id The id of the entry to edit
     * @param content The new content
     * @return true if successful
     */
    public boolean editEntry(String id, String content) {
        for (ChangelogEntry entry : entries) {
            if (entry.getId().equals(id) && !entry.isDeleted()) {
                entry.setContent(content);
                
                if (databaseManager.isUsingMySQL()) {
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            databaseManager.updateEntry(id, content);
                        }
                    }.runTaskAsynchronously(plugin);
                } else {
                    saveData();
                }
                
                return true;
            }
        }
        return false;
    }

    /**
     * Removes (soft-deletes) a changelog entry
     * @param id The id of the entry to remove
     * @return true if successful
     */
    public boolean removeEntry(String id) {
        for (ChangelogEntry entry : entries) {
            if (entry.getId().equals(id) && !entry.isDeleted()) {
                entry.setDeleted(true);
                
                if (databaseManager.isUsingMySQL()) {
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            databaseManager.removeEntry(id);
                        }
                    }.runTaskAsynchronously(plugin);
                } else {
                    saveData();
                }
                
                return true;
            }
        }
        return false;
    }

    /**
     * Gets all active (non-deleted) changelog entries
     * @return List of entries
     */
    public List<ChangelogEntry> getEntries() {
        return new ArrayList<>(entries.stream()
                .filter(e -> !e.isDeleted())
                .toList());
    }

    /**
     * Gets the display number for a changelog entry (e.g., #1, #2, #3)
     * @param entry The changelog entry
     * @return The display number string (e.g., "#1")
     */
    public String getEntryDisplayNumber(ChangelogEntry entry) {
        List<ChangelogEntry> activeEntries = entries.stream()
                .filter(e -> !e.isDeleted())
                .sorted((e1, e2) -> Long.compare(e2.getTimestamp(), e1.getTimestamp()))
                .toList();
        
        int index = activeEntries.indexOf(entry);
        if (index >= 0) {
            return "#" + (index + 1);
        }
        return "#?";
    }

    /**
     * Updates a player's last seen timestamp asynchronously
     * @param player The player
     */
    public void updateLastSeen(Player player) {
        UUID uuid = player.getUniqueId();
        long timestamp = System.currentTimeMillis();
        
        lastSeenMap.put(uuid, timestamp);
        
        if (databaseManager.isUsingMySQL()) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    databaseManager.updateLastSeen(uuid.toString(), timestamp);
                }
            }.runTaskAsynchronously(plugin);
        } else {
            saveData();
        }
    }

    /**
     * Gets the last time a player checked the changelog
     * @param player The player
     * @return The timestamp or 0 if never seen
     */
    public long getLastSeen(Player player) {
        UUID uuid = player.getUniqueId();
        return lastSeenMap.getOrDefault(uuid, 0L);
    }

    /**
     * Gets new entries since player's last visit
     * @param player The player
     * @return Count of new entries
     */
    public int getNewEntriesCount(Player player) {
        long lastSeen = getLastSeen(player);
        if (lastSeen == 0) {
            return (int) entries.stream().filter(e -> !e.isDeleted()).count();
        }
        
        return (int) entries.stream()
                .filter(e -> !e.isDeleted() && e.getTimestamp() > lastSeen)
                .count();
    }

    /**
     * Formats a timestamp to a readable date
     * @param timestamp The timestamp to format
     * @return A formatted date string
     */
    public String formatDate(long timestamp) {
        Calendar now = Calendar.getInstance();
        Calendar entryTime = Calendar.getInstance();
        entryTime.setTimeInMillis(timestamp);
        
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm");
        SimpleDateFormat shortDateFormat = new SimpleDateFormat("dd.MM.yyyy");
        
        if (isSameDay(entryTime, now)) {
            return "Today " + timeFormat.format(entryTime.getTime());
        }
        
        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        if (isSameDay(entryTime, yesterday)) {
            return "Yesterday " + timeFormat.format(entryTime.getTime());
        }
        
        Calendar lastWeek = Calendar.getInstance();
        lastWeek.add(Calendar.DAY_OF_YEAR, -6);
        if (entryTime.after(lastWeek)) {
            String dayName = getDayName(entryTime.get(Calendar.DAY_OF_WEEK));
            return dayName + " " + timeFormat.format(entryTime.getTime());
        }
        
        return shortDateFormat.format(entryTime.getTime());
    }

    private String getDayName(int dayOfWeek) {
        return switch (dayOfWeek) {
            case Calendar.MONDAY -> "Monday";
            case Calendar.TUESDAY -> "Tuesday";
            case Calendar.WEDNESDAY -> "Wednesday";
            case Calendar.THURSDAY -> "Thursday";
            case Calendar.FRIDAY -> "Friday";
            case Calendar.SATURDAY -> "Saturday";
            case Calendar.SUNDAY -> "Sunday";
            default -> "Day";
        };
    }
    
    private boolean isSameDay(Calendar cal1, Calendar cal2) {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
    }
    
    public void shutdown() {
        saveData();
        if (databaseManager != null) {
            databaseManager.disconnect();
        }
    }
}
