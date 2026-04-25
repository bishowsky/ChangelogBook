package com.puffmc.changelog;

import com.puffmc.changelog.manager.ChangelogManager;
import com.puffmc.changelog.util.ColorUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

/**
 * Handles preview of changelog entries before publishing
 */
public class ChangelogPreview {
    private final ChangelogPlugin plugin;
    private final ChangelogManager changelogManager;

    public ChangelogPreview(ChangelogPlugin plugin, ChangelogManager changelogManager) {
        this.plugin = plugin;
        this.changelogManager = changelogManager;
    }

    /**
     * Shows preview of an existing entry
     * @param player Player to show preview to
     * @param entryId Entry ID to preview
     */
    public void previewExisting(Player player, String entryId) {
        ChangelogEntry entry = changelogManager.getEntries().stream()
                .filter(e -> e.getId().equals(entryId))
                .findFirst()
                .orElse(null);

        if (entry == null) {
            player.sendMessage(ColorUtil.formatText("&cEntry not found: " + entryId));
            return;
        }

        showPreviewBook(player, entry);
    }

    /**
     * Shows preview of a new entry before creating it
     * @param player Player to show preview to
     * @param content Entry content
     * @param category Entry category
     */
    public void previewNew(Player player, String content, String category) {
        ChangelogEntry tempEntry = new ChangelogEntry(
                "preview-temp", 
                content, 
                player.getName(), 
                System.currentTimeMillis(), 
                category
        );

        showPreviewBook(player, tempEntry);
    }

    /**
     * Shows preview book to player
     * @param player Player
     * @param entry Entry to preview
     */
    private void showPreviewBook(Player player, ChangelogEntry entry) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();

        if (meta == null) {
            player.sendMessage(ColorUtil.formatText("&cFailed to create preview book"));
            return;
        }

        meta.setTitle(ColorUtil.formatText("&6Preview"));
        meta.setAuthor("ChangelogBook");

        String page = buildPreviewPage(entry);
        meta.addPage(page);

        book.setItemMeta(meta);
        player.openBook(book);

        player.sendMessage(ColorUtil.formatText("&aShowing preview. This entry is not saved yet."));
    }

    /**
     * Builds preview page content
     * @param entry Entry
     * @return Formatted page content
     */
    private String buildPreviewPage(ChangelogEntry entry) {
        StringBuilder page = new StringBuilder();

        page.append(ColorUtil.formatText("&6&l[PREVIEW]")).append("\n\n");

        // Category
        if (entry.getCategory() != null && !entry.getCategory().isEmpty()) {
            String categoryIcon = getCategoryIcon(entry.getCategory());
            String categoryColor = getCategoryColor(entry.getCategory());
            String categoryName = getCategoryDisplayName(entry.getCategory());

            page.append(ColorUtil.formatText(categoryColor + categoryIcon + " " + categoryName)).append("\n\n");
        }

        // Content
        String formattedContent = ColorUtil.formatText(entry.getContent());
        page.append(formattedContent).append("\n\n");

        // Metadata
        page.append(ColorUtil.formatText("&7---")).append("\n");
        page.append(ColorUtil.formatText("&7Author: &f" + entry.getAuthor())).append("\n");
        
        if (!entry.getId().equals("preview-temp")) {
            page.append(ColorUtil.formatText("&7ID: &f" + entry.getId()));
        } else {
            page.append(ColorUtil.formatText("&7Status: &eNot saved"));
        }

        return page.toString();
    }

    private String getCategoryIcon(String category) {
        return plugin.getConfig().getString("categories." + category.toLowerCase() + ".icon", "");
    }

    private String getCategoryColor(String category) {
        return plugin.getConfig().getString("categories." + category.toLowerCase() + ".color", "&7");
    }

    private String getCategoryDisplayName(String category) {
        String name = plugin.getConfig().getString("categories." + category.toLowerCase() + ".name", "");
        if (name.isEmpty()) {
            return category.substring(0, 1).toUpperCase() + category.substring(1);
        }
        return name;
    }
}
