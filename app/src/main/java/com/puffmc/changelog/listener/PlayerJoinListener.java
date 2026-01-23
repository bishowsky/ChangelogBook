package com.puffmc.changelog.listener;

import com.puffmc.changelog.ChangelogPlugin;
import com.puffmc.changelog.MessageManager;
import com.puffmc.changelog.UpdateChecker;
import com.puffmc.changelog.manager.ChangelogManager;
import com.puffmc.changelog.util.ComponentUtil;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;

public class PlayerJoinListener implements Listener {
    private final ChangelogPlugin plugin;
    private final ChangelogManager changelogManager;
    private final MessageManager messageManager;
    private final UpdateChecker updateChecker;

    public PlayerJoinListener(ChangelogPlugin plugin, ChangelogManager changelogManager, MessageManager messageManager, UpdateChecker updateChecker) {
        this.plugin = plugin;
        this.changelogManager = changelogManager;
        this.messageManager = messageManager;
        this.updateChecker = updateChecker;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Check if auto-open is enabled
        boolean autoOpenEnabled = plugin.getConfig().getBoolean("auto-open.enabled", false);
        int autoOpenDelaySeconds = plugin.getConfig().getInt("auto-open.delay-seconds", 3);
        long autoOpenDelayTicks = autoOpenDelaySeconds * 20L; // Convert seconds to ticks
        
        plugin.debug("Player " + player.getName() + " joined. Auto-open: " + autoOpenEnabled);
        
        if (autoOpenEnabled) {
            // Auto-open mode: open book directly if there are new entries
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (player.isOnline()) {
                        int newChanges = changelogManager.getNewEntriesCount(player);
                        plugin.debug(player.getName() + " has " + newChanges + " new changelog entries");
                        
                        if (newChanges > 0) {
                            // Open the changelog book directly using the command handler
                            // This bypasses permission checks and opens the book directly
                            plugin.debug("Auto-opening changelog book for " + player.getName());
                            plugin.getCommand("changelog").getExecutor()
                                .onCommand(player, plugin.getCommand("changelog"), "changelog", new String[]{"show"});
                        }
                    }
                }
            }.runTaskLater(plugin, autoOpenDelayTicks);
        } else {
            // Notification mode: show clickable message
            long notificationDelay = plugin.getConfig().getLong("notification-delay", 60L);
            
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (player.isOnline()) {
                        int newChanges = changelogManager.getNewEntriesCount(player);
                        
                        if (newChanges > 0) {
                            // First message
                            Map<String, String> placeholders = new HashMap<>();
                            placeholders.put("count", String.valueOf(newChanges));
                            String message = messageManager.getMessage("messages.new_entries_count", placeholders);
                            player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
                            
                            // Clickable message
                            TextComponent clickableMessage = new TextComponent(
                                    ChatColor.GOLD + "/changelog " +
                                    ChatColor.GRAY + "- " +
                                    ChatColor.GOLD + messageManager.getMessage("messages.click_to_view"));
                            
                            clickableMessage.setHoverEvent(new HoverEvent(
                                    HoverEvent.Action.SHOW_TEXT, 
                                    new ComponentBuilder(messageManager.getMessage("messages.click_to_view")).create()));
                            
                            clickableMessage.setClickEvent(new ClickEvent(
                                    ClickEvent.Action.RUN_COMMAND, 
                                    "/changelog"));
                            
                            player.spigot().sendMessage(clickableMessage);
                        }
                    }
                }
            }.runTaskLater(plugin, notificationDelay);
        }
        
        // Check for plugin updates (separate from changelog notifications)
        if (plugin.getConfig().getBoolean("update-checker.notify-on-join", true)) {
            // Delay update notification by 10 seconds (200 ticks) after join
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (player.isOnline() && (player.hasPermission("changelogbook.update.notify") || player.isOp())) {
                        if (updateChecker.isUpdateAvailable()) {
                            TextComponent updateNotification = ComponentUtil.createUpdateNotification(
                                messageManager,
                                updateChecker.getCurrentVersion(),
                                updateChecker.getLatestVersion(),
                                updateChecker.getDownloadUrl()
                            );
                            player.spigot().sendMessage(updateNotification);
                        }
                    }
                }
            }.runTaskLater(plugin, 200L); // 10 seconds delay
        }
    }
}
