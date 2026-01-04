package com.puffmc.changelog;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

public class MessageManager {
    private final JavaPlugin plugin;
    private final String language;
    private FileConfiguration messages;
    private final Map<String, String> defaultMessages = new HashMap<>();

    public MessageManager(JavaPlugin plugin, String language) {
        this.plugin = plugin;
        this.language = language.toLowerCase();
        loadDefaultMessages();
        reload();
    }

    private void loadDefaultMessages() {
        // English default fallback
        defaultMessages.put("commands.help", "§6/changelogbook §7- Display the server changelog");
        defaultMessages.put("commands.help_admin", "§6/changelogbook admin §7- Open administration panel");
        defaultMessages.put("commands.usage", "§cUsage: /changelogbook [admin]");
        defaultMessages.put("commands.reload", "§6/changelogbook reload §7- Reload configuration");
        defaultMessages.put("commands.reload_success", "§aConfiguration reloaded successfully");
        defaultMessages.put("commands.reload_permission", "§cYou don't have permission to reload the configuration");
        defaultMessages.put("messages.success", "§a✓ Operation successful");
        defaultMessages.put("messages.error", "§c✗ An error occurred");
        defaultMessages.put("messages.notification", "§eNew changelog entry available!");
        defaultMessages.put("messages.no_entries", "§7No changelog entries available");
        defaultMessages.put("dates.today", "Today");
        defaultMessages.put("dates.yesterday", "Yesterday");
        defaultMessages.put("errors.no_permission", "§cYou don't have permission to use this command");
        defaultMessages.put("errors.invalid_argument", "§cInvalid argument");
        defaultMessages.put("errors.player_not_found", "§cPlayer not found");
        defaultMessages.put("errors.database_error", "§cDatabase error occurred");
    }

    public void reload() {
        File languageDir = new File(plugin.getDataFolder(), "languages");
        if (!languageDir.exists()) {
            languageDir.mkdirs();
            extractDefaultLanguageFiles();
            plugin.getLogger().info("Created languages directory at: " + languageDir.getAbsolutePath());
        }

        // Try to load the selected language file
        File languageFile = new File(languageDir, language + ".yml");
        
        if (languageFile.exists()) {
            messages = YamlConfiguration.loadConfiguration(languageFile);
            plugin.getLogger().info("Loaded language file: " + language + ".yml");
        } else {
            // Fallback to English
            languageFile = new File(languageDir, "en.yml");
            if (languageFile.exists()) {
                messages = YamlConfiguration.loadConfiguration(languageFile);
                plugin.getLogger().info("Language file not found for: " + language + ", loaded English fallback");
            } else {
                // Create default configuration
                messages = new YamlConfiguration();
                plugin.getLogger().warning("No language files found in: " + languageDir.getAbsolutePath());
            }
        }
    }

    private void extractDefaultLanguageFiles() {
        extractLanguageFile("languages/en.yml");
        extractLanguageFile("languages/pl.yml");
    }

    private void extractLanguageFile(String path) {
        try {
            InputStream inputStream = plugin.getResource(path);
            if (inputStream == null) {
                plugin.getLogger().warning("Could not find resource: " + path);
                return;
            }

            File outputFile = new File(plugin.getDataFolder(), path);
            outputFile.getParentFile().mkdirs();
            Files.copy(inputStream, outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            inputStream.close();
        } catch (Exception e) {
            plugin.getLogger().warning("Could not extract language file: " + path);
        }
    }

    public String getMessage(String key) {
        return getMessage(key, null);
    }

    public String getMessage(String key, Map<String, String> placeholders) {
        String message = null;

        // Try to get from loaded YAML file
        if (messages != null && messages.contains(key)) {
            message = messages.getString(key);
        } 
        // Fall back to default English messages
        else if (defaultMessages.containsKey(key)) {
            message = defaultMessages.get(key);
        } 
        // If still not found, return the key itself
        else {
            message = "§c[Missing: " + key + "]";
        }

        // Replace placeholders if provided
        if (placeholders != null && message != null) {
            for (Map.Entry<String, String> placeholder : placeholders.entrySet()) {
                message = message.replace("%" + placeholder.getKey() + "%", placeholder.getValue());
            }
        }

        return message;
    }

    public String format(String message, Map<String, String> placeholders) {
        if (placeholders != null && message != null) {
            for (Map.Entry<String, String> placeholder : placeholders.entrySet()) {
                message = message.replace("%" + placeholder.getKey() + "%", placeholder.getValue());
            }
        }
        return message;
    }
}
