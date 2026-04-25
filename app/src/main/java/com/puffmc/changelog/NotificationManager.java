package com.puffmc.changelog;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages notifications (Title, ActionBar, BossBar) for changelog updates
 */
public class NotificationManager {
    private final ChangelogPlugin plugin;
    private final Map<UUID, BossBar> activeBossBars;

    public NotificationManager(ChangelogPlugin plugin) {
        this.plugin = plugin;
        this.activeBossBars = new HashMap<>();
    }

    /**
     * Sends a title notification to a player
     * @param player Player
     * @param title Title text
     * @param subtitle Subtitle text
     * @param fadeIn Fade in time (ticks)
     * @param stay Stay time (ticks)
     * @param fadeOut Fade out time (ticks)
     */
    public void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        player.sendTitle(
                colorize(title),
                colorize(subtitle),
                fadeIn, stay, fadeOut
        );
    }

    /**
     * Sends an action bar notification to a player
     * @param player Player
     * @param message Message text
     */
    public void sendActionBar(Player player, String message) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(colorize(message)));
    }

    /**
     * Shows a boss bar notification to a player
     * @param player Player
     * @param message Message text
     * @param color Bar color
     * @param style Bar style
     * @param duration Duration in seconds
     */
    public void showBossBar(Player player, String message, BarColor color, BarStyle style, int duration) {
        removeBossBar(player);

        BossBar bossBar = Bukkit.createBossBar(
                colorize(message),
                color,
                style
        );
        bossBar.addPlayer(player);
        bossBar.setProgress(1.0);

        activeBossBars.put(player.getUniqueId(), bossBar);

        new BukkitRunnable() {
            int ticks = 0;
            final int totalTicks = duration * 20;

            @Override
            public void run() {
                ticks++;
                double progress = 1.0 - ((double) ticks / totalTicks);
                bossBar.setProgress(Math.max(0, progress));

                if (ticks >= totalTicks) {
                    removeBossBar(player);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Removes active boss bar from player
     * @param player Player
     */
    public void removeBossBar(Player player) {
        BossBar existing = activeBossBars.remove(player.getUniqueId());
        if (existing != null) {
            existing.removeAll();
        }
    }

    /**
     * Notifies player about new changelog entry
     * @param player Player
     * @param entryId Entry ID
     * @param category Category
     */
    public void notifyNewEntry(Player player, String entryId, String category) {
        String notifType = plugin.getConfig().getString("notifications.type", "actionbar");

        switch (notifType.toLowerCase()) {
            case "title":
                String title = plugin.getConfig().getString("notifications.title.text", "&6New Changelog!");
                String subtitle = plugin.getConfig().getString("notifications.title.subtitle", "&7Category: %category%")
                        .replace("%category%", category)
                        .replace("%id%", entryId);
                sendTitle(player, title, subtitle, 10, 40, 10);
                break;

            case "bossbar":
                String bossbarText = plugin.getConfig().getString("notifications.bossbar.text", "&6New Changelog Entry")
                        .replace("%category%", category)
                        .replace("%id%", entryId);
                String colorStr = plugin.getConfig().getString("notifications.bossbar.color", "BLUE");
                BarColor barColor = parseBarColor(colorStr);
                int duration = plugin.getConfig().getInt("notifications.bossbar.duration", 10);
                showBossBar(player, bossbarText, barColor, BarStyle.SOLID, duration);
                break;

            case "actionbar":
            default:
                String actionbarText = plugin.getConfig().getString("notifications.actionbar.text", "&6New changelog: &f%category%")
                        .replace("%category%", category)
                        .replace("%id%", entryId);
                sendActionBar(player, actionbarText);
                break;
        }
    }

    /**
     * Broadcasts notification to all online players
     * @param entryId Entry ID
     * @param category Category
     */
    public void broadcastNewEntry(String entryId, String category) {
        if (!plugin.getConfig().getBoolean("notifications.enabled", true)) {
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("changelogbook.notify")) {
                notifyNewEntry(player, entryId, category);
            }
        }
    }

    /**
     * Cleans up all active boss bars
     */
    public void cleanup() {
        for (BossBar bossBar : activeBossBars.values()) {
            bossBar.removeAll();
        }
        activeBossBars.clear();
    }

    private String colorize(String text) {
        return text.replace("&", "§");
    }

    private BarColor parseBarColor(String color) {
        try {
            return BarColor.valueOf(color.toUpperCase());
        } catch (IllegalArgumentException e) {
            return BarColor.BLUE;
        }
    }
}
