package com.puffmc.changelog.util;

import org.bukkit.ChatColor;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for parsing and formatting color codes in text.
 * Supports standard Minecraft color codes (&0-9a-f, &l/o/n/m/k/r)
 * and hex colors (&#RRGGBB) with automatic version detection.
 * 
 * On servers 1.16+: Hex colors are rendered natively
 * On servers 1.8-1.15: Hex colors are converted to nearest legacy color
 */
public class ColorUtil {
    
    // Pattern for hex colors: &#RRGGBB
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([0-9A-Fa-f]{6})");
    
    // Map of legacy colors to their RGB values for distance calculation
    private static final Map<ChatColor, int[]> LEGACY_RGB = new HashMap<>();
    
    static {
        LEGACY_RGB.put(ChatColor.BLACK, new int[]{0, 0, 0});
        LEGACY_RGB.put(ChatColor.DARK_BLUE, new int[]{0, 0, 170});
        LEGACY_RGB.put(ChatColor.DARK_GREEN, new int[]{0, 170, 0});
        LEGACY_RGB.put(ChatColor.DARK_AQUA, new int[]{0, 170, 170});
        LEGACY_RGB.put(ChatColor.DARK_RED, new int[]{170, 0, 0});
        LEGACY_RGB.put(ChatColor.DARK_PURPLE, new int[]{170, 0, 170});
        LEGACY_RGB.put(ChatColor.GOLD, new int[]{255, 170, 0});
        LEGACY_RGB.put(ChatColor.GRAY, new int[]{170, 170, 170});
        LEGACY_RGB.put(ChatColor.DARK_GRAY, new int[]{85, 85, 85});
        LEGACY_RGB.put(ChatColor.BLUE, new int[]{85, 85, 255});
        LEGACY_RGB.put(ChatColor.GREEN, new int[]{85, 255, 85});
        LEGACY_RGB.put(ChatColor.AQUA, new int[]{85, 255, 255});
        LEGACY_RGB.put(ChatColor.RED, new int[]{255, 85, 85});
        LEGACY_RGB.put(ChatColor.LIGHT_PURPLE, new int[]{255, 85, 255});
        LEGACY_RGB.put(ChatColor.YELLOW, new int[]{255, 255, 85});
        LEGACY_RGB.put(ChatColor.WHITE, new int[]{255, 255, 255});
    }
    
    /**
     * Main method to format text with color codes.
     * Processes both legacy codes (&) and hex colors (&#RRGGBB).
     * Automatically detects server version for hex color support.
     *
     * @param text the text to format
     * @return formatted text with proper color codes
     */
    public static String formatText(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        
        // First, process hex colors
        text = parseHexColors(text);
        
        // Then, translate legacy color codes
        text = translateLegacyCodes(text);
        
        return text;
    }
    
    /**
     * Translates legacy color codes (&0-9a-f, &l/o/n/m/k/r) to Minecraft format (§).
     *
     * @param text the text containing & color codes
     * @return text with § color codes
     */
    public static String translateLegacyCodes(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        
        return ChatColor.translateAlternateColorCodes('&', text);
    }
    
    /**
     * Parses hex color codes (&#RRGGBB) in text.
     * On 1.16+: Converts to native hex format (§x§R§R§G§G§B§B)
     * On 1.8-1.15: Converts to nearest legacy ChatColor
     *
     * @param text the text containing hex color codes
     * @return text with parsed hex colors
     */
    public static String parseHexColors(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuffer buffer = new StringBuffer();
        
        while (matcher.find()) {
            String hexColor = matcher.group(1);
            String replacement = hexToColorCode(hexColor);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        
        matcher.appendTail(buffer);
        return buffer.toString();
    }
    
    /**
     * Converts a hex color string to a Minecraft color code.
     * Version-aware: uses native hex on 1.16+, legacy color on older versions.
     *
     * @param hex the hex color without # (e.g., "FF5733")
     * @return color code string
     */
    private static String hexToColorCode(String hex) {
        if (VersionUtil.supportsHexColors()) {
            return hexToModernColorCode(hex);
        } else {
            return hexToLegacyColorCode(hex);
        }
    }
    
    /**
     * Converts hex to modern Minecraft hex format (§x§R§R§G§G§B§B).
     * Used on servers 1.16+.
     *
     * @param hex the hex color without # (e.g., "FF5733")
     * @return Minecraft hex color code
     */
    private static String hexToModernColorCode(String hex) {
        StringBuilder sb = new StringBuilder("§x");
        for (char c : hex.toCharArray()) {
            sb.append("§").append(c);
        }
        return sb.toString();
    }
    
    /**
     * Converts hex to nearest legacy ChatColor using RGB distance calculation.
     * Used on servers 1.8-1.15 that don't support hex colors.
     *
     * @param hex the hex color without # (e.g., "FF5733")
     * @return legacy ChatColor code
     */
    private static String hexToLegacyColorCode(String hex) {
        try {
            // Parse RGB values from hex
            int targetR = Integer.parseInt(hex.substring(0, 2), 16);
            int targetG = Integer.parseInt(hex.substring(2, 4), 16);
            int targetB = Integer.parseInt(hex.substring(4, 6), 16);
            
            // Find nearest legacy color using Euclidean distance
            ChatColor nearest = ChatColor.WHITE;
            double minDistance = Double.MAX_VALUE;
            
            for (Map.Entry<ChatColor, int[]> entry : LEGACY_RGB.entrySet()) {
                int[] rgb = entry.getValue();
                double distance = Math.sqrt(
                    Math.pow(rgb[0] - targetR, 2) +
                    Math.pow(rgb[1] - targetG, 2) +
                    Math.pow(rgb[2] - targetB, 2)
                );
                
                if (distance < minDistance) {
                    minDistance = distance;
                    nearest = entry.getKey();
                }
            }
            
            return nearest.toString();
        } catch (Exception e) {
            // Fallback to white if parsing fails
            return ChatColor.WHITE.toString();
        }
    }
    
    /**
     * Strips all color codes from text.
     * Useful for logging or plain text output.
     *
     * @param text the text with color codes
     * @return plain text without color codes
     */
    public static String stripColors(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        
        // First strip hex colors
        text = text.replaceAll("&#[0-9A-Fa-f]{6}", "");
        
        // Then strip legacy codes
        return ChatColor.stripColor(text);
    }
}
