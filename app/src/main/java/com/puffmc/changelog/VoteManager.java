package com.puffmc.changelog;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Manages votes/reactions on changelog entries
 */
public class VoteManager {
    private final ChangelogPlugin plugin;
    private final File votesFile;
    private FileConfiguration votesConfig;
    private final Map<String, EntryVotes> entryVotes;

    public VoteManager(ChangelogPlugin plugin) {
        this.plugin = plugin;
        this.votesFile = new File(plugin.getDataFolder(), "votes.yml");
        this.entryVotes = new HashMap<>();
        loadVotes();
    }

    /**
     * Loads votes from file
     */
    private void loadVotes() {
        if (!votesFile.exists()) {
            try {
                votesFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to create votes file: " + e.getMessage());
                return;
            }
        }

        votesConfig = YamlConfiguration.loadConfiguration(votesFile);
        entryVotes.clear();

        for (String entryId : votesConfig.getKeys(false)) {
            int likes = votesConfig.getInt(entryId + ".likes", 0);
            int dislikes = votesConfig.getInt(entryId + ".dislikes", 0);
            List<String> likedBy = votesConfig.getStringList(entryId + ".liked_by");
            List<String> dislikedBy = votesConfig.getStringList(entryId + ".disliked_by");

            EntryVotes votes = new EntryVotes(entryId);
            votes.setLikes(likes);
            votes.setDislikes(dislikes);
            votes.getLikedBy().addAll(likedBy);
            votes.getDislikedBy().addAll(dislikedBy);

            entryVotes.put(entryId, votes);
        }
    }

    /**
     * Saves votes to file
     */
    private void saveVotes() {
        for (EntryVotes votes : entryVotes.values()) {
            String entryId = votes.getEntryId();
            votesConfig.set(entryId + ".likes", votes.getLikes());
            votesConfig.set(entryId + ".dislikes", votes.getDislikes());
            votesConfig.set(entryId + ".liked_by", new ArrayList<>(votes.getLikedBy()));
            votesConfig.set(entryId + ".disliked_by", new ArrayList<>(votes.getDislikedBy()));
        }

        try {
            votesConfig.save(votesFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save votes: " + e.getMessage());
        }
    }

    /**
     * Adds a like to an entry
     * @param entryId Entry ID
     * @param player Player name
     * @return true if vote was added
     */
    public boolean addLike(String entryId, String player) {
        EntryVotes votes = entryVotes.computeIfAbsent(entryId, EntryVotes::new);

        if (votes.getDislikedBy().contains(player)) {
            votes.getDislikedBy().remove(player);
            votes.setDislikes(votes.getDislikes() - 1);
        }

        if (!votes.getLikedBy().contains(player)) {
            votes.getLikedBy().add(player);
            votes.setLikes(votes.getLikes() + 1);
            saveVotes();
            plugin.debug(player + " liked entry " + entryId);
            return true;
        }

        return false;
    }

    /**
     * Adds a dislike to an entry
     * @param entryId Entry ID
     * @param player Player name
     * @return true if vote was added
     */
    public boolean addDislike(String entryId, String player) {
        EntryVotes votes = entryVotes.computeIfAbsent(entryId, EntryVotes::new);

        if (votes.getLikedBy().contains(player)) {
            votes.getLikedBy().remove(player);
            votes.setLikes(votes.getLikes() - 1);
        }

        if (!votes.getDislikedBy().contains(player)) {
            votes.getDislikedBy().add(player);
            votes.setDislikes(votes.getDislikes() + 1);
            saveVotes();
            plugin.debug(player + " disliked entry " + entryId);
            return true;
        }

        return false;
    }

    /**
     * Removes a player's vote
     * @param entryId Entry ID
     * @param player Player name
     * @return true if vote was removed
     */
    public boolean removeVote(String entryId, String player) {
        EntryVotes votes = entryVotes.get(entryId);
        if (votes == null) return false;

        boolean removed = false;

        if (votes.getLikedBy().remove(player)) {
            votes.setLikes(votes.getLikes() - 1);
            removed = true;
        }

        if (votes.getDislikedBy().remove(player)) {
            votes.setDislikes(votes.getDislikes() - 1);
            removed = true;
        }

        if (removed) {
            saveVotes();
            plugin.debug(player + " removed vote from entry " + entryId);
        }

        return removed;
    }

    /**
     * Gets votes for an entry
     * @param entryId Entry ID
     * @return EntryVotes or null
     */
    public EntryVotes getVotes(String entryId) {
        return entryVotes.get(entryId);
    }

    /**
     * Gets vote score (likes - dislikes)
     * @param entryId Entry ID
     * @return Vote score
     */
    public int getScore(String entryId) {
        EntryVotes votes = entryVotes.get(entryId);
        if (votes == null) return 0;
        return votes.getLikes() - votes.getDislikes();
    }

    /**
     * Gets top voted entries
     * @param limit Number of entries to return
     * @return List of entry IDs
     */
    public List<String> getTopVoted(int limit) {
        return entryVotes.entrySet().stream()
                .sorted((a, b) -> Integer.compare(
                        b.getValue().getLikes() - b.getValue().getDislikes(),
                        a.getValue().getLikes() - a.getValue().getDislikes()
                ))
                .limit(limit)
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Represents votes for an entry
     */
    public static class EntryVotes {
        private final String entryId;
        private int likes;
        private int dislikes;
        private final Set<String> likedBy;
        private final Set<String> dislikedBy;

        public EntryVotes(String entryId) {
            this.entryId = entryId;
            this.likes = 0;
            this.dislikes = 0;
            this.likedBy = new HashSet<>();
            this.dislikedBy = new HashSet<>();
        }

        public String getEntryId() {
            return entryId;
        }

        public int getLikes() {
            return likes;
        }

        public void setLikes(int likes) {
            this.likes = likes;
        }

        public int getDislikes() {
            return dislikes;
        }

        public void setDislikes(int dislikes) {
            this.dislikes = dislikes;
        }

        public Set<String> getLikedBy() {
            return likedBy;
        }

        public Set<String> getDislikedBy() {
            return dislikedBy;
        }
    }
}
