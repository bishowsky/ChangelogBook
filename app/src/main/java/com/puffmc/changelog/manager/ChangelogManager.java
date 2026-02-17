package com.puffmc.changelog.manager;

import com.puffmc.changelog.ChangelogEntry;
import com.puffmc.changelog.ChangelogPlugin;
import com.puffmc.changelog.DatabaseManager;
import com.puffmc.changelog.MessageManager;
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
    private MessageManager messageManager;
    // ✅ FIX #10: Cache for display numbers to avoid O(n log n) on every call
    private final Map<String, String> displayNumberCache = new HashMap<>();

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
        plugin.debug("Loading changelog data from MySQL...");
        
        // ✅ FIX #5: Thread-safe database loading
        // Load to temporary collections first
        List<ChangelogEntry> loadedEntries = databaseManager.loadEntries();
        Map<UUID, Long> loadedLastSeen = new HashMap<>();
        
        for (DatabaseManager.PlayerLastSeen lastSeen : databaseManager.loadLastSeen()) {
            try {
                UUID uuid = UUID.fromString(lastSeen.getUuid());
                loadedLastSeen.put(uuid, lastSeen.getTimestamp());
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid UUID in database: " + lastSeen.getUuid());
            }
        }
        
        // Atomic replacement - synchronized to prevent concurrent modification
        synchronized (this) {
            entries.clear();
            entries.addAll(loadedEntries);
            lastSeenMap.clear();
            lastSeenMap.putAll(loadedLastSeen);
            displayNumberCache.clear(); // Invalidate cache
        }
        
        plugin.debug("Loaded " + entries.size() + " changelog entries from database");
        plugin.debug("Loaded " + lastSeenMap.size() + " player last-seen records from database");
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
                    String category = entrySection.getString("category", null);
                    
                    if (!deleted) {
                        ChangelogEntry entry = new ChangelogEntry(id, content, author, timestamp, category);
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
        
        saveToYaml(false);
    }
    
    /**
     * Saves all data to YAML files
     * @param synchronous If true, saves synchronously (used during shutdown)
     */
    private void saveToYaml(boolean synchronous) {
        Runnable saveTask = () -> {
            FileConfiguration data = plugin.getDataConfig();
            
            // Save entries
            data.set("entries", null);
            for (ChangelogEntry entry : entries) {
                String path = "entries." + entry.getId();
                data.set(path + ".content", entry.getContent());
                data.set(path + ".author", entry.getAuthor());
                data.set(path + ".timestamp", entry.getTimestamp());
                data.set(path + ".deleted", entry.isDeleted());
                data.set(path + ".category", entry.getCategory());
            }
            
            // Save last seen timestamps
            data.set("last_seen", null);
            for (Map.Entry<UUID, Long> entry : lastSeenMap.entrySet()) {
                data.set("last_seen." + entry.getKey().toString(), entry.getValue());
            }
            
            plugin.saveDataConfig();
        };
        
        if (synchronous) {
            saveTask.run();
        } else {
            new BukkitRunnable() {
                @Override
                public void run() {
                    saveTask.run();
                }
            }.runTaskAsynchronously(plugin);
        }
    }

    /**
     * Adds a new changelog entry
     * @param content The content of the entry
     * @param author The author of the entry
     * @return The created entry
     */
    public ChangelogEntry addEntry(String content, String author) {
        return addEntry(content, author, null);
    }

    /**
     * Adds a new changelog entry with category
     * @param content The content of the entry
     * @param author The author of the entry
     * @param category The category of the entry (optional, can be null)
     * @return The created entry
     */
    public ChangelogEntry addEntry(String content, String author, String category) {
        ChangelogEntry entry = new ChangelogEntry(content, author, System.currentTimeMillis(), category);
        plugin.debug("Adding new changelog entry by " + author + " [" + (category != null ? category : "no category") + "]: " + content.substring(0, Math.min(50, content.length())));
        
        if (databaseManager.isUsingMySQL()) {
            // ✅ FIX #2: Race condition - add to memory ONLY after successful DB save (Critical)
            new BukkitRunnable() {
                @Override
                public void run() {
                    try {
                        boolean success = databaseManager.addEntry(entry);
                        
                        if (success) {
                            // Add to memory only if DB save succeeded
                            plugin.getServer().getScheduler().runTask(plugin, () -> {
                                synchronized (ChangelogManager.this) {
                                    entries.add(0, entry);
                                    displayNumberCache.clear(); // Invalidate cache
                                }
                                plugin.debug("Entry added to memory after successful DB save: " + entry.getId());
                            });
                        } else {
                            plugin.getLogger().severe("Failed to add entry to database: " + entry.getId());
                            // Notify admin on next login or via console
                            plugin.getLogger().severe("Entry content: " + entry.getContent().substring(0, Math.min(100, entry.getContent().length())));
                        }
                    } catch (Exception e) {
                        plugin.getLogger().severe("Error adding entry to database: " + e.getMessage());
                        if (plugin.isDebugMode()) {
                            e.printStackTrace();
                        }
                    }
                }
            }.runTaskAsynchronously(plugin);
        } else {
            // YAML mode - add immediately
            synchronized (this) {
                entries.add(0, entry);
                displayNumberCache.clear();
            }
            saveData();
        }
        
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
                            try {
                                databaseManager.updateEntry(id, content);
                            } catch (Exception e) {
                                plugin.getLogger().severe("Error updating entry in database: " + e.getMessage());
                                if (plugin.isDebugMode()) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }.runTaskAsynchronously(plugin);
                } else {
                    saveData();
                }
                
                // Invalidate display number cache
                displayNumberCache.clear();
                
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
                            try {
                                databaseManager.removeEntry(id);
                            } catch (Exception e) {
                                plugin.getLogger().severe("Error removing entry from database: " + e.getMessage());
                                if (plugin.isDebugMode()) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }.runTaskAsynchronously(plugin);
                } else {
                    saveData();
                }
                
                // Invalidate display number cache
                displayNumberCache.clear();
                
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
     * Oldest entry = #1, newest entry = highest number
     * @param entry The changelog entry
     * @return The display number string (e.g., "#1")
     */
    public String getEntryDisplayNumber(ChangelogEntry entry) {
        // ✅ FIX #10: Use cache to avoid O(n log n) sorting on every call
        return displayNumberCache.computeIfAbsent(entry.getId(), id -> {
            List<ChangelogEntry> activeEntries = entries.stream()
                    .filter(e -> !e.isDeleted())
                    .sorted((e1, e2) -> Long.compare(e1.getTimestamp(), e2.getTimestamp()))
                    .toList();
            
            int index = activeEntries.indexOf(entry);
            if (index >= 0) {
                return "#" + (index + 1);
            }
            return "#?";
        });
    }

    /**
     * Updates a player's last seen timestamp asynchronously
     * @param player The player
     */
    public void updateLastSeen(Player player) {
        UUID uuid = player.getUniqueId();
        long timestamp = System.currentTimeMillis();
        
        lastSeenMap.put(uuid, timestamp);
        plugin.debug("Updated last-seen for player " + player.getName() + " (" + uuid + ")");
        
        if (databaseManager.isUsingMySQL()) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    try {
                        databaseManager.updateLastSeen(uuid.toString(), timestamp);
                    } catch (Exception e) {
                        plugin.getLogger().severe("Error updating last-seen in database: " + e.getMessage());
                        if (plugin.isDebugMode()) {
                            e.printStackTrace();
                        }
                    }
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
            String today = messageManager != null ? messageManager.getMessage("dates.today") : "Today";
            return today + " " + timeFormat.format(entryTime.getTime());
        }
        
        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        if (isSameDay(entryTime, yesterday)) {
            String yesterdayStr = messageManager != null ? messageManager.getMessage("dates.yesterday") : "Yesterday";
            return yesterdayStr + " " + timeFormat.format(entryTime.getTime());
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
        if (messageManager == null) {
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
        
        return switch (dayOfWeek) {
            case Calendar.MONDAY -> messageManager.getMessage("dates.monday");
            case Calendar.TUESDAY -> messageManager.getMessage("dates.tuesday");
            case Calendar.WEDNESDAY -> messageManager.getMessage("dates.wednesday");
            case Calendar.THURSDAY -> messageManager.getMessage("dates.thursday");
            case Calendar.FRIDAY -> messageManager.getMessage("dates.friday");
            case Calendar.SATURDAY -> messageManager.getMessage("dates.saturday");
            case Calendar.SUNDAY -> messageManager.getMessage("dates.sunday");
            default -> "Day";
        };
    }
    
    private boolean isSameDay(Calendar cal1, Calendar cal2) {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
    }
    
    /**
     * Sets the MessageManager for date formatting translations
     * @param messageManager the MessageManager instance
     */
    public void setMessageManager(MessageManager messageManager) {
        this.messageManager = messageManager;
    }
    
    public void shutdown() {
        if (!databaseManager.isUsingMySQL()) {
            saveToYaml(true); // Synchronous save during shutdown
        }
        if (databaseManager != null) {
            databaseManager.disconnect();
        }
    }
}
