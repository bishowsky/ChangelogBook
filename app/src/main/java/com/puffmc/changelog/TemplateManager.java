package com.puffmc.changelog;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Manages changelog entry templates
 */
public class TemplateManager {
    private final ChangelogPlugin plugin;
    private final File templatesFile;
    private FileConfiguration templates;
    private final Map<String, Template> loadedTemplates;

    public TemplateManager(ChangelogPlugin plugin) {
        this.plugin = plugin;
        this.templatesFile = new File(plugin.getDataFolder(), "templates.yml");
        this.loadedTemplates = new LinkedHashMap<>();
        loadTemplates();
    }

    /**
     * Loads templates from file
     */
    private void loadTemplates() {
        if (!templatesFile.exists()) {
            createDefaultTemplates();
        }

        templates = YamlConfiguration.loadConfiguration(templatesFile);
        loadedTemplates.clear();

        for (String templateId : templates.getKeys(false)) {
            String name = templates.getString(templateId + ".name");
            String content = templates.getString(templateId + ".content");
            String category = templates.getString(templateId + ".category");
            List<String> tags = templates.getStringList(templateId + ".tags");

            if (name != null && content != null) {
                loadedTemplates.put(templateId, new Template(templateId, name, content, category, tags));
            }
        }

        plugin.debug("Loaded " + loadedTemplates.size() + " templates");
    }

    /**
     * Creates default templates
     */
    private void createDefaultTemplates() {
        try {
            templatesFile.createNewFile();
            FileConfiguration config = YamlConfiguration.loadConfiguration(templatesFile);

            config.set("bugfix.name", "Bug Fix");
            config.set("bugfix.content", "&cFixed: {description}");
            config.set("bugfix.category", "Fixed");
            config.set("bugfix.tags", Arrays.asList("bug", "fix"));

            config.set("feature.name", "New Feature");
            config.set("feature.content", "&aAdded: {description}");
            config.set("feature.category", "Added");
            config.set("feature.tags", Arrays.asList("feature", "new"));

            config.set("change.name", "Change");
            config.set("change.content", "&eChanged: {description}");
            config.set("change.category", "Changed");
            config.set("change.tags", Arrays.asList("change", "update"));

            config.set("removal.name", "Removal");
            config.set("removal.content", "&7Removed: {description}");
            config.set("removal.category", "Removed");
            config.set("removal.tags", Arrays.asList("removed", "deprecated"));

            config.save(templatesFile);
            plugin.getLogger().info("Created default templates");
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to create templates file: " + e.getMessage());
        }
    }

    /**
     * Gets a template by ID
     * @param templateId Template ID
     * @return Template or null
     */
    public Template getTemplate(String templateId) {
        return loadedTemplates.get(templateId);
    }

    /**
     * Gets all templates
     * @return Collection of templates
     */
    public Collection<Template> getAllTemplates() {
        return loadedTemplates.values();
    }

    /**
     * Creates a new template
     * @param templateId Template ID
     * @param name Template name
     * @param content Template content
     * @param category Default category
     * @param tags Default tags
     */
    public void createTemplate(String templateId, String name, String content, String category, List<String> tags) {
        Template template = new Template(templateId, name, content, category, tags);
        loadedTemplates.put(templateId, template);

        templates.set(templateId + ".name", name);
        templates.set(templateId + ".content", content);
        templates.set(templateId + ".category", category);
        templates.set(templateId + ".tags", tags);

        saveTemplates();
        plugin.debug("Created template: " + templateId);
    }

    /**
     * Deletes a template
     * @param templateId Template ID
     * @return true if deleted
     */
    public boolean deleteTemplate(String templateId) {
        if (loadedTemplates.remove(templateId) != null) {
            templates.set(templateId, null);
            saveTemplates();
            plugin.debug("Deleted template: " + templateId);
            return true;
        }
        return false;
    }

    /**
     * Saves templates to file
     */
    private void saveTemplates() {
        try {
            templates.save(templatesFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save templates: " + e.getMessage());
        }
    }

    /**
     * Applies a template with placeholders
     * @param template Template
     * @param placeholders Placeholder values
     * @return Filled content
     */
    public String applyTemplate(Template template, Map<String, String> placeholders) {
        String content = template.getContent();

        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            content = content.replace("{" + entry.getKey() + "}", entry.getValue());
        }

        return content;
    }

    /**
     * Represents a changelog template
     */
    public static class Template {
        private final String id;
        private final String name;
        private final String content;
        private final String category;
        private final List<String> tags;

        public Template(String id, String name, String content, String category, List<String> tags) {
            this.id = id;
            this.name = name;
            this.content = content;
            this.category = category;
            this.tags = tags != null ? tags : new ArrayList<>();
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getContent() {
            return content;
        }

        public String getCategory() {
            return category;
        }

        public List<String> getTags() {
            return tags;
        }
    }
}
