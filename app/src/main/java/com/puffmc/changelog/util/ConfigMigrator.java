package com.puffmc.changelog.util;

import com.puffmc.changelog.ChangelogPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Migrates existing config.yml to newer versions by adding missing keys
 * without overwriting values already set by the server owner.
 */
public class ConfigMigrator {

    // Bump this number every release that adds or renames config keys.
    public static final int CURRENT_VERSION = 2;

    private final ChangelogPlugin plugin;

    public ConfigMigrator(ChangelogPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Runs migration. Call this once in onEnable(), after saveDefaultConfig().
     * Returns the number of keys that were added.
     */
    public int migrate() {
        FileConfiguration config = plugin.getConfig();
        int existingVersion = config.getInt("config-version", 0);

        if (existingVersion >= CURRENT_VERSION) {
            return 0;
        }

        // Load the bundled default config from the JAR
        FileConfiguration defaults = loadBundledDefaults();
        if (defaults == null) {
            plugin.getLogger().warning("[ConfigMigrator] Could not read bundled config.yml - skipping migration.");
            return 0;
        }

        int added = addMissingKeys(config, defaults, "");

        if (added > 0) {
            config.set("config-version", CURRENT_VERSION);
            plugin.saveConfig();
            plugin.getLogger().info("[ConfigMigrator] Added " + added + " missing config key(s). Your existing settings were NOT changed.");
        } else {
            // Version was outdated but no keys were missing - still update the version
            config.set("config-version", CURRENT_VERSION);
            plugin.saveConfig();
        }

        return added;
    }

    /**
     * Recursively walks through all keys in the bundled defaults and copies
     * any key that is absent from the live config. Existing keys are skipped.
     */
    private int addMissingKeys(FileConfiguration live, FileConfiguration defaults, String path) {
        int count = 0;
        ConfigurationSection section = path.isEmpty() ? defaults : defaults.getConfigurationSection(path);
        if (section == null) return 0;

        Set<String> keys = section.getKeys(false);
        for (String key : keys) {
            String fullKey = path.isEmpty() ? key : path + "." + key;

            // Skip the version key itself
            if (fullKey.equals("config-version")) continue;

            Object defaultValue = defaults.get(fullKey);

            if (defaultValue instanceof ConfigurationSection) {
                // Recurse into nested sections
                count += addMissingKeys(live, defaults, fullKey);
            } else {
                if (!live.contains(fullKey)) {
                    live.set(fullKey, defaultValue);
                    plugin.getLogger().info("[ConfigMigrator] Added missing key: " + fullKey);
                    count++;
                }
            }
        }
        return count;
    }

    private FileConfiguration loadBundledDefaults() {
        try (InputStream stream = plugin.getResource("config.yml")) {
            if (stream == null) return null;
            return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException e) {
            plugin.getLogger().warning("[ConfigMigrator] Failed to read bundled config.yml: " + e.getMessage());
            return null;
        }
    }
}
