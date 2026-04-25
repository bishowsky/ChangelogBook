package com.puffmc.changelog.command;

import com.puffmc.changelog.ChangelogEntry;
import com.puffmc.changelog.ChangelogPlugin;
import com.puffmc.changelog.manager.ChangelogManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ChangelogTabCompleter implements TabCompleter {
    private final ChangelogPlugin plugin;
    private final ChangelogManager changelogManager;

    public ChangelogTabCompleter(ChangelogPlugin plugin, ChangelogManager changelogManager) {
        this.plugin = plugin;
        this.changelogManager = changelogManager;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Complete first argument (subcommands)
            List<String> subcommands = new ArrayList<>();
            subcommands.add("show");
            
            if (sender.hasPermission("changelogbook.admin")) {
                subcommands.add("add");
                subcommands.add("edit");
                subcommands.add("delete");
                subcommands.add("list");
                subcommands.add("reload");
                subcommands.add("give");
                subcommands.add("debug");
                subcommands.add("checkupdate");
                subcommands.add("info");
                subcommands.add("stats");
                subcommands.add("search");
                subcommands.add("health");
                subcommands.add("export");
                subcommands.add("import");
                subcommands.add("backup");
                subcommands.add("preview");
                subcommands.add("suggestions");
                subcommands.add("tag");
                subcommands.add("milestone");
                subcommands.add("impact");
                subcommands.add("schedule");
                subcommands.add("analytics");
            }
            
            if (sender.hasPermission("changelogbook.use")) {
                subcommands.add("suggest");
                subcommands.add("vote");
            }

            String arg = args[0].toLowerCase();
            for (String subcommand : subcommands) {
                if (subcommand.startsWith(arg)) {
                    completions.add(subcommand);
                }
            }
        } else if (args.length > 1) {
            String subcommand = args[0].toLowerCase();

            switch (subcommand) {
                case "add":
                    // /changelog add [custom-id] [category] <content>
                    if (args.length == 2) {
                        // Show categories for quick selection
                        var categoriesSection = plugin.getConfig().getConfigurationSection("categories");
                        if (categoriesSection != null) {
                            completions.addAll(categoriesSection.getKeys(false).stream()
                                    .filter(category -> categoriesSection.getBoolean(category + ".enabled", false))
                                    .filter(category -> category.toLowerCase().startsWith(args[1].toLowerCase()))
                                    .sorted()
                                    .collect(Collectors.toList()));
                        }
                    } else if (args.length == 3) {
                        // Show categories again for custom-id + category format
                        var categoriesSection = plugin.getConfig().getConfigurationSection("categories");
                        if (categoriesSection != null) {
                            completions.addAll(categoriesSection.getKeys(false).stream()
                                    .filter(category -> categoriesSection.getBoolean(category + ".enabled", false))
                                    .filter(category -> category.toLowerCase().startsWith(args[2].toLowerCase()))
                                    .sorted()
                                    .collect(Collectors.toList()));
                        }
                    }
                    break;

                case "edit":
                case "delete":
                case "preview":
                    if (args.length == 2) {
                        // Complete entry IDs
                        completions.addAll(changelogManager.getEntries().stream()
                                .map(ChangelogEntry::getId)
                                .filter(id -> id.toLowerCase().startsWith(args[1].toLowerCase()))
                                .limit(20)
                                .collect(Collectors.toList()));
                    }
                    break;
                
                case "vote":
                    if (args.length == 2) {
                        // Complete entry IDs
                        completions.addAll(changelogManager.getEntries().stream()
                                .map(ChangelogEntry::getId)
                                .filter(id -> id.toLowerCase().startsWith(args[1].toLowerCase()))
                                .limit(10)
                                .collect(Collectors.toList()));
                    } else if (args.length == 3) {
                        // Complete vote type
                        completions.add("like");
                        completions.add("dislike");
                    }
                    break;
                
                case "search":
                    if (args.length == 2) {
                        // Suggest search filters
                        completions.add("author:");
                        completions.add("category:");
                        completions.add("tag:");
                        completions.add("date:");
                    }
                    break;
                
                case "export":
                    if (args.length == 2) {
                        completions.add("json");
                        completions.add("markdown");
                        completions.add("yaml");
                        completions.add("csv");
                    }
                    break;
                
                case "import":
                    if (args.length == 2) {
                        completions.add("changelog-backup.json");
                        completions.add("changelog-export.json");
                    }
                    break;
                
                case "suggestions":
                    if (args.length == 2) {
                        completions.add("approve");
                        completions.add("reject");
                        completions.add("list");
                    } else if (args.length == 3 && (args[1].equalsIgnoreCase("approve") || args[1].equalsIgnoreCase("reject"))) {
                        // Suggest suggestion IDs (1, 2, 3, etc.)
                        completions.add("1");
                        completions.add("2");
                        completions.add("3");
                    }
                    break;
                
                case "tag":
                    if (args.length == 2) {
                        completions.add("add");
                        completions.add("remove");
                        completions.add("list");
                    } else if (args.length == 3) {
                        // Complete entry IDs
                        completions.addAll(changelogManager.getEntries().stream()
                                .map(ChangelogEntry::getId)
                                .filter(id -> id.toLowerCase().startsWith(args[2].toLowerCase()))
                                .limit(10)
                                .collect(Collectors.toList()));
                    } else if (args.length == 4 && args[1].equalsIgnoreCase("add")) {
                        // Suggest common tags
                        completions.add("critical");
                        completions.add("security");
                        completions.add("important");
                        completions.add("hotfix");
                        completions.add("experimental");
                    }
                    break;
                
                case "milestone":
                    if (args.length == 2) {
                        completions.add("create");
                        completions.add("list");
                        completions.add("add");
                        completions.add("release");
                    } else if (args.length == 3 && args[1].equalsIgnoreCase("create")) {
                        // Suggest version format
                        completions.add("v1.0.0");
                        completions.add("v1.1.0");
                        completions.add("v2.0.0");
                    }
                    break;
                
                case "impact":
                    if (args.length == 2) {
                        // Complete entry IDs
                        completions.addAll(changelogManager.getEntries().stream()
                                .map(ChangelogEntry::getId)
                                .filter(id -> id.toLowerCase().startsWith(args[1].toLowerCase()))
                                .limit(10)
                                .collect(Collectors.toList()));
                    } else if (args.length == 3) {
                        // Complete impact levels
                        completions.add("CRITICAL");
                        completions.add("HIGH");
                        completions.add("MEDIUM");
                        completions.add("LOW");
                        completions.add("MINOR");
                    }
                    break;
                
                case "schedule":
                    if (args.length == 2) {
                        // Suggest delay times in minutes
                        completions.add("5");
                        completions.add("10");
                        completions.add("30");
                        completions.add("60");
                        completions.add("1440");
                    }
                    break;

                case "give":
                    if (args.length == 2) {
                        // Complete player names
                        completions.addAll(Bukkit.getOnlinePlayers().stream()
                                .map(Player::getName)
                                .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                                .collect(Collectors.toList()));
                    } else if (args.length == 3) {
                        // Complete reward types
                        var rewardsSection = plugin.getConfig().getConfigurationSection("rewards.types");
                        if (rewardsSection != null) {
                            completions.addAll(rewardsSection.getKeys(false).stream()
                                    .filter(type -> type.toLowerCase().startsWith(args[2].toLowerCase()))
                                    .collect(Collectors.toList()));
                        }
                    }
                    break;

                case "debug":
                    if (args.length == 2) {
                        String arg = args[1].toLowerCase();
                        if ("on".startsWith(arg)) {
                            completions.add("on");
                        }
                        if ("off".startsWith(arg)) {
                            completions.add("off");
                        }
                    }
                    break;
            }
        }

        return completions;
    }
}
