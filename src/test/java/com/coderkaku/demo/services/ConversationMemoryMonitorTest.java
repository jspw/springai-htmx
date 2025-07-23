package com.coderkaku.demo.services;

import com.coderkaku.demo.configs.ConversationMemoryConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;


import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ConversationMemoryMonitor
 */
class ConversationMemoryMonitorTest {
    
    @Mock
    private ConversationMemoryConfig config;
    
    private ConversationMemoryMonitor monitor;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Setup default config values
        when(config.getMaxMessagesPerSession()).thenReturn(50);
        when(config.getMaxContextMessages()).thenReturn(10);
        when(config.getCleanupIntervalMinutes()).thenReturn(30L);
        when(config.getSessionExpirationMinutes()).thenReturn(120L);
        when(config.getMaxActiveSessions()).thenReturn(1000);
        when(config.isEnableAutomaticCleanup()).thenReturn(true);
        when(config.getMaxMemoryUsageMB()).thenReturn(100L);
        
        monitor = new ConversationMemoryMonitor(config);
    }
    
    @Test
    @DisplayName("Should record conversation creation")
    void recordConversationCreated_ShouldIncrementCounter() {
        // Initial state
        Map<String, Object> initialMetrics = monitor.getPerformanceMetrics();
        assertEquals(0L, initialMetrics.get("totalConversationsCreated"));
        
        // Record conversation creation
        monitor.recordConversationCreated();
        
        // Verify counter incremented
        Map<String, Object> updatedMetrics = monitor.getPerformanceMetrics();
        assertEquals(1L, updatedMetrics.get("totalConversationsCreated"));
        
        // Record another
        monitor.recordConversationCreated();
        updatedMetrics = monitor.getPerformanceMetrics();
        assertEquals(2L, updatedMetrics.get("totalConversationsCreated"));
    }
    
    @Test
    @DisplayName("Should record message storage")
    void recordMessageStored_ShouldIncrementCounter() {
        // Initial state
        Map<String, Object> initialMetrics = monitor.getPerformanceMetrics();
        assertEquals(0L, initialMetrics.get("totalMessagesStored"));
        
        // Record message storage
        monitor.recordMessageStored();
        
        // Verify counter incremented
        Map<String, Object> updatedMetrics = monitor.getPerformanceMetrics();
        assertEquals(1L, updatedMetrics.get("totalMessagesStored"));
    }
    
    @Test
    @DisplayName("Should record context build with timing")
    void recordContextBuild_ShouldUpdateTimingMetrics() {
        // Record context builds with different durations
        monitor.recordContextBuild(100L);
        monitor.recordContextBuild(200L);
        monitor.recordContextBuild(300L);
        
        Map<String, Object> metrics = monitor.getPerformanceMetrics();
        
        // Verify counters and timing
        assertEquals(3L, metrics.get("totalContextBuilds"));
        assertEquals(300L, metrics.get("maxContextBuildTimeMs"));
        assertEquals(200L, metrics.get("averageContextBuildTimeMs")); // (100+200+300)/3 = 200
    }
    
    @Test
    @DisplayName("Should record cleanup operations")
    void recordCleanupOperation_ShouldIncrementCounter() {
        // Initial state
        Map<String, Object> initialMetrics = monitor.getPerformanceMetrics();
        assertEquals(0L, initialMetrics.get("totalCleanupOperations"));
        
        // Record cleanup
        monitor.recordCleanupOperation();
        
        // Verify counter incremented and timestamp updated
        Map<String, Object> updatedMetrics = monitor.getPerformanceMetrics();
        assertEquals(1L, updatedMetrics.get("totalCleanupOperations"));
        assertTrue((Long) updatedMetrics.get("lastCleanupTime") > 0);
    }
    
    @Test
    @DisplayName("Should record errors")
    void recordError_ShouldIncrementCounter() {
        // Initial state
        Map<String, Object> initialMetrics = monitor.getPerformanceMetrics();
        assertEquals(0L, initialMetrics.get("totalErrors"));
        
        // Record error
        monitor.recordError();
        
        // Verify counter incremented
        Map<String, Object> updatedMetrics = monitor.getPerformanceMetrics();
        assertEquals(1L, updatedMetrics.get("totalErrors"));
    }
    
    @Test
    @DisplayName("Should reset metrics")
    void resetMetrics_ShouldClearAllCounters() {
        // Record some metrics
        monitor.recordConversationCreated();
        monitor.recordMessageStored();
        monitor.recordContextBuild(100L);
        monitor.recordCleanupOperation();
        monitor.recordError();
        
        // Verify metrics are recorded
        Map<String, Object> metrics = monitor.getPerformanceMetrics();
        assertEquals(1L, metrics.get("totalConversationsCreated"));
        assertEquals(1L, metrics.get("totalMessagesStored"));
        assertEquals(1L, metrics.get("totalContextBuilds"));
        assertEquals(1L, metrics.get("totalCleanupOperations"));
        assertEquals(1L, metrics.get("totalErrors"));
        
        // Reset metrics
        monitor.resetMetrics();
        
        // Verify all metrics are reset
        metrics = monitor.getPerformanceMetrics();
        assertEquals(0L, metrics.get("totalConversationsCreated"));
        assertEquals(0L, metrics.get("totalMessagesStored"));
        assertEquals(0L, metrics.get("totalContextBuilds"));
        assertEquals(0L, metrics.get("totalCleanupOperations"));
        assertEquals(0L, metrics.get("totalErrors"));
        assertEquals(0L, metrics.get("averageContextBuildTimeMs"));
        assertEquals(0L, metrics.get("maxContextBuildTimeMs"));
        assertEquals(0L, metrics.get("lastCleanupTime"));
    }
    
    @Test
    @DisplayName("Should return healthy status when system is normal")
    void getHealthStatus_NormalConditions_ShouldReturnHealthy() {
        Map<String, Object> health = monitor.getHealthStatus();
        
        assertEquals("UP", health.get("status"));
        assertTrue(health.containsKey("memoryUsageMB"));
        assertTrue(health.containsKey("memoryThresholdMB"));
        assertTrue(health.containsKey("totalMemoryMB"));
        assertTrue(health.containsKey("freeMemoryMB"));
    }
    
    @Test
    @DisplayName("Should return unhealthy status when error rate is too high")
    void getHealthStatus_HighErrorRate_ShouldReturnUnhealthy() {
        // Record operations and errors to create high error rate
        for (int i = 0; i < 100; i++) {
            monitor.recordConversationCreated();
        }
        for (int i = 0; i < 10; i++) { // 10% error rate (exceeds 5% threshold)
            monitor.recordError();
        }
        
        Map<String, Object> health = monitor.getHealthStatus();
        
        assertEquals("DOWN", health.get("status"));
    }
    
    @Test
    @DisplayName("Should handle health check gracefully")
    void getHealthStatus_ShouldWork() {
        Map<String, Object> health = monitor.getHealthStatus();
        
        // Should return UP status for normal conditions
        assertEquals("UP", health.get("status"));
        assertTrue(health.containsKey("memoryUsageMB"));
        assertTrue(health.containsKey("memoryThresholdMB"));
    }
    

    
    @Test
    @DisplayName("Should provide system configuration info")
    void getSystemInfo_ShouldProvideConfigurationInfo() {
        Map<String, Object> conversationMemoryInfo = monitor.getSystemInfo();
        
        assertNotNull(conversationMemoryInfo);
        
        // Check configuration info
        Map<String, Object> configInfo = (Map<String, Object>) conversationMemoryInfo.get("configuration");
        assertEquals(50, configInfo.get("maxMessagesPerSession"));
        assertEquals(10, configInfo.get("maxContextMessages"));
        assertEquals(30L, configInfo.get("cleanupIntervalMinutes"));
        assertEquals(120L, configInfo.get("sessionExpirationMinutes"));
        assertEquals(1000, configInfo.get("maxActiveSessions"));
        assertEquals(true, configInfo.get("automaticCleanupEnabled"));
        assertEquals(100L, configInfo.get("maxMemoryUsageMB"));
        
        // Check performance info exists
        Map<String, Object> performanceInfo = (Map<String, Object>) conversationMemoryInfo.get("performance");
        assertNotNull(performanceInfo);
        assertTrue(performanceInfo.containsKey("totalConversationsCreated"));
        assertTrue(performanceInfo.containsKey("totalMessagesStored"));
        
        // Check status info exists
        Map<String, Object> statusInfo = (Map<String, Object>) conversationMemoryInfo.get("status");
        assertNotNull(statusInfo);
    }
    
    @Test
    @DisplayName("Should get performance metrics with memory stats")
    void getPerformanceMetrics_ShouldIncludeMemoryStats() {
        Map<String, Object> metrics = monitor.getPerformanceMetrics();
        
        // Verify basic metrics are included
        assertTrue(metrics.containsKey("totalConversationsCreated"));
        assertTrue(metrics.containsKey("totalMessagesStored"));
        assertTrue(metrics.containsKey("totalContextBuilds"));
        assertTrue(metrics.containsKey("totalCleanupOperations"));
        assertTrue(metrics.containsKey("totalErrors"));
        
        // Verify memory stats are included
        assertTrue(metrics.containsKey("usedMemoryMB"));
        assertTrue(metrics.containsKey("totalMemoryMB"));
        assertTrue(metrics.containsKey("memoryThresholdMB"));
        
        // Verify utilization percentages are calculated
        assertTrue(metrics.containsKey("memoryUtilizationPercent"));
    }
}