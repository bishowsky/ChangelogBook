package com.puffmc.changelog;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class RewardManager {
    private final ChangelogPlugin plugin;
    // ✅ FIX: Use Caffeine Cache to prevent memory leak from inactive players
    // Cache automatically expires entries after 30 days of no access
    private final Cache<String, Map<String, Long>> playerCooldowns = Caffeine.newBuilder()
            .expireAfterAccess(30, TimeUnit.DAYS)
            .maximumSize(10000) // Limit to 10,000 players
            .build();
    private DatabaseManager databaseManager;

    public RewardManager(ChangelogPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Sets the database manager instance
     * @param databaseManager The database manager
     */
    public void setDatabaseManager(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
        loadCooldowns();
    }
    
    /**
     * Validates player name to prevent command injection
     * Only allows alphanumeric characters, underscores (valid Minecraft names)
     * @param playerName Player name to validate
     * @return true if safe, false if potentially malicious
     */
    private boolean isPlayerNameSafe(String playerName) {
        if (playerName == null || playerName.isEmpty()) {
            return false;
        }
        // Minecraft player names: 3-16 chars, alphanumeric + underscore only
        return playerName.matches("^[a-zA-Z0-9_]{3,16}$");
    }

    /**
     * Checks if a player can claim a specific reward type
     * @param player The player
     * @param rewardType The reward type
     * @return true if cooldown has passed, false otherwise
     */
    public boolean canClaimReward(Player player, String rewardType) {
        String uuid = player.getUniqueId().toString();
        
        Map<String, Long> cooldowns = playerCooldowns.getIfPresent(uuid);
        if (cooldowns == null) {
            return true;
        }

        if (!cooldowns.containsKey(rewardType)) {
            return true;
        }

        long lastClaimTime = cooldowns.get(rewardType);
        long cooldownMillis = getCooldownHours(rewardType) * 3600000L;
        long currentTime = System.currentTimeMillis();

        return (currentTime - lastClaimTime) >= cooldownMillis;
    }

    /**
     * Gets remaining cooldown time in hours
     * @param player The player
     * @param rewardType The reward type
     * @return Hours remaining, 0 if no cooldown
     */
    public long getRemainingCooldownHours(Player player, String rewardType) {
        String uuid = player.getUniqueId().toString();
        
        Map<String, Long> cooldowns = playerCooldowns.getIfPresent(uuid);
        if (cooldowns == null || !cooldowns.containsKey(rewardType)) {
            return 0;
        }

        long lastClaimTime = cooldowns.get(rewardType);
        long cooldownMillis = getCooldownHours(rewardType) * 3600000L;
        long currentTime = System.currentTimeMillis();
        long elapsed = currentTime - lastClaimTime;

        if (elapsed >= cooldownMillis) {
            return 0;
        }

        return (cooldownMillis - elapsed) / 3600000L + 1;
    }

    /**
     * Claims a reward for the player
     * @param player The player
     * @param rewardType The reward type
     * @return true if reward was claimed, false if on cooldown
     */
    public boolean claimReward(Player player, String rewardType) {
        if (!canClaimReward(player, rewardType)) {
            plugin.debug("Player " + player.getName() + " cannot claim " + rewardType + " reward (cooldown active)");
            return false;
        }
        
        // Validate player name to prevent command injection
        String playerName = player.getName();
        if (!isPlayerNameSafe(playerName)) {
            plugin.getLogger().warning("SECURITY: Blocked potential command injection attempt from player: " + playerName);
            plugin.getLogger().warning("Player name contains invalid characters - reward claim denied");
            return false;
        }

        // Execute the reward commands (supports both single command and list)
        var commands = getRewardCommands(rewardType);
        if (!commands.isEmpty()) {
            for (String command : commands) {
                String finalCommand = command.replace("%player%", playerName);
                plugin.debug("Executing reward command for " + playerName + ": " + finalCommand);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand);
            }
        }

        // Update cooldown
        String uuid = player.getUniqueId().toString();
        long timestamp = System.currentTimeMillis();
        
        Map<String, Long> cooldowns = playerCooldowns.getIfPresent(uuid);
        if (cooldowns == null) {
            cooldowns = new HashMap<>();
            playerCooldowns.put(uuid, cooldowns);
        }
        cooldowns.put(rewardType, timestamp);
        plugin.debug("Set cooldown for " + player.getName() + " on " + rewardType + " reward");
        
        // Save to database/YAML
        saveCooldown(uuid, rewardType, timestamp);

        return true;
    }

    /**
     * Gets the cooldown in hours for a reward type
     * @param rewardType The reward type
     * @return Cooldown in hours
     */
    public long getCooldownHours(String rewardType) {
        ConfigurationSection rewards = plugin.getConfig().getConfigurationSection("rewards.types." + rewardType);
        if (rewards != null) {
            return rewards.getLong("cooldown-hours", 6);
        }
        return 6;
    }

    /**
     * Gets the chance percentage for a reward type
     * @param rewardType The reward type
     * @return Chance (0-100)
     */
    public int getRewardChance(String rewardType) {
        ConfigurationSection rewards = plugin.getConfig().getConfigurationSection("rewards.types." + rewardType);
        if (rewards != null) {
            return rewards.getInt("chance", 10);
        }
        return 10;
    }

    /**
     * Gets the max days for a reward type
     * @param rewardType The reward type
     * @return Max days
     */
    public int getMaxDays(String rewardType) {
        ConfigurationSection rewards = plugin.getConfig().getConfigurationSection("rewards.types." + rewardType);
        if (rewards != null) {
            return rewards.getInt("max-days", 7);
        }
        return 7;
    }

    /**
     * Gets the command to execute for a reward type (legacy support)
     * @param rewardType The reward type
     * @return The command string
     * @deprecated Use getRewardCommands() instead for multiple commands support
     */
    @Deprecated
    public String getRewardCommand(String rewardType) {
        var commands = getRewardCommands(rewardType);
        return commands.isEmpty() ? "" : commands.get(0);
    }

    /**
     * Gets the commands to execute for a reward type
     * Supports both single command (string) and multiple commands (list)
     * @param rewardType The reward type
     * @return List of commands to execute
     */
    public java.util.List<String> getRewardCommands(String rewardType) {
        ConfigurationSection rewards = plugin.getConfig().getConfigurationSection("rewards.types." + rewardType);
        java.util.List<String> commandList = new java.util.ArrayList<>();
        
        if (rewards != null) {
            // Try to get as list first (new format)
            if (rewards.isList("commands")) {
                commandList.addAll(rewards.getStringList("commands"));
            }
            // Fallback to single command (legacy format)
            else if (rewards.isString("command")) {
                String cmd = rewards.getString("command", "");
                if (!cmd.isEmpty()) {
                    commandList.add(cmd);
                }
            }
            // Also check for 'commands' as string (edge case)
            else if (rewards.isString("commands")) {
                String cmd = rewards.getString("commands", "");
                if (!cmd.isEmpty()) {
                    commandList.add(cmd);
                }
            }
        }
        
        return commandList;
    }

    /**
     * Checks if a reward type is enabled
     * @param rewardType The reward type
     * @return true if enabled
     */
    public boolean isRewardEnabled(String rewardType) {
        ConfigurationSection rewards = plugin.getConfig().getConfigurationSection("rewards.types." + rewardType);
        if (rewards != null) {
            return rewards.getBoolean("enabled", true);
        }
        return false;
    }

    /**
     * Loads cooldowns from database or YAML
     */
    public void loadCooldowns() {
        playerCooldowns.invalidateAll();
        
        if (databaseManager != null && databaseManager.isUsingMySQL()) {
            // Load from MySQL
            Map<String, Map<String, Long>> loaded = databaseManager.loadCooldowns();
            playerCooldowns.putAll(loaded);
            plugin.getLogger().info("Loaded " + loaded.size() + " reward cooldowns from database");
        } else {
            // Load from YAML
            loadCooldownsFromYaml();
        }
    }
    
    /**
     * Loads cooldowns from YAML file
     */
    private void loadCooldownsFromYaml() {
        var data = plugin.getDataConfig();
        var cooldownsSection = data.getConfigurationSection("cooldowns");
        
        int count = 0;
        if (cooldownsSection != null) {
            for (String uuid : cooldownsSection.getKeys(false)) {
                var playerSection = cooldownsSection.getConfigurationSection(uuid);
                if (playerSection != null) {
                    Map<String, Long> rewards = new HashMap<>();
                    for (String rewardType : playerSection.getKeys(false)) {
                        rewards.put(rewardType, playerSection.getLong(rewardType));
                    }
                    playerCooldowns.put(uuid, rewards);
                    count++;
                }
            }
            plugin.getLogger().info("Loaded " + count + " reward cooldowns from YAML");
        }
    }
    
    /**
     * Saves a single cooldown to database or YAML
     */
    private void saveCooldown(String uuid, String rewardType, long timestamp) {
        if (databaseManager != null && databaseManager.isUsingMySQL()) {
            // Save to MySQL asynchronously
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    databaseManager.saveCooldown(uuid, rewardType, timestamp);
                } catch (Exception e) {
                    plugin.getLogger().severe("Error saving cooldown to database: " + e.getMessage());
                }
            });
        } else {
            // Save to YAML asynchronously
            Bukkit.getScheduler().runTaskAsynchronously(plugin, this::saveCooldownsToYaml);
        }
    }

    /**
     * Saves all cooldowns to YAML file
     */
    private void saveCooldownsToYaml() {
        var data = plugin.getDataConfig();
        data.set("cooldowns", null);
        
        for (var entry : playerCooldowns.asMap().entrySet()) {
            String uuid = entry.getKey();
            for (var reward : entry.getValue().entrySet()) {
                data.set("cooldowns." + uuid + "." + reward.getKey(), reward.getValue());
            }
        }
        
        plugin.saveDataConfig();
    }
}
