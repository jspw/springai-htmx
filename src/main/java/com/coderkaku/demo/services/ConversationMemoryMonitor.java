package com.coderkaku.demo.services;

import com.coderkaku.demo.configs.ConversationMemoryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Component for monitoring conversation memory health and performance metrics.
 * Provides health checks and performance statistics for the conversation memory system.
 */
@Component
public class ConversationMemoryMonitor {

    private static final Logger logger = LoggerFactory.getLogger(ConversationMemoryMonitor.class);
    
    private final ConversationMemoryConfig config;
    
    // Performance metrics
    private final AtomicLong totalConversationsCreated = new AtomicLong(0);
    private final AtomicLong totalMessagesStored = new AtomicLong(0);
    private final AtomicLong totalContextBuilds = new AtomicLong(0);
    private final AtomicLong totalCleanupOperations = new AtomicLong(0);
    private final AtomicLong totalErrors = new AtomicLong(0);
    
    // Timing metrics
    private volatile long lastCleanupTime = 0;
    private volatile long averageContextBuildTimeMs = 0;
    private volatile long maxContextBuildTimeMs = 0;
    
    public ConversationMemoryMonitor(ConversationMemoryConfig config) {
        this.config = config;
        logger.info("ConversationMemoryMonitor initialized");
    }

    /**
     * Get health status information
     * @return Map containing health status and details
     */
    public Map<String, Object> getHealthStatus() {
        try {
            // Get basic memory stats without depending on memory store
            Runtime runtime = Runtime.getRuntime();
            long totalMemoryMB = runtime.totalMemory() / (1024 * 1024);
            long freeMemoryMB = runtime.freeMemory() / (1024 * 1024);
            long usedMemoryMB = totalMemoryMB - freeMemoryMB;
            
            // Check if system is healthy
            boolean isHealthy = checkSystemHealth(usedMemoryMB);
            
            Map<String, Object> healthInfo = new HashMap<>();
            healthInfo.put("status", isHealthy ? "UP" : "DOWN");
            healthInfo.put("memoryUsageMB", usedMemoryMB);
            healthInfo.put("memoryThresholdMB", config.getMaxMemoryUsageMB());
            healthInfo.put("totalMemoryMB", totalMemoryMB);
            healthInfo.put("freeMemoryMB", freeMemoryMB);
            healthInfo.put("automaticCleanupEnabled", config.isEnableAutomaticCleanup());
            healthInfo.put("lastCleanupTime", lastCleanupTime > 0 ? 
                LocalDateTime.ofEpochSecond(lastCleanupTime / 1000, 0, java.time.ZoneOffset.UTC)
                    .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : "Never");
            healthInfo.put("totalConversationsCreated", totalConversationsCreated.get());
            healthInfo.put("totalMessagesStored", totalMessagesStored.get());
            healthInfo.put("totalErrors", totalErrors.get());
            
            return healthInfo;
            
        } catch (Exception e) {
            logger.error("Error checking conversation memory health", e);
            recordError();
            Map<String, Object> errorInfo = new HashMap<>();
            errorInfo.put("status", "DOWN");
            errorInfo.put("error", "Failed to check system health");
            errorInfo.put("exception", e.getMessage());
            return errorInfo;
        }
    }

    /**
     * Get system information
     * @return Map containing configuration and status information
     */
    public Map<String, Object> getSystemInfo() {
        try {
            Map<String, Object> conversationMemoryInfo = new HashMap<>();
            
            // Configuration information
            Map<String, Object> configInfo = new HashMap<>();
            configInfo.put("maxMessagesPerSession", config.getMaxMessagesPerSession());
            configInfo.put("maxContextMessages", config.getMaxContextMessages());
            configInfo.put("cleanupIntervalMinutes", config.getCleanupIntervalMinutes());
            configInfo.put("sessionExpirationMinutes", config.getSessionExpirationMinutes());
            configInfo.put("maxActiveSessions", config.getMaxActiveSessions());
            configInfo.put("automaticCleanupEnabled", config.isEnableAutomaticCleanup());
            configInfo.put("maxMemoryUsageMB", config.getMaxMemoryUsageMB());
            
            // Performance metrics
            Map<String, Object> performanceInfo = new HashMap<>();
            performanceInfo.put("totalConversationsCreated", totalConversationsCreated.get());
            performanceInfo.put("totalMessagesStored", totalMessagesStored.get());
            performanceInfo.put("totalContextBuilds", totalContextBuilds.get());
            performanceInfo.put("totalCleanupOperations", totalCleanupOperations.get());
            performanceInfo.put("totalErrors", totalErrors.get());
            performanceInfo.put("averageContextBuildTimeMs", averageContextBuildTimeMs);
            performanceInfo.put("maxContextBuildTimeMs", maxContextBuildTimeMs);
            
            // Current status
            Runtime runtime = Runtime.getRuntime();
            long totalMemoryMB = runtime.totalMemory() / (1024 * 1024);
            long freeMemoryMB = runtime.freeMemory() / (1024 * 1024);
            long usedMemoryMB = totalMemoryMB - freeMemoryMB;
            
            Map<String, Object> statusInfo = new HashMap<>();
            statusInfo.put("memoryUsageMB", usedMemoryMB);
            statusInfo.put("memoryUtilizationPercent", calculateMemoryUtilization(usedMemoryMB, totalMemoryMB));
            
            conversationMemoryInfo.put("configuration", configInfo);
            conversationMemoryInfo.put("performance", performanceInfo);
            conversationMemoryInfo.put("status", statusInfo);
            
            return conversationMemoryInfo;
            
        } catch (Exception e) {
            logger.error("Error gathering conversation memory info", e);
            recordError();
            return Map.of("error", "Failed to gather info");
        }
    }
    
