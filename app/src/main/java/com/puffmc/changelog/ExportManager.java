package com.puffmc.changelog;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.puffmc.changelog.manager.ChangelogManager;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Handles exporting and importing changelog data in various formats
 */
public class ExportManager {
    private final ChangelogPlugin plugin;
    private final ChangelogManager changelogManager;
    private final Gson gson;

    public ExportManager(ChangelogPlugin plugin, ChangelogManager changelogManager) {
        this.plugin = plugin;
        this.changelogManager = changelogManager;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }
    
    /**
     * Validates that file path is safe and within plugin folder
     * Prevents path traversal attacks
     * @param file File to validate
     * @return true if safe, false if potentially malicious
     */
    private boolean isSafeExportPath(File file) {
        try {
            File pluginFolder = plugin.getDataFolder().getCanonicalFile();
            File targetFile = file.getCanonicalFile();
            
            // Check if file is within plugin folder
            if (!targetFile.toPath().startsWith(pluginFolder.toPath())) {
                plugin.getLogger().warning("SECURITY: Blocked path traversal attempt - file outside plugin folder: " + file.getPath());
                return false;
            }
            
            // Block overwriting critical configuration files
            String name = targetFile.getName().toLowerCase();
            if (name.equals("config.yml") || 
                name.equals("discord.yml") || 
                name.equals("data.yml") ||
                name.startsWith("en.yml") ||
                name.startsWith("pl.yml")) {
                plugin.getLogger().warning("SECURITY: Blocked attempt to overwrite critical file: " + name);
                return false;
            }
            
            return true;
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to validate export path: " + e.getMessage());
            return false;
        }
    }

    /**
     * Exports to JSON format
     * @param outputFile Output file
     * @return true if successful
     */
    public boolean exportToJson(File outputFile) {
        if (!isSafeExportPath(outputFile)) {
            plugin.getLogger().severe("Export to JSON denied: unsafe path");
            return false;
        }
        
        try {
            List<ChangelogEntry> entries = changelogManager.getEntries();
            
            Map<String, Object> exportData = new HashMap<>();
            exportData.put("version", plugin.getDescription().getVersion());
            exportData.put("exported", System.currentTimeMillis());
            exportData.put("total", entries.size());
            
            List<Map<String, Object>> entriesData = new ArrayList<>();
            for (ChangelogEntry entry : entries) {
                Map<String, Object> entryData = new HashMap<>();
                entryData.put("id", entry.getId());
                entryData.put("content", entry.getContent());
                entryData.put("author", entry.getAuthor());
                entryData.put("timestamp", entry.getTimestamp());
                entryData.put("category", entry.getCategory());
                entryData.put("created_at", entry.getCreatedAt());
                entryData.put("modified_at", entry.getModifiedAt());
                entriesData.add(entryData);
            }
            
            exportData.put("entries", entriesData);

            try (FileWriter writer = new FileWriter(outputFile)) {
                gson.toJson(exportData, writer);
            }

            plugin.debug("Exported " + entries.size() + " entries to JSON: " + outputFile.getName());
            return true;
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to export to JSON: " + e.getMessage());
            return false;
        }
    }

