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
                    if (args.length == 2) {
                        // Complete category names
                        var categoriesSection = plugin.getConfig().getConfigurationSection("categories");
                        if (categoriesSection != null) {
                            completions.addAll(categoriesSection.getKeys(false).stream()
                                    .filter(category -> categoriesSection.getBoolean(category + ".enabled", false))
                                    .filter(category -> category.toLowerCase().startsWith(args[1].toLowerCase()))
                                    .sorted()
                                    .collect(Collectors.toList()));
                        }
                    }
                    break;

                case "edit":
                case "delete":
                    if (args.length == 2) {
                        // Complete entry IDs
                        completions.addAll(changelogManager.getEntries().stream()
                                .map(ChangelogEntry::getId)
                                .filter(id -> id.startsWith(args[1]))
                                .limit(10)
                                .collect(Collectors.toList()));
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