    /**
     * Record that a new conversation was created
     */
    public void recordConversationCreated() {
        totalConversationsCreated.incrementAndGet();
        logger.debug("Conversation created. Total: {}", totalConversationsCreated.get());
    }
    
    /**
     * Record that a message was stored
     */
    public void recordMessageStored() {
        totalMessagesStored.incrementAndGet();
        if (totalMessagesStored.get() % 100 == 0) {
            logger.info("Messages stored milestone: {}", totalMessagesStored.get());
        }
    }
    
    /**
     * Record context build operation with timing
     * @param durationMs Duration of the context build operation in milliseconds
     */
    public void recordContextBuild(long durationMs) {
        totalContextBuilds.incrementAndGet();
        
        // Update timing metrics
        if (durationMs > maxContextBuildTimeMs) {
            maxContextBuildTimeMs = durationMs;
        }
        
        // Simple moving average calculation
        long totalBuilds = totalContextBuilds.get();
        averageContextBuildTimeMs = (averageContextBuildTimeMs * (totalBuilds - 1) + durationMs) / totalBuilds;
        
        if (durationMs > 1000) { // Log slow operations
            logger.warn("Slow context build operation: {}ms", durationMs);
        }
        
        logger.debug("Context build completed in {}ms. Total builds: {}", durationMs, totalBuilds);
    }
    
    /**
     * Record cleanup operation
     */
    public void recordCleanupOperation() {
        totalCleanupOperations.incrementAndGet();
        lastCleanupTime = System.currentTimeMillis();
        logger.info("Cleanup operation completed. Total cleanups: {}", totalCleanupOperations.get());
    }
    
    /**
     * Record an error occurrence
     */
    public void recordError() {
        long errorCount = totalErrors.incrementAndGet();
        logger.warn("Error recorded. Total errors: {}", errorCount);
        
        // Log warning if error rate is high
        if (errorCount % 10 == 0) {
            logger.warn("High error count detected: {} errors", errorCount);
        }
    }
    
    /**
     * Get performance metrics as a map
     * @return Map containing performance metrics
     */
    public Map<String, Object> getPerformanceMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        
        metrics.put("totalConversationsCreated", totalConversationsCreated.get());
        metrics.put("totalMessagesStored", totalMessagesStored.get());
        metrics.put("totalContextBuilds", totalContextBuilds.get());
        metrics.put("totalCleanupOperations", totalCleanupOperations.get());
        metrics.put("totalErrors", totalErrors.get());
        metrics.put("averageContextBuildTimeMs", averageContextBuildTimeMs);
        metrics.put("maxContextBuildTimeMs", maxContextBuildTimeMs);
        metrics.put("lastCleanupTime", lastCleanupTime);
        
        // Add basic memory stats
        Runtime runtime = Runtime.getRuntime();
        long totalMemoryMB = runtime.totalMemory() / (1024 * 1024);
        long freeMemoryMB = runtime.freeMemory() / (1024 * 1024);
        long usedMemoryMB = totalMemoryMB - freeMemoryMB;
        
        metrics.put("totalMemoryMB", totalMemoryMB);
        metrics.put("freeMemoryMB", freeMemoryMB);
        metrics.put("usedMemoryMB", usedMemoryMB);
        metrics.put("memoryThresholdMB", config.getMaxMemoryUsageMB());
        
        // Add utilization percentages
        metrics.put("memoryUtilizationPercent", calculateMemoryUtilization(usedMemoryMB, totalMemoryMB));
        
        return metrics;
    }
    
    /**
     * Reset performance counters (useful for testing)
     */
    public void resetMetrics() {
        totalConversationsCreated.set(0);
        totalMessagesStored.set(0);
        totalContextBuilds.set(0);
        totalCleanupOperations.set(0);
        totalErrors.set(0);
        lastCleanupTime = 0;
        averageContextBuildTimeMs = 0;
        maxContextBuildTimeMs = 0;
        logger.info("Performance metrics reset");
    }
    
    /**
     * Check if the conversation memory system is healthy
     * @param usedMemoryMB Current memory usage in MB
     * @return true if system is healthy
     */
    private boolean checkSystemHealth(long usedMemoryMB) {
        try {
            // Check memory usage
            if (usedMemoryMB > config.getMaxMemoryUsageMB() * 1.2) {
                logger.warn("Memory usage ({} MB) significantly exceeds threshold ({} MB)", 
                           usedMemoryMB, config.getMaxMemoryUsageMB());
                return false;
            }
            
            // Check error rate
            long totalOperations = totalConversationsCreated.get() + totalMessagesStored.get() + totalContextBuilds.get();
            long errorCount = totalErrors.get();
            if (totalOperations >= 100 && errorCount > 0) {
                double errorRate = (double) errorCount / totalOperations;
                if (errorRate > 0.05) { // 5% error rate threshold
                    logger.warn("High error rate detected: {} errors out of {} operations ({}%)", 
                               errorCount, totalOperations, errorRate * 100);
                    return false;
                }
            }
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error checking system health", e);
            return false;
        }
    }
    
    /**
     * Calculate memory utilization percentage
     * @param usedMemoryMB Used memory in MB
     * @param totalMemoryMB Total memory in MB
     * @return Memory utilization percentage
     */
    private double calculateMemoryUtilization(long usedMemoryMB, long totalMemoryMB) {
        try {
            if (totalMemoryMB > 0) {
                return (double) usedMemoryMB / totalMemoryMB * 100.0;
            }
        } catch (Exception e) {
            logger.debug("Error calculating memory utilization", e);
        }
        return 0.0;
    }
}