    /**
     * Exports to Markdown format
     * @param outputFile Output file
     * @return true if successful
     */
    public boolean exportToMarkdown(File outputFile) {
        if (!isSafeExportPath(outputFile)) {
            plugin.getLogger().severe("Export to Markdown denied: unsafe path");
            return false;
        }
        
        try {
            List<ChangelogEntry> entries = changelogManager.getEntries();
            StringBuilder md = new StringBuilder();

            md.append("# Changelog\n\n");
            md.append("Generated: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())).append("\n");
            md.append("Total Entries: ").append(entries.size()).append("\n\n");
            md.append("---\n\n");

            Map<String, List<ChangelogEntry>> byCategory = new LinkedHashMap<>();
            for (ChangelogEntry entry : entries) {
                String category = entry.getCategory() != null ? entry.getCategory() : "Uncategorized";
                byCategory.computeIfAbsent(category, k -> new ArrayList<>()).add(entry);
            }

            for (Map.Entry<String, List<ChangelogEntry>> categoryGroup : byCategory.entrySet()) {
                md.append("## ").append(categoryGroup.getKey()).append("\n\n");

                for (ChangelogEntry entry : categoryGroup.getValue()) {
                    String date = new SimpleDateFormat("yyyy-MM-dd").format(new Date(entry.getTimestamp()));
                    md.append("### ").append(date).append(" - ").append(entry.getAuthor()).append("\n\n");
                    md.append(entry.getContent().replaceAll("&[0-9a-fk-or]", "")).append("\n\n");
                    md.append("_ID: ").append(entry.getId()).append("_\n\n");
                    md.append("---\n\n");
                }
            }

            Files.write(outputFile.toPath(), md.toString().getBytes());

            plugin.debug("Exported " + entries.size() + " entries to Markdown: " + outputFile.getName());
            return true;
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to export to Markdown: " + e.getMessage());
            return false;
        }
    }

    /**
     * Exports to YAML format
     * @param outputFile Output file
     * @return true if successful
     */
    public boolean exportToYaml(File outputFile) {
        if (!isSafeExportPath(outputFile)) {
            plugin.getLogger().severe("Export to YAML denied: unsafe path");
            return false;
        }
        
        try {
            List<ChangelogEntry> entries = changelogManager.getEntries();
            YamlConfiguration yaml = new YamlConfiguration();

            yaml.set("version", plugin.getDescription().getVersion());
            yaml.set("exported", System.currentTimeMillis());
            yaml.set("total", entries.size());

            for (int i = 0; i < entries.size(); i++) {
                ChangelogEntry entry = entries.get(i);
                String path = "entries." + i;
                
                yaml.set(path + ".id", entry.getId());
                yaml.set(path + ".content", entry.getContent());
                yaml.set(path + ".author", entry.getAuthor());
                yaml.set(path + ".timestamp", entry.getTimestamp());
                yaml.set(path + ".category", entry.getCategory());
                yaml.set(path + ".created_at", entry.getCreatedAt());
                yaml.set(path + ".modified_at", entry.getModifiedAt());
            }

            yaml.save(outputFile);

            plugin.debug("Exported " + entries.size() + " entries to YAML: " + outputFile.getName());
            return true;
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to export to YAML: " + e.getMessage());
            return false;
        }
    }

    /**
     * Exports to CSV format
     * @param outputFile Output file
     * @return true if successful
     */
    public boolean exportToCsv(File outputFile) {
        if (!isSafeExportPath(outputFile)) {
            plugin.getLogger().severe("Export to CSV denied: unsafe path");
            return false;
        }
        
        try {
            List<ChangelogEntry> entries = changelogManager.getEntries();
            StringBuilder csv = new StringBuilder();

            csv.append("ID,Content,Author,Category,Timestamp,Created,Modified\n");

            for (ChangelogEntry entry : entries) {
                csv.append(escapeCsv(entry.getId())).append(",");
                csv.append(escapeCsv(entry.getContent())).append(",");
                csv.append(escapeCsv(entry.getAuthor())).append(",");
                csv.append(escapeCsv(entry.getCategory())).append(",");
                csv.append(entry.getTimestamp()).append(",");
                csv.append(entry.getCreatedAt()).append(",");
                csv.append(entry.getModifiedAt()).append("\n");
            }

            Files.write(outputFile.toPath(), csv.toString().getBytes());

            plugin.debug("Exported " + entries.size() + " entries to CSV: " + outputFile.getName());
            return true;
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to export to CSV: " + e.getMessage());
            return false;
        }
    }

    /**
     * Imports from JSON format
     * @param inputFile Input file
     * @return Number of imported entries
     */
    @SuppressWarnings("unchecked")
    public int importFromJson(File inputFile) {
        if (!isSafeExportPath(inputFile)) {
            plugin.getLogger().severe("Import from JSON denied: unsafe path");
            return 0;
        }
        
        // Check file size limit (10 MB)
        final long MAX_FILE_SIZE = 10 * 1024 * 1024;
        if (inputFile.length() > MAX_FILE_SIZE) {
            plugin.getLogger().severe("Import failed: file too large (" + 
                (inputFile.length() / 1024 / 1024) + " MB > 10 MB)");
            return 0;
        }
        
        try {
            String content = new String(Files.readAllBytes(inputFile.toPath()));
            Map<String, Object> data = gson.fromJson(content, Map.class);

            List<Map<String, Object>> entriesData = (List<Map<String, Object>>) data.get("entries");
            if (entriesData == null) {
                return 0;
            }
            
            // Check entries count limit (10,000 entries max)
            final int MAX_ENTRIES = 10000;
            if (entriesData.size() > MAX_ENTRIES) {
                plugin.getLogger().severe("Import failed: too many entries (" + 
                    entriesData.size() + " > " + MAX_ENTRIES + ")");
                return 0;
            }

            int imported = 0;
            for (Map<String, Object> entryData : entriesData) {
                String id = (String) entryData.get("id");
                String entryContent = (String) entryData.get("content");
                String author = (String) entryData.get("author");
                Number timestamp = (Number) entryData.get("timestamp");
                String category = (String) entryData.get("category");

                if (id != null && entryContent != null && author != null && timestamp != null) {
                    ChangelogEntry entry = new ChangelogEntry(id, entryContent, author, timestamp.longValue(), category);
                    
                    if (entryData.containsKey("created_at")) {
                        entry.setCreatedAt(((Number) entryData.get("created_at")).longValue());
                    }
                    if (entryData.containsKey("modified_at")) {
                        entry.setModifiedAt(((Number) entryData.get("modified_at")).longValue());
                    }

                    if (!changelogManager.entryExists(id)) {
                        changelogManager.addEntry(id, entryContent, author, category);
                        imported++;
                    }
                }
            }

            plugin.debug("Imported " + imported + " entries from JSON");
            return imported;
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to import from JSON: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Escapes CSV field
     * @param field Field value
     * @return Escaped value
     */
    private String escapeCsv(String field) {
        if (field == null) {
            return "";
        }
        
        if (field.contains(",") || field.contains("\"") || field.contains("\n")) {
            return "\"" + field.replace("\"", "\"\"") + "\"";
        }
        
        return field;
    }
}
