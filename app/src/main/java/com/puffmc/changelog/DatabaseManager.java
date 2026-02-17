package com.puffmc.changelog;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.ConfigurationSection;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public class DatabaseManager {
    private final ChangelogPlugin plugin;
    private HikariDataSource dataSource;
    private boolean useMySQL;
    private String host, database, username, password, tablePrefix;
    private int port;

    // Table names
    private String entriesTable;
    private String lastSeenTable;
    private String cooldownsTable;

    public DatabaseManager(ChangelogPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    /**
     * Loads database configuration from config
     */
    private void loadConfig() {
        ConfigurationSection mysqlConfig = plugin.getConfig().getConfigurationSection("mysql");
        if (mysqlConfig != null) {
            useMySQL = mysqlConfig.getBoolean("enabled", false);
            host = mysqlConfig.getString("host", "localhost");
            port = mysqlConfig.getInt("port", 3306);
            database = mysqlConfig.getString("database", "minecraft");
            username = mysqlConfig.getString("username", "root");
            password = mysqlConfig.getString("password", "");
            tablePrefix = mysqlConfig.getString("table-prefix", "changelog_");
            
            entriesTable = tablePrefix + "entries";
            lastSeenTable = tablePrefix + "lastseen";
            cooldownsTable = tablePrefix + "cooldowns";
        } else {
            useMySQL = false;
        }
    }

    /**
     * Initializes HikariCP connection pool
     * @return true if successful, false on error
     */
    public boolean connect() {
        if (!useMySQL) {
            plugin.getLogger().info("Using YAML storage for changelog data.");
            return true;
        }
        
        try {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&autoReconnect=true&serverTimezone=UTC");
            config.setUsername(username);
            config.setPassword(password);
            
            // ✅ FIX #6: Configurable connection pool settings
            config.setMaximumPoolSize(plugin.getConfig().getInt("mysql.pool.maximum-size", 10));
            config.setMinimumIdle(plugin.getConfig().getInt("mysql.pool.minimum-idle", 2));
            config.setConnectionTimeout(plugin.getConfig().getLong("mysql.pool.connection-timeout", 10000));
            config.setIdleTimeout(plugin.getConfig().getLong("mysql.pool.idle-timeout", 600000));
            config.setMaxLifetime(plugin.getConfig().getLong("mysql.pool.max-lifetime", 1800000));

            dataSource = new HikariDataSource(config);
            
            // Test connection
            try (Connection conn = dataSource.getConnection()) {
                plugin.getLogger().info("Connected to MySQL database successfully!");
            }

            setupTables();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Could not connect to MySQL database: " + e.getMessage());
            useMySQL = false;
            return false;
        }
    }

    /**
     * Creates necessary tables if they don't exist
     */
    private void setupTables() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            
            // Create entries table with UUID and soft-delete support
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + entriesTable + " (" +
                    "id VARCHAR(36) PRIMARY KEY, " +
                    "content LONGTEXT NOT NULL, " +
                    "author VARCHAR(36) NOT NULL, " +
                    "timestamp BIGINT NOT NULL, " +
                    "deleted TINYINT DEFAULT 0, " +
                    "created_at BIGINT NOT NULL, " +
                    "modified_at BIGINT NOT NULL, " +
                    "category VARCHAR(20)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;");
            
            // Migrate existing tables: add category column if it doesn't exist
            try {
                statement.executeUpdate("ALTER TABLE " + entriesTable + " ADD COLUMN IF NOT EXISTS category VARCHAR(20)");
            } catch (SQLException e) {
                // Column might already exist, ignore
                plugin.debug("Category column already exists or migration skipped");
            }
            
            // Create last seen table
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + lastSeenTable + " (" +
                    "uuid VARCHAR(36) PRIMARY KEY, " +
                    "timestamp BIGINT NOT NULL" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;");
            
            // Create cooldowns table
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + cooldownsTable + " (" +
                    "uuid VARCHAR(36) NOT NULL, " +
                    "reward_type VARCHAR(50) NOT NULL, " +
                    "timestamp BIGINT NOT NULL, " +
                    "PRIMARY KEY (uuid, reward_type)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;");
            
            // ✅ FIX: Create indexes for better query performance
            // Index on timestamp for sorting entries by date (DESC for newest first)
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_timestamp ON " + entriesTable + " (timestamp DESC)");
            
            // Index on deleted flag for faster filtering of active entries
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_deleted ON " + entriesTable + " (deleted)");
            
            // Composite index for efficient queries on non-deleted entries sorted by timestamp
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_deleted_timestamp ON " + entriesTable + " (deleted, timestamp DESC)");
            
            // Index on author for filtering by author
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_author ON " + entriesTable + " (author)");
            
            // Index on category for filtering by category
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_category ON " + entriesTable + " (category)");

            plugin.getLogger().info("Database tables and indexes initialized successfully!");
        }
    }

    /**
     * Gets a connection from the pool
     * @return Connection from HikariCP pool
     * @throws SQLException if connection cannot be obtained
     */
    private Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("Connection pool is not initialized");
        }
        
        try {
            return dataSource.getConnection();
        } catch (SQLException e) {
            // ✅ FIX: Better error handling for connection pool exhaustion
            if (e.getMessage() != null && e.getMessage().contains("Connection is not available")) {
                plugin.getLogger().warning("Connection pool exhausted! Consider increasing maximum-pool-size in config.yml");
                plugin.getLogger().warning("Current pool size: " + dataSource.getHikariPoolMXBean().getTotalConnections());
            }
            throw e;
        }
    }

    /**
     * Closes the database connection pool
     */
    public void disconnect() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("Disconnected from database.");
        }
    }

    /**
     * Loads all changelog entries from the database
     * @return List of changelog entries (excluding deleted ones)
     */
    public List<ChangelogEntry> loadEntries() {
        List<ChangelogEntry> entries = new ArrayList<>();
        
        if (!useMySQL) {
            return entries;
        }
        
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT * FROM " + entriesTable + " WHERE deleted = 0 ORDER BY created_at DESC")) {
            
            while (resultSet.next()) {
                String id = resultSet.getString("id");
                String content = resultSet.getString("content");
                String author = resultSet.getString("author");
                long timestamp = resultSet.getLong("timestamp");
                String category = resultSet.getString("category");
                
                ChangelogEntry entry = new ChangelogEntry(id, content, author, timestamp, category);
                entry.setCreatedAt(resultSet.getLong("created_at"));
                entry.setModifiedAt(resultSet.getLong("modified_at"));
                entries.add(entry);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error loading changelog entries from database", e);
        }
        
        return entries;
    }

    /**
     * Adds a new changelog entry to the database
     * @param entry The entry to add
     * @return true if successful
     */
    public boolean addEntry(ChangelogEntry entry) {
        if (!useMySQL) {
            return false;
        }
        
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO " + entriesTable + " (id, content, author, timestamp, deleted, created_at, modified_at, category) VALUES (?, ?, ?, ?, 0, ?, ?, ?)")) {
            
            statement.setString(1, entry.getId());
            statement.setString(2, entry.getContent());
            statement.setString(3, entry.getAuthor());
            statement.setLong(4, entry.getTimestamp());
            statement.setLong(5, entry.getCreatedAt());
            statement.setLong(6, entry.getModifiedAt());
            statement.setString(7, entry.getCategory());
            
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error adding changelog entry to database", e);
            return false;
        }
    }

    /**
     * Updates an existing changelog entry in the database
     * @param id The ID of the entry
     * @param content The new content
     * @return true if successful
     */
    public boolean updateEntry(String id, String content) {
        if (!useMySQL) {
            return false;
        }
        
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                "UPDATE " + entriesTable + " SET content = ?, modified_at = ? WHERE id = ? AND deleted = 0")) {
            
            statement.setString(1, content);
            statement.setLong(2, System.currentTimeMillis());
            statement.setString(3, id);
            
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error updating changelog entry in database", e);
            return false;
        }
    }

    /**
     * Soft-deletes a changelog entry in the database
     * @param id The ID of the entry to delete
     * @return true if successful
     */
    public boolean removeEntry(String id) {
        if (!useMySQL) {
            return false;
        }
        
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                "UPDATE " + entriesTable + " SET deleted = 1, modified_at = ? WHERE id = ?")) {
            
            statement.setLong(1, System.currentTimeMillis());
            statement.setString(2, id);
            
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error removing changelog entry from database", e);
            return false;
        }
    }

    /**
     * Loads all last seen timestamps from the database
     * @return A list of player UUIDs and their last seen timestamps
     */
    public List<PlayerLastSeen> loadLastSeen() {
        List<PlayerLastSeen> lastSeen = new ArrayList<>();
        
        if (!useMySQL) {
            return lastSeen;
        }
        
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT * FROM " + lastSeenTable)) {
            
            while (resultSet.next()) {
                String uuid = resultSet.getString("uuid");
                long timestamp = resultSet.getLong("timestamp");
                
                lastSeen.add(new PlayerLastSeen(uuid, timestamp));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error loading last seen data from database", e);
        }
        
        return lastSeen;
    }

    /**
     * Updates a player's last seen timestamp in the database
     * @param uuid The player's UUID
     * @param timestamp The timestamp
     * @return true if successful
     */
    public boolean updateLastSeen(String uuid, long timestamp) {
        if (!useMySQL) {
            return false;
        }
        
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO " + lastSeenTable + " (uuid, timestamp) VALUES (?, ?) " +
                "ON DUPLICATE KEY UPDATE timestamp = ?")) {
            
            statement.setString(1, uuid);
            statement.setLong(2, timestamp);
            statement.setLong(3, timestamp);
            
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error updating last seen in database", e);
            return false;
        }
    }

    /**
     * Gets the last seen timestamp for a player from the database
     * @param uuid The player's UUID
     * @return The timestamp or 0 if not found
     */
    public long getLastSeen(String uuid) {
        if (!useMySQL) {
            return 0;
        }
        
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                "SELECT timestamp FROM " + lastSeenTable + " WHERE uuid = ?")) {
            
            statement.setString(1, uuid);
            
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getLong("timestamp");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error getting last seen from database", e);
        }
        
        return 0;
    }

    /**
     * Loads all cooldowns from the database
     * @return Map of UUID -> Map of reward type -> timestamp
     */
    public Map<String, Map<String, Long>> loadCooldowns() {
        Map<String, Map<String, Long>> cooldowns = new HashMap<>();
        
        if (!useMySQL) {
            return cooldowns;
        }
        
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT * FROM " + cooldownsTable)) {
            
            while (resultSet.next()) {
                String uuid = resultSet.getString("uuid");
                String rewardType = resultSet.getString("reward_type");
                long timestamp = resultSet.getLong("timestamp");
                
                cooldowns.computeIfAbsent(uuid, k -> new HashMap<>()).put(rewardType, timestamp);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error loading cooldowns from database", e);
        }
        
        return cooldowns;
    }
    
    /**
     * Saves or updates a cooldown in the database
     * @param uuid The player's UUID
     * @param rewardType The reward type
     * @param timestamp The cooldown timestamp
     * @return true if successful
     */
    public boolean saveCooldown(String uuid, String rewardType, long timestamp) {
        if (!useMySQL) {
            return false;
        }
        
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO " + cooldownsTable + " (uuid, reward_type, timestamp) VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE timestamp = ?")) {
            
            statement.setString(1, uuid);
            statement.setString(2, rewardType);
            statement.setLong(3, timestamp);
            statement.setLong(4, timestamp);
            
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error saving cooldown to database", e);
            return false;
        }
    }
    
    /**
     * Gets a specific cooldown from the database
     * @param uuid The player's UUID
     * @param rewardType The reward type
     * @return The timestamp or 0 if not found
     */
    public long getCooldown(String uuid, String rewardType) {
        if (!useMySQL) {
            return 0;
        }
        
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                "SELECT timestamp FROM " + cooldownsTable + " WHERE uuid = ? AND reward_type = ?")) {
            
            statement.setString(1, uuid);
            statement.setString(2, rewardType);
            
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getLong("timestamp");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error getting cooldown from database", e);
        }
        
        return 0;
    }

    /**
     * Checks if MySQL is enabled
     * @return true if MySQL is enabled
     */
    public boolean isUsingMySQL() {
        return useMySQL;
    }
    
    /**
     * ✅ FIX #3: Garbage collection for soft-deleted entries (Critical)
     * Permanently deletes soft-deleted entries older than specified days
     * @param daysOld Entries older than this many days will be deleted
     * @return Number of deleted entries
     */
    public int pruneOldDeletedEntries(int daysOld) {
        if (!useMySQL) {
            return 0;
        }
        
        long cutoffTimestamp = System.currentTimeMillis() - (daysOld * 24L * 60L * 60L * 1000L);
        
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM " + entriesTable + " WHERE deleted = 1 AND modified_at < ?")) {
            
            statement.setLong(1, cutoffTimestamp);
            int deleted = statement.executeUpdate();
            
            if (deleted > 0) {
                plugin.getLogger().info("Pruned " + deleted + " old deleted entries (older than " + daysOld + " days)");
            }
            return deleted;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error pruning old deleted entries", e);
            return 0;
        }
    }

    /**
     * Simple class to store player UUID and last seen timestamp
     */
    public static class PlayerLastSeen {
        private final String uuid;
        private final long timestamp;
        
        public PlayerLastSeen(String uuid, long timestamp) {
            this.uuid = uuid;
            this.timestamp = timestamp;
        }
        
        public String getUuid() {
            return uuid;
        }
        
        public long getTimestamp() {
            return timestamp;
        }
    }
}
