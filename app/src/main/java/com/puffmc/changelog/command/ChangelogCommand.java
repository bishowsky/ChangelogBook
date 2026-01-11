package com.puffmc.changelog.command;

import com.puffmc.changelog.ChangelogEntry;
import com.puffmc.changelog.ChangelogPlugin;
import com.puffmc.changelog.MessageManager;
import com.puffmc.changelog.RewardManager;
import com.puffmc.changelog.manager.ChangelogManager;
import com.puffmc.changelog.util.ColorUtil;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class ChangelogCommand implements CommandExecutor {
    private final ChangelogPlugin plugin;
    private final ChangelogManager changelogManager;
    private final MessageManager messageManager;
    private final RewardManager rewardManager;

    public ChangelogCommand(ChangelogPlugin plugin, ChangelogManager changelogManager, MessageManager messageManager, RewardManager rewardManager) {
        this.plugin = plugin;
        this.changelogManager = changelogManager;
        this.messageManager = messageManager;
        this.rewardManager = rewardManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender.hasPermission("changelogbook.admin")) {
                showHelpMenu(sender);
            } else if (sender instanceof Player) {
                Player player = (Player) sender;
                showChangelogBook(player, false);
                changelogManager.updateLastSeen(player);
            } else {
                sender.sendMessage(messageManager.getMessage("errors.no_permission"));
            }
            return true;
        }

        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "add":
                if (!sender.hasPermission("changelogbook.admin")) {
                    sender.sendMessage(messageManager.getMessage("errors.no_permission"));
                    return true;
                }
                return handleAddCommand(sender, args);
                
            case "edit":
                if (!sender.hasPermission("changelogbook.admin")) {
                    sender.sendMessage(messageManager.getMessage("errors.no_permission"));
                    return true;
                }
                return handleEditCommand(sender, args);
                
            case "delete":
                if (!sender.hasPermission("changelogbook.admin")) {
                    sender.sendMessage(messageManager.getMessage("errors.no_permission"));
                    return true;
                }
                return handleDeleteCommand(sender, args);
                
            case "list":
                if (!sender.hasPermission("changelogbook.admin")) {
                    sender.sendMessage(messageManager.getMessage("errors.no_permission"));
                    return true;
                }
                return handleListCommand(sender);
                
            case "reload":
                if (!sender.hasPermission("changelogbook.admin")) {
                    sender.sendMessage(messageManager.getMessage("errors.no_permission"));
                    return true;
                }
                return handleReloadCommand(sender);
                
            case "show":
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    showChangelogBook(player, sender.hasPermission("changelogbook.admin"));
                    changelogManager.updateLastSeen(player);
                    return true;
                }
                sender.sendMessage(messageManager.getMessage("errors.no_permission"));
                return true;
                
            case "give":
                if (!sender.hasPermission("changelogbook.admin")) {
                    sender.sendMessage(messageManager.getMessage("errors.no_permission"));
                    return true;
                }
                return handleGiveCommand(sender, args);
                
            case "debug":
                if (!sender.hasPermission("changelogbook.admin")) {
                    sender.sendMessage(messageManager.getMessage("errors.no_permission"));
                    return true;
                }
                return handleDebugCommand(sender, args);
                
            default:
                showHelpMenu(sender);
                return true;
        }
    }

    private boolean handleAddCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(messageManager.getMessage("commands.add_usage"));
            return true;
        }

        StringBuilder content = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            content.append(args[i]).append(" ");
        }

        String authorName = (sender instanceof Player) ? ((Player) sender).getName() : sender.getName();
        ChangelogEntry entry = changelogManager.addEntry(content.toString().trim(), authorName);

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("number", changelogManager.getEntryDisplayNumber(entry));
        sender.sendMessage(messageManager.getMessage("messages.entry_added", placeholders));
        
        // Log the action
        plugin.getLogManager().info("Changelog entry added by " + authorName + ": " + entry.getId());
        
        broadcastChangelogNotification();
        return true;
    }

    private boolean handleEditCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(messageManager.getMessage("commands.edit_usage"));
            return true;
        }

        String id = args[1];
        StringBuilder content = new StringBuilder();
        for (int i = 2; i < args.length; i++) {
            content.append(args[i]).append(" ");
        }

        boolean success = changelogManager.editEntry(id, content.toString().trim());

        if (success) {
            // Find the entry to get its display number
            ChangelogEntry entry = changelogManager.getEntries().stream()
                    .filter(e -> e.getId().equals(id))
                    .findFirst()
                    .orElse(null);
            
            Map<String, String> placeholders = new HashMap<>();
            if (entry != null) {
                placeholders.put("number", changelogManager.getEntryDisplayNumber(entry));
            } else {
                placeholders.put("number", "#?");
            }
            sender.sendMessage(messageManager.getMessage("messages.entry_updated", placeholders));
        } else {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("id", id);
            sender.sendMessage(messageManager.getMessage("errors.entry_not_found", placeholders));
        }

        return true;
    }

    private boolean handleDeleteCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(messageManager.getMessage("commands.delete_usage"));
            return true;
        }

        String id = args[1];
        
        // Find the entry to get its display number before deletion
        ChangelogEntry entryToDelete = changelogManager.getEntries().stream()
                .filter(e -> e.getId().equals(id))
                .findFirst()
                .orElse(null);
        
        String displayNumber = "#?";
        if (entryToDelete != null) {
            displayNumber = changelogManager.getEntryDisplayNumber(entryToDelete);
        }
        
        boolean success = changelogManager.removeEntry(id);

        if (success) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("number", displayNumber);
            sender.sendMessage(messageManager.getMessage("messages.entry_deleted", placeholders));
            
            // Log the action
            String actorName = (sender instanceof Player) ? ((Player) sender).getName() : sender.getName();
            plugin.getLogManager().info("Changelog entry deleted by " + actorName + ": " + id);
        } else {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("id", id);
            sender.sendMessage(messageManager.getMessage("errors.entry_not_found", placeholders));
        }

        return true;
    }

    private boolean handleListCommand(CommandSender sender) {
        List<ChangelogEntry> entries = changelogManager.getEntries();
        
        if (entries.isEmpty()) {
            sender.sendMessage(messageManager.getMessage("messages.no_entries"));
            return true;
        }

        if (sender instanceof Player) {
            Player player = (Player) sender;
            showChangelogBook(player, true);
        } else {
            sender.sendMessage(ChatColor.GREEN + "Changelog entries:");
            int index = 1;
            for (ChangelogEntry entry : entries) {
                sender.sendMessage(ChatColor.GOLD + "#" + index + 
                                  ChatColor.WHITE + " | Author: " + entry.getAuthor() + 
                                  ChatColor.WHITE + " | Date: " + changelogManager.formatDate(entry.getTimestamp()));
                sender.sendMessage(ChatColor.WHITE + "Content: " + ColorUtil.formatText(entry.getContent()));
                sender.sendMessage(ChatColor.GRAY + "ID (for commands): " + entry.getId());
                sender.sendMessage("");
                index++;
            }
        }

        return true;
    }

    private boolean handleReloadCommand(CommandSender sender) {
        plugin.reloadConfig();
        changelogManager.loadData();
        
        sender.sendMessage(messageManager.getMessage("commands.reload_success"));
        return true;
    }

    private boolean handleGiveCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(messageManager.getMessage("commands.give_usage"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("player", args[1]);
            sender.sendMessage(messageManager.getMessage("errors.player_not_found", placeholders));
            return true;
        }

        String rewardType = args[2];
        if (!rewardManager.isRewardEnabled(rewardType)) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("reward", rewardType);
            sender.sendMessage(messageManager.getMessage("rewards.not_available", placeholders));
            return true;
        }

        if (rewardManager.claimReward(target, rewardType)) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("player", target.getName());
            placeholders.put("reward", rewardType);
            
            sender.sendMessage(ChatColor.GREEN + "Reward given to " + target.getName());
            target.sendMessage(messageManager.getMessage("rewards.claim_success", placeholders));
        } else {
            long remaining = rewardManager.getRemainingCooldownHours(target, rewardType);
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("hours", String.valueOf(remaining));
            target.sendMessage(messageManager.getMessage("errors.cooldown_active", placeholders));
        }

        return true;
    }

    private boolean handleDebugCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "Debug is currently: " + 
                              (plugin.isDebugMode() ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF"));
            return true;
        }

        String mode = args[1].toLowerCase();
        if (mode.equals("on")) {
            plugin.setDebugMode(true);
            sender.sendMessage(ChatColor.GREEN + "Debug mode enabled");
        } else if (mode.equals("off")) {
            plugin.setDebugMode(false);
            sender.sendMessage(ChatColor.GREEN + "Debug mode disabled");
        } else {
            sender.sendMessage(ChatColor.RED + "Usage: /changelog debug <on|off>");
        }

        return true;
    }

    private void broadcastChangelogNotification() {
        TextComponent message = new TextComponent(ChatColor.GRAY + "[" + 
                                                 ChatColor.YELLOW + "Changelog" + 
                                                 ChatColor.GRAY + "] " + 
                                                 ChatColor.GREEN + messageManager.getMessage("messages.notification"));
        
        message.setHoverEvent(new HoverEvent(
                HoverEvent.Action.SHOW_TEXT, 
                new ComponentBuilder(messageManager.getMessage("messages.click_to_view")).create()));
        
        message.setClickEvent(new ClickEvent(
                ClickEvent.Action.RUN_COMMAND, 
                "/changelog"));
        
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            player.spigot().sendMessage(message);
        }
    }

    private void showHelpMenu(CommandSender sender) {
        sender.sendMessage(ChatColor.GREEN + "=== ChangelogBook Help ===");
        sender.sendMessage(messageManager.getMessage("commands.help"));
        
        if (sender.hasPermission("changelogbook.admin")) {
            sender.sendMessage(ChatColor.GOLD + "/changelog add <content>" + ChatColor.WHITE + " - Add a changelog entry");
            sender.sendMessage(ChatColor.GOLD + "/changelog edit <id> <content>" + ChatColor.WHITE + " - Edit an entry");
            sender.sendMessage(ChatColor.GOLD + "/changelog delete <id>" + ChatColor.WHITE + " - Delete an entry");
            sender.sendMessage(ChatColor.GOLD + "/changelog list" + ChatColor.WHITE + " - List all entries");
            sender.sendMessage(ChatColor.GOLD + "/changelog reload" + ChatColor.WHITE + " - Reload configuration");
            sender.sendMessage(ChatColor.GOLD + "/changelog give <player> <type>" + ChatColor.WHITE + " - Give a reward");
            sender.sendMessage(ChatColor.GOLD + "/changelog debug <on|off>" + ChatColor.WHITE + " - Toggle debug mode");
        }
    }

    private void showChangelogBook(Player player, boolean showIds) {
        List<ChangelogEntry> entries = changelogManager.getEntries();
        
        if (entries.isEmpty()) {
            player.sendMessage(messageManager.getMessage("messages.no_entries"));
            return;
        }

        long lastSeen = changelogManager.getLastSeen(player);
        
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        
        meta.setTitle(ChatColor.GOLD + "Changelog");
        meta.setAuthor("Administrator");
        
        List<String> pages = new ArrayList<>();
        
        int newChangesCount = changelogManager.getNewEntriesCount(player);
        
        StringBuilder titlePage = new StringBuilder();
        titlePage.append(ChatColor.DARK_RED).append("⚒ Changelog ⚒\n\n");
        titlePage.append(ChatColor.DARK_GRAY).append("━━━━━━━━━━━━━━━━━━\n\n");
        titlePage.append(ChatColor.BLACK).append("Server changelog\n\n");
        titlePage.append(ChatColor.DARK_BLUE).append("➤ Total: ");
        titlePage.append(ChatColor.DARK_AQUA).append(entries.size()).append("\n");
        titlePage.append(ChatColor.DARK_BLUE).append("➤ New: ");
        
        if (newChangesCount > 0) {
            titlePage.append(ChatColor.GREEN).append(newChangesCount);
        } else {
            titlePage.append(ChatColor.GRAY).append("0");
        }
        
        titlePage.append("\n\n");
        titlePage.append(ChatColor.DARK_GRAY).append("━━━━━━━━━━━━━━━━━━\n\n");
        titlePage.append(ChatColor.GOLD).append("Scroll to view changes");
        
        pages.add(titlePage.toString());
        
        boolean hasNewRecentEntries = false;
        long maxEntryAgeMillis = 7 * 24 * 60 * 60 * 1000L;
        long currentTime = System.currentTimeMillis();
        
        for (ChangelogEntry entry : entries) {
            StringBuilder page = new StringBuilder();
            page.append(ChatColor.DARK_BLUE).append("📅 ").append(changelogManager.formatDate(entry.getTimestamp())).append("\n");
            page.append(ChatColor.DARK_BLUE).append("👤 ").append(entry.getAuthor());
            page.append(" ").append(ChatColor.GOLD).append(changelogManager.getEntryDisplayNumber(entry));
            page.append("\n\n");
            page.append(ColorUtil.formatText(entry.getContent()));
            
            pages.add(page.toString());
            
            if (entry.getTimestamp() > lastSeen && 
                currentTime - entry.getTimestamp() <= maxEntryAgeMillis) {
                hasNewRecentEntries = true;
            }
        }
        
        meta.setPages(pages);
        book.setItemMeta(meta);
        
        player.openBook(book);
        
        if (!showIds && plugin.getConfig().getBoolean("rewards.enabled", true) && hasNewRecentEntries) {
            processReward(player);
        }
    }
    
    private void processReward(Player player) {
        var rewardsSection = plugin.getConfig().getConfigurationSection("rewards.types");
        if (rewardsSection == null) {
            return;
        }

        for (String rewardType : rewardsSection.getKeys(false)) {
            if (!rewardManager.isRewardEnabled(rewardType)) {
                continue;
            }

            int chance = rewardManager.getRewardChance(rewardType);
            int random = new Random().nextInt(100) + 1;

            if (random <= chance) {
                if (rewardManager.claimReward(player, rewardType)) {
                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("reward", rewardType);
                    player.sendMessage(messageManager.getMessage("rewards.claim_success", placeholders));
                    break;
                }
            }
        }
    }
}
