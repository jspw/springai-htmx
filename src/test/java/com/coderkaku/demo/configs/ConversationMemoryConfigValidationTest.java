package com.coderkaku.demo.configs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for configuration validation and property binding
 */
class ConversationMemoryConfigValidationTest {
    
    @Test
    @DisplayName("Should bind valid configuration properties")
    void bindValidConfiguration_ShouldSucceed() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("conversation.memory.max-messages-per-session", "100");
        properties.put("conversation.memory.max-context-messages", "20");
        properties.put("conversation.memory.cleanup-interval-minutes", "15");
        properties.put("conversation.memory.session-expiration-minutes", "60");
        properties.put("conversation.memory.max-active-sessions", "2000");
        properties.put("conversation.memory.enable-automatic-cleanup", "true");
        properties.put("conversation.memory.max-memory-usage-mb", "200");
        
        ConfigurationPropertySource source = new MapConfigurationPropertySource(properties);
        Binder binder = new Binder(source);
        
        ConversationMemoryConfig config = binder.bind("conversation.memory", ConversationMemoryConfig.class).get();
        
        assertEquals(100, config.getMaxMessagesPerSession());
        assertEquals(20, config.getMaxContextMessages());
        assertEquals(15, config.getCleanupIntervalMinutes());
        assertEquals(60, config.getSessionExpirationMinutes());
        assertEquals(2000, config.getMaxActiveSessions());
        assertTrue(config.isEnableAutomaticCleanup());
        assertEquals(200, config.getMaxMemoryUsageMB());
        
        // Should validate successfully
        assertDoesNotThrow(() -> config.validate());
    }
    
    @Test
    @DisplayName("Should handle missing properties with defaults")
    void bindPartialConfiguration_ShouldUseDefaults() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("conversation.memory.max-messages-per-session", "75");
        // Other properties will use defaults
        
        ConfigurationPropertySource source = new MapConfigurationPropertySource(properties);
        Binder binder = new Binder(source);
        
        ConversationMemoryConfig config = binder.bind("conversation.memory", ConversationMemoryConfig.class).get();
        
        assertEquals(75, config.getMaxMessagesPerSession());
        assertEquals(10, config.getMaxContextMessages()); // default
        assertEquals(30, config.getCleanupIntervalMinutes()); // default
        assertEquals(120, config.getSessionExpirationMinutes()); // default
        assertEquals(1000, config.getMaxActiveSessions()); // default
        assertTrue(config.isEnableAutomaticCleanup()); // default
        assertEquals(100, config.getMaxMemoryUsageMB()); // default
        
        // Should validate successfully
        assertDoesNotThrow(() -> config.validate());
    }
    
    @Test
    @DisplayName("Should validate configuration with edge case values")
    void validateEdgeCaseConfiguration_ShouldWork() {
        ConversationMemoryConfig config = new ConversationMemoryConfig();
        
        // Set edge case values that should still be valid
        config.setMaxMessagesPerSession(1);
        config.setMaxContextMessages(1);
        config.setCleanupIntervalMinutes(1);
        config.setSessionExpirationMinutes(2); // Must be greater than cleanup interval
        config.setMaxActiveSessions(1);
        config.setMaxMemoryUsageMB(1);
        
        assertDoesNotThrow(() -> config.validate());
    }
    
    @Test
    @DisplayName("Should fail validation when context messages exceed total messages")
    void validateInvalidContextMessages_ShouldFail() {
        ConversationMemoryConfig config = new ConversationMemoryConfig();
        config.setMaxMessagesPerSession(5);
        config.setMaxContextMessages(10); // Exceeds max messages
        
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> config.validate()
        );
        assertEquals("Max context messages cannot exceed max messages per session", exception.getMessage());
    }
    
    @Test
    @DisplayName("Should fail validation when cleanup interval is too long")
    void validateInvalidCleanupInterval_ShouldFail() {
        ConversationMemoryConfig config = new ConversationMemoryConfig();
        config.setCleanupIntervalMinutes(120);
        config.setSessionExpirationMinutes(60); // Less than cleanup interval
        
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> config.validate()
        );
        assertEquals("Cleanup interval should be less than session expiration time", exception.getMessage());
    }
    
    @Test
    @DisplayName("Should fail validation when cleanup interval equals expiration time")
    void validateEqualCleanupAndExpiration_ShouldFail() {
        ConversationMemoryConfig config = new ConversationMemoryConfig();
        config.setCleanupIntervalMinutes(60);
        config.setSessionExpirationMinutes(60); // Equal to cleanup interval
        
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> config.validate()
        );
        assertEquals("Cleanup interval should be less than session expiration time", exception.getMessage());
    }
    
    @Test
    @DisplayName("Should handle boolean property binding correctly")
    void bindBooleanProperties_ShouldWork() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("conversation.memory.enable-automatic-cleanup", "false");
        
        ConfigurationPropertySource source = new MapConfigurationPropertySource(properties);
        Binder binder = new Binder(source);
        
        ConversationMemoryConfig config = binder.bind("conversation.memory", ConversationMemoryConfig.class).get();
        
        assertFalse(config.isEnableAutomaticCleanup());
    }
    
    @Test
    @DisplayName("Should handle numeric property binding correctly")
    void bindNumericProperties_ShouldWork() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("conversation.memory.max-messages-per-session", 150);
        properties.put("conversation.memory.cleanup-interval-minutes", 45L);
        
        ConfigurationPropertySource source = new MapConfigurationPropertySource(properties);
        Binder binder = new Binder(source);
        
        ConversationMemoryConfig config = binder.bind("conversation.memory", ConversationMemoryConfig.class).get();
        
        assertEquals(150, config.getMaxMessagesPerSession());
        assertEquals(45, config.getCleanupIntervalMinutes());
    }
    
    @Test
    @DisplayName("Should validate realistic production configuration")
    void validateProductionConfiguration_ShouldSucceed() {
        ConversationMemoryConfig config = new ConversationMemoryConfig();
        
        // Realistic production values
        config.setMaxMessagesPerSession(200);
        config.setMaxContextMessages(50);
        config.setCleanupIntervalMinutes(60); // 1 hour
        config.setSessionExpirationMinutes(480); // 8 hours
        config.setMaxActiveSessions(10000);
        config.setMaxMemoryUsageMB(512); // 512 MB
        config.setEnableAutomaticCleanup(true);
        
        assertDoesNotThrow(() -> config.validate());
        
        // Verify all values are set correctly
        assertEquals(200, config.getMaxMessagesPerSession());
        assertEquals(50, config.getMaxContextMessages());
        assertEquals(60, config.getCleanupIntervalMinutes());
        assertEquals(480, config.getSessionExpirationMinutes());
        assertEquals(10000, config.getMaxActiveSessions());
        assertEquals(512, config.getMaxMemoryUsageMB());
        assertTrue(config.isEnableAutomaticCleanup());
    }
    
    @Test
    @DisplayName("Should validate minimal configuration")
    void validateMinimalConfiguration_ShouldSucceed() {
        ConversationMemoryConfig config = new ConversationMemoryConfig();
        
        // Minimal but valid values
        config.setMaxMessagesPerSession(2);
        config.setMaxContextMessages(1);
        config.setCleanupIntervalMinutes(1);
        config.setSessionExpirationMinutes(5);
        config.setMaxActiveSessions(10);
        config.setMaxMemoryUsageMB(10);
        config.setEnableAutomaticCleanup(false);
        
        assertDoesNotThrow(() -> config.validate());
    }
}