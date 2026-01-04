package com.puffmc.changelog;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RewardManager {
    private final ChangelogPlugin plugin;
    private final Map<UUID, Map<String, Long>> playerCooldowns = new HashMap<>();

    public RewardManager(ChangelogPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Checks if a player can claim a specific reward type
     * @param player The player
     * @param rewardType The reward type
     * @return true if cooldown has passed, false otherwise
     */
    public boolean canClaimReward(Player player, String rewardType) {
        UUID uuid = player.getUniqueId();
        
        if (!playerCooldowns.containsKey(uuid)) {
            return true;
        }

        Map<String, Long> cooldowns = playerCooldowns.get(uuid);
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
        UUID uuid = player.getUniqueId();
        
        if (!playerCooldowns.containsKey(uuid) || !playerCooldowns.get(uuid).containsKey(rewardType)) {
            return 0;
        }

        long lastClaimTime = playerCooldowns.get(uuid).get(rewardType);
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
            return false;
        }

        // Execute the reward command
        String command = getRewardCommand(rewardType);
        if (command != null && !command.isEmpty()) {
            String finalCommand = command.replace("%player%", player.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand);
        }

        // Update cooldown
        UUID uuid = player.getUniqueId();
        if (!playerCooldowns.containsKey(uuid)) {
            playerCooldowns.put(uuid, new HashMap<>());
        }
        playerCooldowns.get(uuid).put(rewardType, System.currentTimeMillis());

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
     * Gets the command to execute for a reward type
     * @param rewardType The reward type
     * @return The command string
     */
    public String getRewardCommand(String rewardType) {
        ConfigurationSection rewards = plugin.getConfig().getConfigurationSection("rewards.types." + rewardType);
        if (rewards != null) {
            return rewards.getString("command", "");
        }
        return "";
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
     * Loads cooldowns from database (when implemented)
     */
    public void loadCooldowns() {
        // TODO: Implement database loading of cooldowns
    }

    /**
     * Saves cooldowns to database (when implemented)
     */
    public void saveCooldowns() {
        // TODO: Implement database saving of cooldowns
    }
}
