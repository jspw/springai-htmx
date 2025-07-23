package com.coderkaku.demo.configs;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for conversation memory management
 */
@Configuration
@ConfigurationProperties(prefix = "conversation.memory")
public class ConversationMemoryConfig {
    
    /**
     * Maximum number of messages to keep per conversation session
     */
    private int maxMessagesPerSession = 50;
    
    /**
     * Maximum number of messages to include in context for LLM prompts
     */
    private int maxContextMessages = 10;
    
    /**
     * Session cleanup interval in minutes
     */
    private long cleanupIntervalMinutes = 30;
    
    /**
     * Session expiration time in minutes (inactive sessions will be cleaned up)
     */
    private long sessionExpirationMinutes = 120;
    
    /**
     * Maximum number of active sessions to maintain
     */
    private int maxActiveSessions = 1000;
    
    /**
     * Enable automatic cleanup of expired sessions
     */
    private boolean enableAutomaticCleanup = true;
    
    /**
     * Maximum memory usage threshold (in MB) before triggering cleanup
     */
    private long maxMemoryUsageMB = 100;
    
    // Getters and setters
    
    public int getMaxMessagesPerSession() {
        return maxMessagesPerSession;
    }
    
    public void setMaxMessagesPerSession(int maxMessagesPerSession) {
        if (maxMessagesPerSession <= 0) {
            throw new IllegalArgumentException("Max messages per session must be positive");
        }
        this.maxMessagesPerSession = maxMessagesPerSession;
    }
    
    public int getMaxContextMessages() {
        return maxContextMessages;
    }
    
    public void setMaxContextMessages(int maxContextMessages) {
        if (maxContextMessages <= 0) {
            throw new IllegalArgumentException("Max context messages must be positive");
        }
        this.maxContextMessages = maxContextMessages;
    }
    
    public long getCleanupIntervalMinutes() {
        return cleanupIntervalMinutes;
    }
    
    public void setCleanupIntervalMinutes(long cleanupIntervalMinutes) {
        if (cleanupIntervalMinutes <= 0) {
            throw new IllegalArgumentException("Cleanup interval must be positive");
        }
        this.cleanupIntervalMinutes = cleanupIntervalMinutes;
    }
    
    public long getSessionExpirationMinutes() {
        return sessionExpirationMinutes;
    }
    
    public void setSessionExpirationMinutes(long sessionExpirationMinutes) {
        if (sessionExpirationMinutes <= 0) {
            throw new IllegalArgumentException("Session expiration time must be positive");
        }
        this.sessionExpirationMinutes = sessionExpirationMinutes;
    }
    
    public int getMaxActiveSessions() {
        return maxActiveSessions;
    }
    
    public void setMaxActiveSessions(int maxActiveSessions) {
        if (maxActiveSessions <= 0) {
            throw new IllegalArgumentException("Max active sessions must be positive");
        }
        this.maxActiveSessions = maxActiveSessions;
    }
    
    public boolean isEnableAutomaticCleanup() {
        return enableAutomaticCleanup;
    }
    
    public void setEnableAutomaticCleanup(boolean enableAutomaticCleanup) {
        this.enableAutomaticCleanup = enableAutomaticCleanup;
    }
    
    public long getMaxMemoryUsageMB() {
        return maxMemoryUsageMB;
    }
    
    public void setMaxMemoryUsageMB(long maxMemoryUsageMB) {
        if (maxMemoryUsageMB <= 0) {
            throw new IllegalArgumentException("Max memory usage must be positive");
        }
        this.maxMemoryUsageMB = maxMemoryUsageMB;
    }
    
    /**
     * Validate configuration values
     * @throws IllegalArgumentException if configuration is invalid
     */
    public void validate() {
        if (maxContextMessages > maxMessagesPerSession) {
            throw new IllegalArgumentException("Max context messages cannot exceed max messages per session");
        }
        
        if (cleanupIntervalMinutes >= sessionExpirationMinutes) {
            throw new IllegalArgumentException("Cleanup interval should be less than session expiration time");
        }
    }
}