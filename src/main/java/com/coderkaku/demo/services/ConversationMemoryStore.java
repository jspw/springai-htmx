package com.coderkaku.demo.services;

import com.coderkaku.demo.models.ConversationMemory;
import com.coderkaku.demo.models.ConversationMessage;
import com.coderkaku.demo.configs.ConversationMemoryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Component responsible for storing and retrieving conversation memory data
 * using HTTP session attributes. Provides session lifecycle management
 * and automatic cleanup capabilities.
 */
@Component
public class ConversationMemoryStore {

    private static final Logger logger = LoggerFactory.getLogger(ConversationMemoryStore.class);
    private static final String CONVERSATION_MEMORY_KEY = "conversationMemory";

    // Track sessions for cleanup (session ID -> last access time)
    private final Map<String, LocalDateTime> sessionTracker = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupExecutor;
    private final ConversationMemoryConfig config;

    public ConversationMemoryStore(ConversationMemoryConfig config) {
        this.config = config;
        config.validate(); // Validate configuration on startup
        // Initialize cleanup scheduler
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ConversationMemoryCleanup");
            t.setDaemon(true);
            return t;
        });

        // Schedule periodic cleanup if enabled
        if (config.isEnableAutomaticCleanup()) {
            this.cleanupExecutor.scheduleAtFixedRate(
                    this::performCleanup,
                    config.getCleanupIntervalMinutes(),
                    config.getCleanupIntervalMinutes(),
                    TimeUnit.MINUTES);

            logger.info("ConversationMemoryStore initialized with cleanup interval: {} minutes, session expiration: {} minutes",
                    config.getCleanupIntervalMinutes(), config.getSessionExpirationMinutes());
        } else {
            logger.info("ConversationMemoryStore initialized with automatic cleanup disabled");
        }
    }
    


    /**
     * Store conversation memory in the HTTP session
     * 
     * @param session The HTTP session
     * @param memory  The conversation memory to store
     * @throws IllegalArgumentException if session or memory is null
     */
    public void storeConversation(HttpSession session, ConversationMemory memory) {
        if (session == null) {
            throw new IllegalArgumentException("Session cannot be null");
        }
        if (memory == null) {
            throw new IllegalArgumentException("ConversationMemory cannot be null");
        }

        try {
            // Check if we need to enforce session limits
            if (sessionTracker.size() >= config.getMaxActiveSessions()) {
                performEmergencyCleanup();
            }
            
            // Truncate conversation if it exceeds limits
            truncateConversationIfNeeded(memory);
            
            session.setAttribute(CONVERSATION_MEMORY_KEY, memory);
            sessionTracker.put(session.getId(), LocalDateTime.now());



            logger.debug("Stored conversation memory for session: {} with {} messages",
                    session.getId(), memory.getMessageCount());
        } catch (Exception e) {
            logger.error("Failed to store conversation memory for session: {}", session.getId(), e);
            throw new RuntimeException("Failed to store conversation memory", e);
        }
    }

    /**
     * Retrieve conversation memory from the HTTP session
     * 
     * @param session The HTTP session
     * @return The conversation memory or null if not found
     * @throws IllegalArgumentException if session is null
     */
    public ConversationMemory retrieveConversation(HttpSession session) {
        if (session == null) {
            throw new IllegalArgumentException("Session cannot be null");
        }

        try {
            ConversationMemory memory = (ConversationMemory) session.getAttribute(CONVERSATION_MEMORY_KEY);

            if (memory != null) {
                sessionTracker.put(session.getId(), LocalDateTime.now());
                logger.debug("Retrieved conversation memory for session: {} with {} messages",
                        session.getId(), memory.getMessageCount());
            } else {
                logger.debug("No conversation memory found for session: {}", session.getId());
            }

            return memory;
        } catch (Exception e) {
            logger.error("Failed to retrieve conversation memory for session: {}", session.getId(), e);
            return null;
        }
    }

    /**
     * Remove conversation memory from the HTTP session
     * 
     * @param session The HTTP session
     * @return The removed conversation memory or null if not found
     * @throws IllegalArgumentException if session is null
     */
    public ConversationMemory removeConversation(HttpSession session) {
        if (session == null) {
            throw new IllegalArgumentException("Session cannot be null");
        }

        try {
            Object attribute = session.getAttribute(CONVERSATION_MEMORY_KEY);
            ConversationMemory memory = null;

            if (attribute instanceof ConversationMemory) {
                memory = (ConversationMemory) attribute;
                session.removeAttribute(CONVERSATION_MEMORY_KEY);
            }

            sessionTracker.remove(session.getId());

            if (memory != null) {
                logger.debug("Removed conversation memory for session: {} with {} messages",
                        session.getId(), memory.getMessageCount());
            } else {
                logger.debug("No conversation memory to remove for session: {}", session.getId());
            }

            return memory;
        } catch (Exception e) {
            logger.error("Failed to remove conversation memory for session: {}", session.getId(), e);
            return null;
        }
    }

    /**
     * Check if conversation memory exists for the session
     * 
     * @param session The HTTP session
     * @return true if conversation memory exists
     * @throws IllegalArgumentException if session is null
     */
    public boolean hasConversation(HttpSession session) {
        if (session == null) {
            throw new IllegalArgumentException("Session cannot be null");
        }

        try {
            boolean exists = session.getAttribute(CONVERSATION_MEMORY_KEY) != null;
            logger.debug("Conversation memory exists for session {}: {}", session.getId(), exists);
            return exists;
        } catch (Exception e) {
            logger.error("Failed to check conversation memory existence for session: {}", session.getId(), e);
            return false;
        }
    }

    /**
     * Get or create conversation memory for the session
     * 
     * @param session The HTTP session
     * @return Existing or new conversation memory
     * @throws IllegalArgumentException if session is null
     */
    public ConversationMemory getOrCreateConversation(HttpSession session) {
        if (session == null) {
            throw new IllegalArgumentException("Session cannot be null");
        }

        try {
            Object attribute = session.getAttribute(CONVERSATION_MEMORY_KEY);
            ConversationMemory memory = null;

            if (attribute instanceof ConversationMemory) {
                memory = (ConversationMemory) attribute;
                sessionTracker.put(session.getId(), LocalDateTime.now());
                logger.debug("Retrieved existing conversation memory for session: {} with {} messages",
                        session.getId(), memory.getMessageCount());
            }

            if (memory == null) {
                memory = new ConversationMemory(session.getId());
                storeConversation(session, memory);
                

                
                logger.debug("Created new conversation memory for session: {}", session.getId());
            }

            return memory;
        } catch (Exception e) {
            logger.error("Failed to get or create conversation memory for session: {}", session.getId(), e);
            // Create new memory as fallback
            ConversationMemory memory = new ConversationMemory(session.getId());
            try {
                storeConversation(session, memory);
            } catch (Exception storeException) {
                logger.error("Failed to store fallback conversation memory for session: {}", session.getId(),
                        storeException);
            }
            return memory;
        }
    }

    /**
     * Update the last activity timestamp for a conversation
     * 
     * @param session The HTTP session
     */
    public void updateLastActivity(HttpSession session) {
        if (session == null) {
            throw new IllegalArgumentException("Session cannot be null");
        }

        ConversationMemory memory = retrieveConversation(session);
        if (memory != null) {
            memory.updateLastActivity();
            storeConversation(session, memory);
            logger.debug("Updated last activity for session: {}", session.getId());
        }
    }

    /**
     * Get the number of tracked sessions
     * 
     * @return Number of active sessions
     */
    public int getActiveSessionCount() {
        return sessionTracker.size();
    }

    /**
     * Truncate conversation if it exceeds configured limits
     * @param memory The conversation memory to potentially truncate
     */
    private void truncateConversationIfNeeded(ConversationMemory memory) {
        if (memory == null) return;
        
        int messageCount = memory.getMessageCount();
        int maxMessages = config.getMaxMessagesPerSession();
        
        if (messageCount > maxMessages) {
            List<ConversationMessage> messages = memory.getMessages();
            
            // Keep the most recent messages, preserving conversation flow
            int messagesToRemove = messageCount - maxMessages;
            
            // Try to remove complete conversation pairs (user + assistant)
            int actualRemoved = 0;
            List<ConversationMessage> newMessages = new ArrayList<>();
            
            // Skip older messages but try to maintain conversation pairs
            for (int i = messagesToRemove; i < messages.size(); i++) {
                newMessages.add(messages.get(i));
            }
            
            // If we removed an odd number and broke a pair, remove one more to maintain pairs
            if (newMessages.size() > 0 && newMessages.size() % 2 == 1) {
                ConversationMessage first = newMessages.get(0);
                if (first.isAssistantMessage()) {
                    newMessages.remove(0); // Remove orphaned assistant message
                }
            }
            
            memory.setMessages(newMessages);
            actualRemoved = messageCount - memory.getMessageCount();
            
            logger.info("Truncated conversation for session, removed {} messages, {} remaining", 
                       actualRemoved, memory.getMessageCount());
        }
    }
    
    /**
     * Perform emergency cleanup when session limits are exceeded
     */
    private void performEmergencyCleanup() {
        try {
            logger.warn("Emergency cleanup triggered - session limit exceeded");
            
            // Remove sessions that haven't been active recently
            LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(config.getSessionExpirationMinutes() / 2);
            
            List<String> sessionsToRemove = sessionTracker.entrySet().stream()
                .filter(entry -> entry.getValue().isBefore(cutoffTime))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
            
            for (String sessionId : sessionsToRemove) {
                sessionTracker.remove(sessionId);
            }
            
            logger.info("Emergency cleanup removed {} sessions", sessionsToRemove.size());
            
            // If still over limit, remove oldest sessions
            if (sessionTracker.size() >= config.getMaxActiveSessions()) {
                int toRemove = sessionTracker.size() - (config.getMaxActiveSessions() * 3 / 4); // Remove to 75% capacity
                
                List<String> oldestSessions = sessionTracker.entrySet().stream()
                    .sorted(Map.Entry.comparingByValue())
                    .limit(toRemove)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
                
                for (String sessionId : oldestSessions) {
                    sessionTracker.remove(sessionId);
                }
                
                logger.info("Emergency cleanup removed {} additional oldest sessions", oldestSessions.size());
            }
            
        } catch (Exception e) {
            logger.error("Error during emergency cleanup", e);
        }
    }
    
    /**
     * Perform cleanup of expired sessions from the tracker
     * This method is called periodically by the cleanup scheduler
     */
    private void performCleanup() {
        try {
            LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(config.getSessionExpirationMinutes());
            
            List<String> expiredSessions = sessionTracker.entrySet().stream()
                .filter(entry -> entry.getValue().isBefore(cutoffTime))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

            // Remove expired sessions from tracker
            for (String sessionId : expiredSessions) {
                sessionTracker.remove(sessionId);
                logger.debug("Cleaned up expired session: {}", sessionId);
            }

            if (!expiredSessions.isEmpty()) {
                logger.info("Cleaned up {} expired sessions", expiredSessions.size());
            }

            logger.debug("Cleanup completed. Active sessions: {}", sessionTracker.size());
            

            
            // Check memory usage and perform additional cleanup if needed
            checkMemoryUsageAndCleanup();
            
        } catch (Exception e) {
            logger.error("Error during session cleanup", e);
        }
    }
    
    /**
     * Check memory usage and perform cleanup if threshold is exceeded
     */
    private void checkMemoryUsageAndCleanup() {
        try {
            Runtime runtime = Runtime.getRuntime();
            long usedMemoryMB = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
            
            if (usedMemoryMB > config.getMaxMemoryUsageMB()) {
                logger.warn("Memory usage ({} MB) exceeds threshold ({} MB), performing additional cleanup", 
                           usedMemoryMB, config.getMaxMemoryUsageMB());
                
                // More aggressive cleanup - remove sessions older than half the expiration time
                LocalDateTime aggressiveCutoff = LocalDateTime.now().minusMinutes(config.getSessionExpirationMinutes() / 2);
                
                List<String> sessionsToRemove = sessionTracker.entrySet().stream()
                    .filter(entry -> entry.getValue().isBefore(aggressiveCutoff))
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
                
                for (String sessionId : sessionsToRemove) {
                    sessionTracker.remove(sessionId);
                }
                
                if (!sessionsToRemove.isEmpty()) {
                    logger.info("Memory cleanup removed {} additional sessions", sessionsToRemove.size());
                }
                
                // Suggest garbage collection
                System.gc();
            }
        } catch (Exception e) {
            logger.error("Error during memory usage check", e);
        }
    }
    
    /**
     * Get memory usage statistics
     * @return Map containing memory usage information
     */
    public Map<String, Object> getMemoryStats() {
        Map<String, Object> stats = new HashMap<>();
        
        Runtime runtime = Runtime.getRuntime();
        long totalMemoryMB = runtime.totalMemory() / (1024 * 1024);
        long freeMemoryMB = runtime.freeMemory() / (1024 * 1024);
        long usedMemoryMB = totalMemoryMB - freeMemoryMB;
        long maxMemoryMB = runtime.maxMemory() / (1024 * 1024);
        
        stats.put("totalMemoryMB", totalMemoryMB);
        stats.put("freeMemoryMB", freeMemoryMB);
        stats.put("usedMemoryMB", usedMemoryMB);
        stats.put("maxMemoryMB", maxMemoryMB);
        stats.put("activeSessions", sessionTracker.size());
        stats.put("maxActiveSessions", config.getMaxActiveSessions());
        stats.put("memoryThresholdMB", config.getMaxMemoryUsageMB());
        
        return stats;
    }
    
    /**
     * Force cleanup of all expired sessions (for testing or manual cleanup)
     * @return Number of sessions cleaned up
     */
    public int forceCleanup() {
        int initialCount = sessionTracker.size();
        performCleanup();
        return initialCount - sessionTracker.size();
    }

    /**
     * Shutdown the cleanup executor
     * Should be called when the application is shutting down
     */
    @PreDestroy
    public void shutdown() {
        if (cleanupExecutor != null && !cleanupExecutor.isShutdown()) {
            cleanupExecutor.shutdown();
            try {
                if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    cleanupExecutor.shutdownNow();
                }
                logger.info("ConversationMemoryStore cleanup executor shutdown completed");
            } catch (InterruptedException e) {
                cleanupExecutor.shutdownNow();
                Thread.currentThread().interrupt();
                logger.warn("ConversationMemoryStore cleanup executor shutdown interrupted", e);
            }
        }
    }
}