package com.puffmc.changelog.command;

import com.puffmc.changelog.ChangelogEntry;
import com.puffmc.changelog.ChangelogPlugin;
import com.puffmc.changelog.MessageManager;
import com.puffmc.changelog.RewardManager;
import com.puffmc.changelog.UpdateChecker;
import com.puffmc.changelog.manager.ChangelogManager;
import com.puffmc.changelog.util.ColorUtil;
import com.puffmc.changelog.util.ComponentUtil;
import com.puffmc.changelog.util.VersionUtil;
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
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class ChangelogCommand implements CommandExecutor {
    private final ChangelogPlugin plugin;
    private final ChangelogManager changelogManager;
    private final MessageManager messageManager;
    private final RewardManager rewardManager;
    private final UpdateChecker updateChecker;

    public ChangelogCommand(ChangelogPlugin plugin, ChangelogManager changelogManager, MessageManager messageManager, RewardManager rewardManager, UpdateChecker updateChecker) {
        this.plugin = plugin;
        this.changelogManager = changelogManager;
        this.messageManager = messageManager;
        this.rewardManager = rewardManager;
        this.updateChecker = updateChecker;
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
                
            case "checkupdate":
                if (!sender.hasPermission("changelogbook.admin")) {
                    sender.sendMessage(messageManager.getMessage("errors.no_permission"));
                    return true;
                }
                return handleCheckUpdateCommand(sender);
                
            case "info":
                if (!sender.hasPermission("changelogbook.admin")) {
                    sender.sendMessage(messageManager.getMessage("errors.no_permission"));
                    return true;
                }
                return handleInfoCommand(sender);
                
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

        // Check if first argument is a valid category
        String category = null;
        int contentStartIndex = 1;
        
        // Get enabled categories from config
        Set<String> enabledCategories = getEnabledCategories();
        
        // Check if first argument matches a category
        if (enabledCategories.contains(args[1].toLowerCase())) {
            category = args[1].toLowerCase();
            contentStartIndex = 2;
            
            // Ensure there's content after the category
            if (args.length < 3) {
                sender.sendMessage(messageManager.getMessage("commands.add_usage"));
                return true;
            }
        }

        // Build content from remaining arguments
        StringBuilder content = new StringBuilder();
        for (int i = contentStartIndex; i < args.length; i++) {
            content.append(args[i]).append(" ");
        }
        
        // ✅ FIX #1: Validate content length and emptiness (Critical)
        String finalContent = content.toString().trim();
        
        // Check if empty
        if (finalContent.isEmpty()) {
            sender.sendMessage(messageManager.getMessage("errors.empty_content"));
            return true;
        }
        
        // Get limits from config
        int maxLength = plugin.getConfig().getInt("limits.max-content-length", 5000);
        int minLength = plugin.getConfig().getInt("limits.min-content-length", 3);
        
        // Check minimum length
        if (finalContent.length() < minLength) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("min", String.valueOf(minLength));
            placeholders.put("current", String.valueOf(finalContent.length()));
            sender.sendMessage(messageManager.getMessage("errors.content_too_short", placeholders));
            return true;
        }
        
        // Check maximum length (DoS protection)
        if (finalContent.length() > maxLength) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("max", String.valueOf(maxLength));
            placeholders.put("current", String.valueOf(finalContent.length()));
            sender.sendMessage(messageManager.getMessage("errors.content_too_long", placeholders));
            return true;
        }

        String authorName = (sender instanceof Player) ? ((Player) sender).getName() : sender.getName();
        ChangelogEntry entry = changelogManager.addEntry(finalContent, authorName, category);

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("number", changelogManager.getEntryDisplayNumber(entry));
        sender.sendMessage(messageManager.getMessage("messages.entry_added", placeholders));
        
        // Log the action
        String categoryInfo = category != null ? " [" + category + "]" : "";
        plugin.getLogManager().info("Changelog entry added by " + authorName + categoryInfo + ": " + entry.getId());
        
        // Send Discord notification
        if (plugin.getDiscordWebhook() != null && plugin.getDiscordWebhook().isEnabled()) {
            plugin.getDiscordWebhook().sendChangelogNotification(entry, authorName);
        }
        
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

    private boolean handleCheckUpdateCommand(CommandSender sender) {
        if (!updateChecker.isEnabled()) {
            sender.sendMessage(messageManager.getMessage("update.disabled"));
            return true;
        }

        sender.sendMessage(messageManager.getMessage("update.checking"));
        
        // Force refresh the update check asynchronously
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            updateChecker.checkForUpdates(true);
            
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (updateChecker.isUpdateAvailable()) {
                    if (sender instanceof Player) {
                        TextComponent updateNotification = ComponentUtil.createUpdateNotification(
                            messageManager,
                            updateChecker.getCurrentVersion(),
                            updateChecker.getLatestVersion(),
                            updateChecker.getDownloadUrl()
                        );
                        ((Player) sender).spigot().sendMessage(updateNotification);
                    } else {
                        String message = messageManager.getMessage("update.available")
                            .replace("%current_version%", updateChecker.getCurrentVersion())
                            .replace("%new_version%", updateChecker.getLatestVersion());
                        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
                        sender.sendMessage(ChatColor.YELLOW + "Download: " + updateChecker.getDownloadUrl());
                    }
                } else {
                    sender.sendMessage(messageManager.getMessage("update.up_to_date"));
                }
            });
        });

        return true;
    }

    private boolean handleInfoCommand(CommandSender sender) {
        // Gather plugin information
        String pluginVersion = plugin.getDescription().getVersion();
        String serverType = VersionUtil.getServerType();
        String serverVersion = VersionUtil.getServerVersionString();
        boolean updateNotifierEnabled = plugin.getConfig().getBoolean("update-checker.enabled", true);
        boolean databaseEnabled = plugin.getDatabaseManager().isUsingMySQL();
        String databaseType = plugin.getConfig().getBoolean("mysql.enabled", false) ? "MySQL" : "YAML";
        boolean databaseSync = plugin.getConfig().getBoolean("mysql.sync", false);

        // Create placeholders map
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("pluginversion", pluginVersion);
        placeholders.put("servertype", serverType);
        placeholders.put("serverversion", serverVersion);
        placeholders.put("updatenotifier", updateNotifierEnabled ? ChatColor.GREEN + "Enabled" : ChatColor.RED + "Disabled");
        placeholders.put("database", databaseEnabled ? ChatColor.GREEN + "Connected" : ChatColor.RED + "Disconnected");
        placeholders.put("databasetype", databaseType);
        placeholders.put("databasesync", databaseSync ? ChatColor.GREEN + "Enabled" : ChatColor.RED + "Disabled");

        // Get info lines from language file
        List<String> infoLines = messageManager.getMessages().getStringList("info");
        
        if (infoLines.isEmpty()) {
            // Fallback if list is not found
            sender.sendMessage(ChatColor.RED + "Info command is not configured properly in language file.");
            return true;
        }
        
        // Send each line with placeholder replacement
        for (String line : infoLines) {
            String formattedLine = line;
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                formattedLine = formattedLine.replace("%" + entry.getKey() + "%", entry.getValue());
            }
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', formattedLine));
        }

        return true;
    }

    /**
     * Gets the set of enabled category names from config
     * @return Set of lowercase category names that are enabled
     */
    private Set<String> getEnabledCategories() {
        Set<String> categories = new HashSet<>();
        ConfigurationSection categoriesSection = plugin.getConfig().getConfigurationSection("categories");
        
        if (categoriesSection != null) {
            for (String categoryName : categoriesSection.getKeys(false)) {
                boolean enabled = categoriesSection.getBoolean(categoryName + ".enabled", false);
                if (enabled) {
                    categories.add(categoryName.toLowerCase());
                }
            }
        }
        
        return categories;
    }

    /**
     * Gets the icon for a category from config
     * @param category The category name
     * @return The icon string, or null if not found
     */
    private String getCategoryIcon(String category) {
        if (category == null || category.isEmpty()) {
            return null;
        }
        
        ConfigurationSection categoriesSection = plugin.getConfig().getConfigurationSection("categories");
        if (categoriesSection != null) {
            String icon = categoriesSection.getString(category.toLowerCase() + ".icon", null);
            if (icon != null && !icon.isEmpty()) {
                return icon;
            }
        }
        
        return null;
    }
    
    /**
     * Gets the color for a category from config
     * @param category The category name
     * @return The color code string (e.g., "&a"), or empty string if not found
     */
    private String getCategoryColor(String category) {
        if (category == null || category.isEmpty()) {
            return "";
        }
        
        ConfigurationSection categoriesSection = plugin.getConfig().getConfigurationSection("categories");
        if (categoriesSection != null) {
            String color = categoriesSection.getString(category.toLowerCase() + ".color", "");
            if (color != null && !color.isEmpty()) {
                return ChatColor.translateAlternateColorCodes('&', color);
            }
        }
        
        return "";
    }

    /**
     * Gets the display name for a category from config
     * @param category The category name
     * @return The display name, or capitalized category name if not found
     */
    private String getCategoryDisplayName(String category) {
        if (category == null || category.isEmpty()) {
            return "";
        }
        
        ConfigurationSection categoriesSection = plugin.getConfig().getConfigurationSection("categories");
        if (categoriesSection != null) {
            String name = categoriesSection.getString(category.toLowerCase() + ".name", "");
            if (name != null && !name.isEmpty()) {
                return name;
            }
        }
        
        // Fallback: capitalize first letter
        return category.substring(0, 1).toUpperCase() + category.substring(1);
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
            sender.sendMessage(ChatColor.GOLD + "/changelog checkupdate" + ChatColor.WHITE + " - Check for plugin updates");
            sender.sendMessage(ChatColor.GOLD + "/changelog info" + ChatColor.WHITE + " - Show plugin information");
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
        
        meta.setTitle(ChatColor.GOLD + messageManager.getMessage("book.title"));
        meta.setAuthor(messageManager.getMessage("book.author"));
        
        List<String> pages = new ArrayList<>();
        
        int newChangesCount = changelogManager.getNewEntriesCount(player);
        
        // Build first page using config values with fallback to language file
        String firstPageTitle = plugin.getConfig().getString("first-page.title", messageManager.getMessage("book.first_page_title"));
        String firstPageSeparator = plugin.getConfig().getString("first-page.separator", messageManager.getMessage("book.first_page_separator"));
        String firstPageDescription = plugin.getConfig().getString("first-page.description", messageManager.getMessage("book.first_page_subtitle"));
        String firstPageFooter = plugin.getConfig().getString("first-page.footer", messageManager.getMessage("book.first_page_scroll"));
        
        StringBuilder titlePage = new StringBuilder();
        titlePage.append(ChatColor.DARK_RED).append(firstPageTitle).append("\n\n");
        titlePage.append(ChatColor.DARK_GRAY).append(firstPageSeparator).append("\n\n");
        titlePage.append(ChatColor.BLACK).append(firstPageDescription).append("\n\n");
        titlePage.append(ChatColor.DARK_BLUE).append(messageManager.getMessage("book.first_page_total"));
        titlePage.append(ChatColor.DARK_AQUA).append(entries.size()).append("\n");
        titlePage.append(ChatColor.DARK_BLUE).append(messageManager.getMessage("book.first_page_new"));
        
        if (newChangesCount > 0) {
            titlePage.append(ChatColor.GREEN).append(newChangesCount);
        } else {
            titlePage.append(ChatColor.GRAY).append("0");
        }
        
        titlePage.append("\n\n");
        titlePage.append(ChatColor.DARK_GRAY).append(firstPageSeparator).append("\n\n");
        titlePage.append(ChatColor.GOLD).append(firstPageFooter);
        
        pages.add(titlePage.toString());
        
        boolean hasNewRecentEntries = false;
        long maxEntryAgeMillis = 7 * 24 * 60 * 60 * 1000L;
        long currentTime = System.currentTimeMillis();
        
        for (ChangelogEntry entry : entries) {
            StringBuilder page = new StringBuilder();
            page.append(ChatColor.DARK_BLUE).append(messageManager.getMessage("book.entry_date_prefix")).append(changelogManager.formatDate(entry.getTimestamp())).append("\n");
            page.append(ChatColor.DARK_BLUE).append(messageManager.getMessage("book.entry_author_prefix")).append(entry.getAuthor());
            page.append(" ").append(ChatColor.GOLD).append(changelogManager.getEntryDisplayNumber(entry));
            page.append("\n\n");
            
            // ✅ Add category icon and color if entry has a category
            String content = entry.getContent();
            if (entry.getCategory() != null && !entry.getCategory().isEmpty()) {
                String categoryColor = getCategoryColor(entry.getCategory());
                String categoryIcon = getCategoryIcon(entry.getCategory());
                
                if (categoryColor != null && !categoryColor.isEmpty()) {
                    page.append(categoryColor);
                }
                
                if (categoryIcon != null && !categoryIcon.isEmpty()) {
                    page.append(categoryIcon).append(" ");
                }
                
                // Reset color after icon
                page.append(ChatColor.RESET);
            }
            
            page.append(ColorUtil.formatText(content));
            
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
