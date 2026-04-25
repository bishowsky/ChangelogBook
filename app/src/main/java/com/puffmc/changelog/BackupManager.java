package com.puffmc.changelog;

import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Manages automatic backups of changelog data
 */
public class BackupManager {
    private final ChangelogPlugin plugin;
    private final File backupFolder;
    private BukkitRunnable backupTask;

    public BackupManager(ChangelogPlugin plugin) {
        this.plugin = plugin;
        this.backupFolder = new File(plugin.getDataFolder(), "backups");

        if (!backupFolder.exists()) {
            backupFolder.mkdirs();
        }
    }

    /**
     * Starts automatic backup schedule
     */
    public void startAutoBackup() {
        if (!plugin.getConfig().getBoolean("backup.enabled", false)) {
            return;
        }

        int interval = plugin.getConfig().getInt("backup.interval-hours", 24);
        long ticks = interval * 60 * 60 * 20L;

        backupTask = new BukkitRunnable() {
            @Override
            public void run() {
                performBackup(true);
            }
        };

        backupTask.runTaskTimerAsynchronously(plugin, ticks, ticks);
        plugin.getLogger().info("Automatic backups scheduled every " + interval + " hours");
    }

    /**
     * Stops automatic backup schedule
     */
    public void stopAutoBackup() {
        if (backupTask != null) {
            backupTask.cancel();
            backupTask = null;
        }
    }

    /**
     * Performs a backup
     * @param auto Whether this is an automatic backup
     * @return true if successful
     */
    public boolean performBackup(boolean auto) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
            String timestamp = sdf.format(new Date());
            String prefix = auto ? "auto" : "manual";
            String backupName = prefix + "-backup-" + timestamp + ".yml";

            File backupFile = new File(backupFolder, backupName);
            File dataFile = new File(plugin.getDataFolder(), "data.yml");

            if (!dataFile.exists()) {
                plugin.getLogger().warning("Data file not found, skipping backup");
                return false;
            }

            Files.copy(dataFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            plugin.debug("Backup created: " + backupName);
            cleanOldBackups();

            return true;
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to create backup: " + e.getMessage());
            return false;
        }
    }

    /**
     * Cleans old backups based on retention policy
     */
    private void cleanOldBackups() {
        int retentionDays = plugin.getConfig().getInt("backup.retention-days", 30);
        long retentionMs = retentionDays * 24L * 60 * 60 * 1000;
        long now = System.currentTimeMillis();

        File[] backups = backupFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (backups == null) return;

        int deleted = 0;
        for (File backup : backups) {
            if ((now - backup.lastModified()) > retentionMs) {
                if (backup.delete()) {
                    deleted++;
                }
            }
        }

        if (deleted > 0) {
            plugin.debug("Cleaned " + deleted + " old backups");
        }
    }

    /**
     * Lists all available backups
     * @return Array of backup files
     */
    public File[] listBackups() {
        File[] backups = backupFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (backups == null) {
            return new File[0];
        }

        java.util.Arrays.sort(backups, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return backups;
    }

    /**
     * Restores from a backup file
     * @param backupFile Backup file
     * @return true if successful
     */
    public boolean restoreBackup(File backupFile) {
        if (!backupFile.exists()) {
            return false;
        }

        try {
            File dataFile = new File(plugin.getDataFolder(), "data.yml");
            Files.copy(backupFile.toPath(), dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            plugin.getLogger().info("Restored backup: " + backupFile.getName());
            return true;
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to restore backup: " + e.getMessage());
            return false;
        }
    }
}
