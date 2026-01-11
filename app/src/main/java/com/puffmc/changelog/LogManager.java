package com.puffmc.changelog;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Level;

/**
 * Manages logging to file for important operations
 */
public class LogManager {
    private final ChangelogPlugin plugin;
    private final File logFile;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    
    public LogManager(ChangelogPlugin plugin) {
        this.plugin = plugin;
        
        // Create logs directory
        File logsDir = new File(plugin.getDataFolder(), "logs");
        if (!logsDir.exists()) {
            logsDir.mkdirs();
        }
        
        // Create log file with date
        String fileName = new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + ".log";
        logFile = new File(logsDir, fileName);
        
        try {
            if (!logFile.exists()) {
                logFile.createNewFile();
                log(Level.INFO, "Log file created: " + logFile.getName());
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to create log file: " + e.getMessage());
        }
    }
    
    /**
     * Logs a message to file
     * @param level The log level
     * @param message The message to log
     */
    public void log(Level level, String message) {
        String timestamp = dateFormat.format(new Date());
        String logLine = String.format("[%s] [%s] %s%n", timestamp, level.getName(), message);
        
        try (FileWriter fw = new FileWriter(logFile, true);
             PrintWriter pw = new PrintWriter(fw)) {
            pw.print(logLine);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to write to log file", e);
        }
    }
    
    /**
     * Logs an INFO level message
     * @param message The message to log
     */
    public void info(String message) {
        log(Level.INFO, message);
    }
    
    /**
     * Logs a WARNING level message
     * @param message The message to log
     */
    public void warning(String message) {
        log(Level.WARNING, message);
    }
    
    /**
     * Logs a SEVERE level message
     * @param message The message to log
     */
    public void severe(String message) {
        log(Level.SEVERE, message);
    }
    
    /**
     * Logs an exception with stack trace
     * @param message The message to log
     * @param throwable The exception
     */
    public void error(String message, Throwable throwable) {
        String timestamp = dateFormat.format(new Date());
        
        try (FileWriter fw = new FileWriter(logFile, true);
             PrintWriter pw = new PrintWriter(fw)) {
            pw.printf("[%s] [SEVERE] %s%n", timestamp, message);
            throwable.printStackTrace(pw);
            pw.println();
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to write error to log file", e);
        }
    }
}
