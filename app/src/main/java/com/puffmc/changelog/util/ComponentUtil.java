package com.puffmc.changelog.util;

import com.puffmc.changelog.MessageManager;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;

/**
 * Utility class for creating text components with click and hover events.
 */
public class ComponentUtil {

    /**
     * Creates an update notification component with clickable link and hover text.
     *
     * @param messageManager message manager for retrieving localized strings
     * @param currentVersion current plugin version
     * @param latestVersion latest available version
     * @param downloadUrl URL to download the latest version
     * @return text component with click and hover events
     */
    public static TextComponent createUpdateNotification(MessageManager messageManager, String currentVersion, String latestVersion, String downloadUrl) {
        // Get localized messages
        String availableMsg = messageManager.getMessage("update.available")
                .replace("%current_version%", currentVersion)
                .replace("%new_version%", latestVersion);
        String downloadMsg = messageManager.getMessage("update.download");
        String hoverMsg = messageManager.getMessage("update.hover");

        // Apply color codes
        availableMsg = ColorUtil.formatText(availableMsg);
        downloadMsg = ColorUtil.formatText(downloadMsg);
        hoverMsg = ColorUtil.formatText(hoverMsg);

        // Create main text component
        TextComponent mainComponent = new TextComponent(availableMsg);

        // Create clickable download component
        TextComponent downloadComponent = new TextComponent(" [" + downloadMsg + "]");
        
        // Add hover event
        downloadComponent.setHoverEvent(new HoverEvent(
            HoverEvent.Action.SHOW_TEXT,
            new ComponentBuilder(hoverMsg).create()
        ));
        
        // Add click event to open URL
        downloadComponent.setClickEvent(new ClickEvent(
            ClickEvent.Action.OPEN_URL,
            downloadUrl
        ));

        // Combine components
        mainComponent.addExtra(downloadComponent);

        return mainComponent;
    }

    /**
     * Creates a simple clickable text component.
     *
     * @param text display text
     * @param hoverText text shown on hover
     * @param command command to run on click
     * @return text component with click and hover events
     */
    public static TextComponent createClickableCommand(String text, String hoverText, String command) {
        text = ColorUtil.formatText(text);
        hoverText = ColorUtil.formatText(hoverText);

        TextComponent component = new TextComponent(text);
        
        component.setHoverEvent(new HoverEvent(
            HoverEvent.Action.SHOW_TEXT,
            new ComponentBuilder(hoverText).create()
        ));
        
        component.setClickEvent(new ClickEvent(
            ClickEvent.Action.RUN_COMMAND,
            command
        ));

        return component;
    }
}
