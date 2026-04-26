package com.puffmc.changelog;

import com.puffmc.changelog.api.ChangelogAPI;
import com.puffmc.changelog.command.ChangelogCommand;
import com.puffmc.changelog.command.ChangelogTabCompleter;
import com.puffmc.changelog.listener.PlayerJoinListener;
import com.puffmc.changelog.manager.ChangelogManager;
import com.puffmc.changelog.util.CategoryDetector;
import com.puffmc.changelog.util.ConfigMigrator;
import com.puffmc.changelog.util.HealthChecker;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class ChangelogPlugin extends JavaPlugin {
    private File dataFile;
    private FileConfiguration dataConfig;
    private ChangelogManager changelogManager;
    private MessageManager messageManager;
    private RewardManager rewardManager;
    private DatabaseManager databaseManager;
    private LogManager logManager;
    private UpdateChecker updateChecker;
    private DiscordWebhook discordWebhook;
    private File discordConfigFile;
    private FileConfiguration discordConfig;
    private boolean debugMode = false;
    
    // New feature managers
    private CategoryDetector categoryDetector;
    private HealthChecker healthChecker;
    private ChangelogStatistics statistics;
    private ChangelogSearch search;
    private ChangelogPreview preview;
    private SuggestionManager suggestionManager;
    private BackupManager backupManager;
    private ExportManager exportManager;
    private ChangelogPlaceholderExpansion placeholderExpansion;
    private TagManager tagManager;
    private TemplateManager templateManager;
    private VoteManager voteManager;
    private VersionMilestone versionMilestone;
    private NotificationManager notificationManager;
    private ImpactLevel impactLevel;
    private ScheduledChangelog scheduledChangelog;
    private AnalyticsTracker analyticsTracker;
    private RssFeedGenerator rssFeedGenerator;
    private DiscordEnhancedEmbed discordEnhancedEmbed;

    @Override
    public void onEnable() {
        // Create config and data files
        saveDefaultConfig();
        new ConfigMigrator(this).migrate();
        setupDataFile();
        setupDiscordConfig();
        
        // Initialize LogManager
        logManager = new LogManager(this);
        logManager.info("ChangelogBook v" + getDescription().getVersion() + " starting...");
        
        // Initialize MessageManager with configured language
        String language = getConfig().getString("language", "en");
        messageManager = new MessageManager(this, language);
        logManager.info("Loaded language: " + language);
        
        // Initialize DatabaseManager — single shared instance used by all managers
        databaseManager = new DatabaseManager(this);
        // Pass the shared DatabaseManager so ChangelogManager never creates its own
        changelogManager = new ChangelogManager(this, databaseManager);
        rewardManager = new RewardManager(this);
        
        // Set MessageManager for ChangelogManager (for date translations)
        changelogManager.setMessageManager(messageManager);
        
        // Set database manager for reward manager (for cooldown persistence)
        rewardManager.setDatabaseManager(databaseManager);

        // Async database connection — loadData() is called only after successful connect
        if (getConfig().getBoolean("mysql.enabled", false)) {
            getServer().getScheduler().runTaskAsynchronously(this, () -> {
                if (!databaseManager.connect()) {
                    getLogger().warning("Failed to connect to database, falling back to YAML storage");
                    logManager.warning("Failed to connect to MySQL database, using YAML storage");
                    // Fall back to YAML on the main thread
                    getServer().getScheduler().runTask(this, () -> {
                        changelogManager.loadData();
                        rewardManager.loadCooldowns();
                    });
                } else {
                    logManager.info("Successfully connected to MySQL database");
                    // Load data from database after successful connection
                    changelogManager.loadData();
                    rewardManager.loadCooldowns();
                }
            });
        } else {
            // YAML mode — connect (no-op) then load immediately
            databaseManager.connect();
            changelogManager.loadData();
            rewardManager.loadCooldowns();
        }
        
        // Initialize UpdateChecker
        updateChecker = new UpdateChecker(this, getConfig(), logManager);
        
        // Initialize Discord Webhook
        discordWebhook = new DiscordWebhook(this);
        if (discordWebhook.isEnabled()) {
            logManager.info("Discord webhook integration enabled");
        }
        
        // Initialize new feature managers
        logManager.info("Initializing feature managers...");
        
        categoryDetector = new CategoryDetector(this);
        healthChecker = new HealthChecker(this);
        statistics = new ChangelogStatistics(this, changelogManager);
        search = new ChangelogSearch(changelogManager);
        preview = new ChangelogPreview(this, changelogManager);
        suggestionManager = new SuggestionManager(this);
        backupManager = new BackupManager(this);
        exportManager = new ExportManager(this, changelogManager);
        tagManager = new TagManager(this);
        templateManager = new TemplateManager(this);
        voteManager = new VoteManager(this);
        versionMilestone = new VersionMilestone(this);
        notificationManager = new NotificationManager(this);
        impactLevel = new ImpactLevel(this);
        scheduledChangelog = new ScheduledChangelog(this);
        analyticsTracker = new AnalyticsTracker(this);
        rssFeedGenerator = new RssFeedGenerator(this, changelogManager);
        discordEnhancedEmbed = new DiscordEnhancedEmbed(this);

        // Wire TagManager into ChangelogSearch so tag: filter works
        search.setTagManager(tagManager);
        
        // Initialize Public API
        ChangelogAPI.initialize(this, changelogManager);
        logManager.info("Public API initialized");
        
        // Initialize PlaceholderAPI expansion if available
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            placeholderExpansion = new ChangelogPlaceholderExpansion(this, changelogManager);
            if (placeholderExpansion.register()) {
                logManager.info("PlaceholderAPI expansion registered");
            }
        }
        
        // Start scheduled tasks
        backupManager.startAutoBackup();
        scheduledChangelog.startScheduler();
        analyticsTracker.startAutoSave();
        
        logManager.info("All feature managers initialized successfully");
        
        // Schedule periodic update checks (first check fires after 30 seconds / 600 ticks)
        long checkIntervalHours = getConfig().getLong("update-checker.check-interval-hours", 6);
        long checkIntervalTicks = checkIntervalHours * 72000L; // Convert hours to ticks (1 hour = 72000 ticks)
        
        new BukkitRunnable() {
            @Override
            public void run() {
                updateChecker.checkForUpdates();
            }
        }.runTaskTimerAsynchronously(this, 600L, checkIntervalTicks);
        
        // ✅ FIX #3: Auto-prune old deleted entries (garbage collection)
        int pruneDays = getConfig().getInt("database.prune-deleted-after-days", 30);
        boolean pruneOnStartup = getConfig().getBoolean("database.prune-on-startup", true);
        
        if (pruneDays > 0 && getConfig().getBoolean("mysql.enabled", false)) {
            // Prune on startup if enabled
            if (pruneOnStartup) {
                getServer().getScheduler().runTaskLaterAsynchronously(this, () -> {
                    int pruned = databaseManager.pruneOldDeletedEntries(pruneDays);
                    if (pruned > 0) {
                        logManager.info("Startup prune: Removed " + pruned + " old deleted entries");
                    }
                }, 1200L); // 1 minute after startup
            }
            
            // Schedule auto-prune every 24 hours
            new BukkitRunnable() {
                @Override
                public void run() {
                    int pruned = databaseManager.pruneOldDeletedEntries(pruneDays);
                    if (pruned > 0) {
                        logManager.info("Auto-prune: Removed " + pruned + " old deleted entries");
                    }
                }
            }.runTaskTimerAsynchronously(this, 72000L, 1728000L); // 1h start delay, 24h interval
            
            logManager.info("Auto-prune enabled: Deleted entries older than " + pruneDays + " days will be removed");
        }
        
        // Register commands
        ChangelogCommand command = new ChangelogCommand(this, changelogManager, messageManager, rewardManager, updateChecker);
        getCommand("changelog").setExecutor(command);
        getCommand("changelog").setTabCompleter(new ChangelogTabCompleter(this, changelogManager));
        
        // Register events
        getServer().getPluginManager().registerEvents(
                new PlayerJoinListener(this, changelogManager, messageManager, updateChecker), this);
        
        getLogger().info("ChangelogBook v" + getDescription().getVersion() + " has been enabled!");
    }

    @Override
    public void onDisable() {
        if (logManager != null) {
            logManager.info("ChangelogBook shutting down...");
        }
        
        // Stop scheduled tasks
        if (backupManager != null) {
            backupManager.stopAutoBackup();
        }
        if (scheduledChangelog != null) {
            scheduledChangelog.stopScheduler();
        }
        if (analyticsTracker != null) {
            analyticsTracker.stopAutoSave();
        }
        if (notificationManager != null) {
            notificationManager.cleanup();
        }
        
        // Unregister PlaceholderAPI expansion
        if (placeholderExpansion != null) {
            placeholderExpansion.unregister();
        }
        
        if (changelogManager != null) {
            changelogManager.shutdown();
        }
        // Note: databaseManager.disconnect() is already called inside changelogManager.shutdown()

        if (logManager != null) {
            logManager.info("ChangelogBook disabled successfully");
        }
        getLogger().info("ChangelogBook has been disabled!");
    }
    
    /**
     * Sets up and loads the discord.yml file, extracting the default from the JAR if absent.
     */
    private void setupDiscordConfig() {
        discordConfigFile = new File(getDataFolder(), "discord.yml");
        if (!discordConfigFile.exists()) {
            try {
                InputStream in = getResource("discord.yml");
                if (in != null) {
                    discordConfigFile.getParentFile().mkdirs();
                    Files.copy(in, discordConfigFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    in.close();
                }
            } catch (IOException e) {
                getLogger().warning("Could not extract discord.yml: " + e.getMessage());
            }
        }
        discordConfig = YamlConfiguration.loadConfiguration(discordConfigFile);
    }

    /**
     * Gets the discord.yml configuration
     * @return FileConfiguration for discord.yml
     */
    public FileConfiguration getDiscordConfig() {
        return discordConfig;
    }

    /**
     * Sets up the data.yml file
     */
    private void setupDataFile() {
        dataFile = new File(getDataFolder(), "data.yml");
        
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                getLogger().severe("Could not create data.yml file!");
                return;
            }
        }
        
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }
    
    /**
     * Reloads the configuration from disk
     */
    @Override
    public void reloadConfig() {
        super.reloadConfig();
        setupDataFile();
        
        // Reload message manager
        if (messageManager != null) {
            messageManager.reload();
        }
        // Reload discord config
        setupDiscordConfig();
    }
    
    /**
     * Saves the data config to file
     */
    public void saveDataConfig() {
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            getLogger().severe("Could not save data.yml file!");
        }
    }
    
    /**
     * Gets the data config
     * @return FileConfiguration the data config
     */
    public FileConfiguration getDataConfig() {
        return dataConfig;
    }

    /**
     * Gets the message manager
     * @return MessageManager instance
     */
    public MessageManager getMessageManager() {
        return messageManager;
    }

    /**
     * Gets the reward manager
     * @return RewardManager instance
     */
    public RewardManager getRewardManager() {
        return rewardManager;
    }

    /**
     * Gets the changelog manager
     * @return ChangelogManager instance
     */
    public ChangelogManager getChangelogManager() {
        return changelogManager;
    }
    
    /**
     * Gets the log manager
     * @return LogManager instance
     */
    public LogManager getLogManager() {
        return logManager;
    }

    /**
     * Gets the database manager
     * @return DatabaseManager instance
     */
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    /**
     * Gets the Discord webhook manager
     * @return DiscordWebhook instance
     */
    public DiscordWebhook getDiscordWebhook() {
        return discordWebhook;
    }

    /**
     * Gets the update checker
     * @return UpdateChecker instance
     */
    public UpdateChecker getUpdateChecker() {
        return updateChecker;
    }

    // New feature manager getters
    
    public CategoryDetector getCategoryDetector() {
        return categoryDetector;
    }
    
    public HealthChecker getHealthChecker() {
        return healthChecker;
    }
    
    public ChangelogStatistics getStatistics() {
        return statistics;
    }
    
    public ChangelogSearch getSearch() {
        return search;
    }
    
    public ChangelogPreview getPreview() {
        return preview;
    }
    
    public SuggestionManager getSuggestionManager() {
        return suggestionManager;
    }
    
    public BackupManager getBackupManager() {
        return backupManager;
    }
    
    public ExportManager getExportManager() {
        return exportManager;
    }
    
    public TagManager getTagManager() {
        return tagManager;
    }
    
    public TemplateManager getTemplateManager() {
        return templateManager;
    }
    
    public VoteManager getVoteManager() {
        return voteManager;
    }
    
    public VersionMilestone getVersionMilestone() {
        return versionMilestone;
    }
    
    public NotificationManager getNotificationManager() {
        return notificationManager;
    }
    
    public ImpactLevel getImpactLevel() {
        return impactLevel;
    }
    
    public ScheduledChangelog getScheduledChangelog() {
        return scheduledChangelog;
    }
    
    public AnalyticsTracker getAnalyticsTracker() {
        return analyticsTracker;
    }
    
    public RssFeedGenerator getRssFeedGenerator() {
        return rssFeedGenerator;
    }
    
    public DiscordEnhancedEmbed getDiscordEnhancedEmbed() {
        return discordEnhancedEmbed;
    }

    /**
     * Sets debug mode
     * @param debug true to enable debug mode
     */
    public void setDebugMode(boolean debug) {
        this.debugMode = debug;
        if (debug) {
            getLogger().info("Debug mode enabled");
        } else {
            getLogger().info("Debug mode disabled");
        }
    }

    /**
     * Checks if debug mode is enabled
     * @return true if debug mode is on
     */
    public boolean isDebugMode() {
        return debugMode;
    }

    /**
     * Logs a debug message if debug mode is enabled
     * @param message The message to log
     */
    public void debug(String message) {
        if (debugMode) {
            getLogger().info("[DEBUG] " + message);
        }
    }
}
