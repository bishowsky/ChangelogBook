package com.puffmc.changelog.listener;

import com.puffmc.changelog.ChangelogPlugin;
import com.puffmc.changelog.MessageManager;
import com.puffmc.changelog.manager.ChangelogManager;
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

    public PlayerJoinListener(ChangelogPlugin plugin, ChangelogManager changelogManager, MessageManager messageManager) {
        this.plugin = plugin;
        this.changelogManager = changelogManager;
        this.messageManager = messageManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Delay notification to allow proper plugin initialization
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
}
