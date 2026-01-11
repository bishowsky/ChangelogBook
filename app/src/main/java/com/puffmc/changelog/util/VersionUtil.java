package com.puffmc.changelog.util;

import org.bukkit.Bukkit;

/**
 * Utility class for detecting Minecraft server version
 * and checking feature compatibility.
 */
public class VersionUtil {
    private static final String VERSION;
    private static final int MAJOR_VERSION;
    private static final int MINOR_VERSION;
    private static final boolean SUPPORTS_HEX;

    static {
        String version = Bukkit.getVersion();
        String parsedVersion = "1.21";
        int major = 1;
        int minor = 21;

        try {
            // Parse version from string like "git-Paper-448 (MC: 1.21.3)"
            if (version.contains("MC: ")) {
                String[] parts = version.split("MC: ");
                if (parts.length > 1) {
                    parsedVersion = parts[1].replaceAll("[^0-9.]", "").trim();
                }
            } else if (version.contains("(") && version.contains(")")) {
                // Alternative format parsing
                String versionPart = version.substring(version.indexOf("(") + 1, version.indexOf(")"));
                parsedVersion = versionPart.replaceAll("[^0-9.]", "").trim();
            }

            String[] versionParts = parsedVersion.split("\\.");
            if (versionParts.length > 0) {
                major = Integer.parseInt(versionParts[0]);
            }
            if (versionParts.length > 1) {
                minor = Integer.parseInt(versionParts[1]);
            }
        } catch (Exception e) {
            // Fallback to safe defaults
            System.err.println("[ChangelogBook] Failed to parse server version: " + version);
            System.err.println("[ChangelogBook] Using fallback version 1.21");
        }

        VERSION = parsedVersion;
        MAJOR_VERSION = major;
        MINOR_VERSION = minor;
        
        // Hex colors are supported in Minecraft 1.16+
        SUPPORTS_HEX = (major > 1) || (major == 1 && minor >= 16);
    }

    /**
     * Checks if the server version supports hex colors.
     * Hex colors were introduced in Minecraft 1.16.
     *
     * @return true if server is running 1.16 or newer, false otherwise
     */
    public static boolean supportsHexColors() {
        return SUPPORTS_HEX;
    }

    /**
     * Gets the parsed server version string.
     *
     * @return version string (e.g., "1.21.3")
     */
    public static String getVersion() {
        return VERSION;
    }

    /**
     * Gets the major version number.
     *
     * @return major version (e.g., 1)
     */
    public static int getMajorVersion() {
        return MAJOR_VERSION;
    }

    /**
     * Gets the minor version number.
     *
     * @return minor version (e.g., 21)
     */
    public static int getMinorVersion() {
        return MINOR_VERSION;
    }
}
