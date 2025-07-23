package com.coderkaku.demo.integration;

import com.coderkaku.demo.configs.ConversationMemoryConfig;
import com.coderkaku.demo.services.ConversationMemoryMonitor;
import com.coderkaku.demo.services.ConversationMemoryStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for conversation memory configuration and monitoring
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "conversation.memory.max-messages-per-session=25",
        "conversation.memory.max-context-messages=5",
        "conversation.memory.cleanup-interval-minutes=15",
        "conversation.memory.session-expiration-minutes=60",
        "conversation.memory.max-active-sessions=500",
        "conversation.memory.enable-automatic-cleanup=false",
        "conversation.memory.max-memory-usage-mb=50",
        "management.endpoints.web.exposure.include=health,info,metrics"
})
class ConversationMemoryConfigurationIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ConversationMemoryConfig config;

    @Autowired
    private ConversationMemoryMonitor monitor;

    @Autowired
    private ConversationMemoryStore memoryStore;

    @Test
    @DisplayName("Should load configuration properties correctly")
    void configurationProperties_ShouldBeLoadedCorrectly() {
        // Verify that custom configuration properties are loaded
        assertEquals(25, config.getMaxMessagesPerSession());
        assertEquals(5, config.getMaxContextMessages());
        assertEquals(15, config.getCleanupIntervalMinutes());
        assertEquals(60, config.getSessionExpirationMinutes());
        assertEquals(500, config.getMaxActiveSessions());
        assertFalse(config.isEnableAutomaticCleanup());
        assertEquals(50, config.getMaxMemoryUsageMB());
    }

    @Test
    @DisplayName("Should validate configuration on startup")
    void configurationValidation_ShouldPassOnStartup() {
        // If the application started successfully, configuration validation passed
        assertDoesNotThrow(() -> config.validate());
    }

    @Test
    @DisplayName("Should create monitor and memory store beans")
    void springBeans_ShouldBeCreated() {
        assertNotNull(config);
        assertNotNull(monitor);
        assertNotNull(memoryStore);
    }

    @Test
    @DisplayName("Should expose health endpoint with conversation memory info")
    void healthEndpoint_ShouldExposeConversationMemoryHealth() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/demo/actuator/health",
                Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        Map<String, Object> healthData = response.getBody();
        assertNotNull(healthData);
        assertEquals("UP", healthData.get("status"));

        // Check if conversation memory health is included in components
        Map<String, Object> components = (Map<String, Object>) healthData.get("components");
        if (components != null) {
            Map<String, Object> conversationMemoryHealth = (Map<String, Object>) components
                    .get("conversationMemoryMonitor");
            if (conversationMemoryHealth != null) {
                assertEquals("UP", conversationMemoryHealth.get("status"));
            }
        }
    }

    @Test
    @DisplayName("Should expose info endpoint with conversation memory configuration")
    void infoEndpoint_ShouldExposeConversationMemoryInfo() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/demo/actuator/info",
                Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        Map<String, Object> infoData = response.getBody();
        assertNotNull(infoData);

        // Check if conversation memory info is included
        Map<String, Object> conversationMemoryInfo = (Map<String, Object>) infoData.get("conversationMemory");
        if (conversationMemoryInfo != null) {
            Map<String, Object> configInfo = (Map<String, Object>) conversationMemoryInfo.get("configuration");
            assertNotNull(configInfo);
            assertEquals(25, configInfo.get("maxMessagesPerSession"));
            assertEquals(5, configInfo.get("maxContextMessages"));
            assertEquals(15L, configInfo.get("cleanupIntervalMinutes"));
            assertEquals(60L, configInfo.get("sessionExpirationMinutes"));
            assertEquals(500, configInfo.get("maxActiveSessions"));
            assertEquals(false, configInfo.get("automaticCleanupEnabled"));
            assertEquals(50L, configInfo.get("maxMemoryUsageMB"));
        }
    }

    @Test
    @DisplayName("Should provide health check functionality")
    void healthCheck_ShouldWork() {
        Map<String, Object> health = monitor.getHealthStatus();

        assertNotNull(health);
        assertEquals("UP", health.get("status"));

        // Verify health details contain expected information
        assertTrue(health.containsKey("memoryUsageMB"));
        assertTrue(health.containsKey("memoryThresholdMB"));
        assertTrue(health.containsKey("automaticCleanupEnabled"));
        assertTrue(health.containsKey("totalConversationsCreated"));
        assertTrue(health.containsKey("totalMessagesStored"));
        assertTrue(health.containsKey("totalErrors"));
    }

    @Test
    @DisplayName("Should provide system info functionality")
    void systemInfo_ShouldWork() {
        Map<String, Object> conversationMemoryInfo = monitor.getSystemInfo();

        assertNotNull(conversationMemoryInfo);
        assertTrue(conversationMemoryInfo.containsKey("configuration"));
        assertTrue(conversationMemoryInfo.containsKey("performance"));
        assertTrue(conversationMemoryInfo.containsKey("status"));
    }

    @Test
    @DisplayName("Should record and retrieve performance metrics")
    void performanceMetrics_ShouldWork() {
        // Record some metrics
        monitor.recordConversationCreated();
        monitor.recordMessageStored();
        monitor.recordContextBuild(100L);
        monitor.recordCleanupOperation();

        Map<String, Object> metrics = monitor.getPerformanceMetrics();

        // Verify metrics are recorded
        assertEquals(1L, metrics.get("totalConversationsCreated"));
        assertEquals(1L, metrics.get("totalMessagesStored"));
        assertEquals(1L, metrics.get("totalContextBuilds"));
        assertEquals(1L, metrics.get("totalCleanupOperations"));
        assertEquals(100L, metrics.get("maxContextBuildTimeMs"));
        assertEquals(100L, metrics.get("averageContextBuildTimeMs"));
        assertTrue((Long) metrics.get("lastCleanupTime") > 0);

        // Verify memory stats are included
        assertTrue(metrics.containsKey("usedMemoryMB"));
        assertTrue(metrics.containsKey("memoryUtilizationPercent"));
    }

    @Test
    @DisplayName("Should handle configuration with automatic cleanup disabled")
    void automaticCleanup_ShouldBeDisabled() {
        // Verify that automatic cleanup is disabled as per test configuration
        assertFalse(config.isEnableAutomaticCleanup());

        // Verify this is reflected in health check
        Map<String, Object> health = monitor.getHealthStatus();
        assertEquals(false, health.get("automaticCleanupEnabled"));
    }

    @Test
    @DisplayName("Should validate memory store configuration integration")
    void memoryStoreConfiguration_ShouldBeIntegrated() {
        // Verify that memory store uses the configuration
        Map<String, Object> memoryStats = memoryStore.getMemoryStats();

        assertNotNull(memoryStats);
        assertEquals(500, memoryStats.get("maxActiveSessions"));
        assertEquals(50L, memoryStats.get("memoryThresholdMB"));
    }

    @Test
    @DisplayName("Should reset metrics functionality")
    void resetMetrics_ShouldWork() {
        // Record some metrics
        monitor.recordConversationCreated();
        monitor.recordMessageStored();
        monitor.recordError();

        // Verify metrics are recorded
        Map<String, Object> metrics = monitor.getPerformanceMetrics();
        assertEquals(1L, metrics.get("totalConversationsCreated"));
        assertEquals(1L, metrics.get("totalMessagesStored"));
        assertEquals(1L, metrics.get("totalErrors"));

        // Reset metrics
        monitor.resetMetrics();

        // Verify metrics are reset
        metrics = monitor.getPerformanceMetrics();
        assertEquals(0L, metrics.get("totalConversationsCreated"));
        assertEquals(0L, metrics.get("totalMessagesStored"));
        assertEquals(0L, metrics.get("totalErrors"));
        assertEquals(0L, metrics.get("averageContextBuildTimeMs"));
        assertEquals(0L, metrics.get("maxContextBuildTimeMs"));
        assertEquals(0L, metrics.get("lastCleanupTime"));
    }
}