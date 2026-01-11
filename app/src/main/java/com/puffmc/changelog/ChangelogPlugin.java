package com.puffmc.changelog;

import com.puffmc.changelog.command.ChangelogCommand;
import com.puffmc.changelog.command.ChangelogTabCompleter;
import com.puffmc.changelog.listener.PlayerJoinListener;
import com.puffmc.changelog.manager.ChangelogManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

public class ChangelogPlugin extends JavaPlugin {
    private File configFile;
    private File dataFile;
    private FileConfiguration dataConfig;
    private ChangelogManager changelogManager;
    private MessageManager messageManager;
    private RewardManager rewardManager;
    private DatabaseManager databaseManager;
    private LogManager logManager;
    private boolean debugMode = false;

    @Override
    public void onEnable() {
        // Create config and data files
        saveDefaultConfig();
        setupDataFile();
        
        // Initialize LogManager
        logManager = new LogManager(this);
        logManager.info("ChangelogBook v" + getDescription().getVersion() + " starting...");
        
        // Initialize MessageManager with configured language
        String language = getConfig().getString("language", "en");
        messageManager = new MessageManager(this, language);
        logManager.info("Loaded language: " + language);
        
        // Initialize DatabaseManager
        databaseManager = new DatabaseManager(this);
        if (!databaseManager.connect()) {
            getLogger().warning("Failed to connect to database, falling back to YAML storage");
            logManager.warning("Failed to connect to MySQL database, using YAML storage");
        } else {
            logManager.info("Successfully connected to MySQL database");
        }
        
        // Initialize managers
        changelogManager = new ChangelogManager(this);
        rewardManager = new RewardManager(this);
        
        // Set database manager for reward manager (for cooldown persistence)
        rewardManager.setDatabaseManager(databaseManager);
        
        // Register commands
        ChangelogCommand command = new ChangelogCommand(this, changelogManager, messageManager, rewardManager);
        getCommand("changelog").setExecutor(command);
        getCommand("changelog").setTabCompleter(new ChangelogTabCompleter(this, changelogManager));
        
        // Register events
        getServer().getPluginManager().registerEvents(
                new PlayerJoinListener(this, changelogManager, messageManager), this);
        
        getLogger().info("ChangelogBook v" + getDescription().getVersion() + " has been enabled!");
    }

    @Override
    public void onDisable() {
        if (logManager != null) {
            logManager.info("ChangelogBook shutting down...");
        }
        
        if (changelogManager != null) {
            changelogManager.shutdown();
        }
        if (databaseManager != null) {
            databaseManager.disconnect();
        }
        
        if (logManager != null) {
            logManager.info("ChangelogBook disabled successfully");
        }
        getLogger().info("ChangelogBook has been disabled!");
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
