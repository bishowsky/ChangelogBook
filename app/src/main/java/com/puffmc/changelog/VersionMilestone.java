package com.puffmc.changelog;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Manages version milestones
 */
public class VersionMilestone {
    private final ChangelogPlugin plugin;
    private final File milestonesFile;
    private FileConfiguration milestones;
    private final Map<String, Milestone> loadedMilestones;

    public VersionMilestone(ChangelogPlugin plugin) {
        this.plugin = plugin;
        this.milestonesFile = new File(plugin.getDataFolder(), "milestones.yml");
        this.loadedMilestones = new LinkedHashMap<>();
        loadMilestones();
    }

    /**
     * Loads milestones from file
     */
    private void loadMilestones() {
        if (!milestonesFile.exists()) {
            try {
                milestonesFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to create milestones file: " + e.getMessage());
                return;
            }
        }

        milestones = YamlConfiguration.loadConfiguration(milestonesFile);
        loadedMilestones.clear();

        for (String versionId : milestones.getKeys(false)) {
            String version = milestones.getString(versionId + ".version");
            String name = milestones.getString(versionId + ".name");
            String description = milestones.getString(versionId + ".description");
            long releaseDate = milestones.getLong(versionId + ".release_date", 0);
            List<String> entries = milestones.getStringList(versionId + ".entries");
            boolean released = milestones.getBoolean(versionId + ".released", false);

            if (version != null) {
                loadedMilestones.put(versionId, new Milestone(versionId, version, name, description, releaseDate, entries, released));
            }
        }

        plugin.debug("Loaded " + loadedMilestones.size() + " milestones");
    }

    /**
     * Saves milestones to file
     */
    private void saveMilestones() {
        for (Milestone milestone : loadedMilestones.values()) {
            String id = milestone.getId();
            milestones.set(id + ".version", milestone.getVersion());
            milestones.set(id + ".name", milestone.getName());
            milestones.set(id + ".description", milestone.getDescription());
            milestones.set(id + ".release_date", milestone.getReleaseDate());
            milestones.set(id + ".entries", milestone.getEntries());
            milestones.set(id + ".released", milestone.isReleased());
        }

        try {
            milestones.save(milestonesFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save milestones: " + e.getMessage());
        }
    }

    /**
     * Creates a new milestone
     * @param version Version string
     * @param name Milestone name
     * @param description Description
     * @return Milestone ID
     */
    public String createMilestone(String version, String name, String description) {
        String id = "milestone-" + version.replace(".", "-");
        Milestone milestone = new Milestone(id, version, name, description, 0, new ArrayList<>(), false);
        loadedMilestones.put(id, milestone);
        saveMilestones();
        plugin.debug("Created milestone: " + id);
        return id;
    }

    /**
     * Gets a milestone
     * @param milestoneId Milestone ID
     * @return Milestone or null
     */
    public Milestone getMilestone(String milestoneId) {
        return loadedMilestones.get(milestoneId);
    }

    /**
     * Gets all milestones
     * @return Collection of milestones
     */
    public Collection<Milestone> getAllMilestones() {
        return loadedMilestones.values();
    }

    /**
     * Adds an entry to a milestone
     * @param milestoneId Milestone ID
     * @param entryId Entry ID
     * @return true if added
     */
    public boolean addEntry(String milestoneId, String entryId) {
        Milestone milestone = loadedMilestones.get(milestoneId);
        if (milestone != null && !milestone.getEntries().contains(entryId)) {
            milestone.getEntries().add(entryId);
            saveMilestones();
            plugin.debug("Added entry " + entryId + " to milestone " + milestoneId);
            return true;
        }
        return false;
    }

    /**
     * Removes an entry from a milestone
     * @param milestoneId Milestone ID
     * @param entryId Entry ID
     * @return true if removed
     */
    public boolean removeEntry(String milestoneId, String entryId) {
        Milestone milestone = loadedMilestones.get(milestoneId);
        if (milestone != null && milestone.getEntries().remove(entryId)) {
            saveMilestones();
            plugin.debug("Removed entry " + entryId + " from milestone " + milestoneId);
            return true;
        }
        return false;
    }

    /**
     * Marks a milestone as released
     * @param milestoneId Milestone ID
     * @return true if marked
     */
    public boolean releaseMilestone(String milestoneId) {
        Milestone milestone = loadedMilestones.get(milestoneId);
        if (milestone != null) {
            milestone.setReleased(true);
            milestone.setReleaseDate(System.currentTimeMillis());
            saveMilestones();
            plugin.debug("Released milestone: " + milestoneId);
            return true;
        }
        return false;
    }

    /**
     * Deletes a milestone
     * @param milestoneId Milestone ID
     * @return true if deleted
     */
    public boolean deleteMilestone(String milestoneId) {
        if (loadedMilestones.remove(milestoneId) != null) {
            milestones.set(milestoneId, null);
            saveMilestones();
            plugin.debug("Deleted milestone: " + milestoneId);
            return true;
        }
        return false;
    }

    /**
     * Represents a version milestone
     */
    public static class Milestone {
        private final String id;
        private final String version;
        private final String name;
        private final String description;
        private long releaseDate;
        private final List<String> entries;
        private boolean released;

        public Milestone(String id, String version, String name, String description, long releaseDate, List<String> entries, boolean released) {
            this.id = id;
            this.version = version;
            this.name = name;
            this.description = description;
            this.releaseDate = releaseDate;
            this.entries = entries != null ? entries : new ArrayList<>();
            this.released = released;
        }

        public String getId() {
            return id;
        }

        public String getVersion() {
            return version;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public long getReleaseDate() {
            return releaseDate;
        }

        public void setReleaseDate(long releaseDate) {
            this.releaseDate = releaseDate;
        }

        public List<String> getEntries() {
            return entries;
        }

        public boolean isReleased() {
            return released;
        }

        public void setReleased(boolean released) {
            this.released = released;
        }
    }
}
