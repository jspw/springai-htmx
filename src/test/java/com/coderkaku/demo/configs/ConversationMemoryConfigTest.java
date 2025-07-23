package com.coderkaku.demo.configs;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ConversationMemoryConfig
 */
class ConversationMemoryConfigTest {
    
    private ConversationMemoryConfig config;
    
    @BeforeEach
    void setUp() {
        config = new ConversationMemoryConfig();
    }
    
    @Test
    @DisplayName("Should have default values")
    void defaultValues_ShouldBeSet() {
        assertEquals(50, config.getMaxMessagesPerSession());
        assertEquals(10, config.getMaxContextMessages());
        assertEquals(30, config.getCleanupIntervalMinutes());
        assertEquals(120, config.getSessionExpirationMinutes());
        assertEquals(1000, config.getMaxActiveSessions());
        assertTrue(config.isEnableAutomaticCleanup());
        assertEquals(100, config.getMaxMemoryUsageMB());
    }
    
    @Test
    @DisplayName("Should validate positive values for max messages per session")
    void setMaxMessagesPerSession_PositiveValue_ShouldSucceed() {
        config.setMaxMessagesPerSession(100);
        assertEquals(100, config.getMaxMessagesPerSession());
    }
    
    @Test
    @DisplayName("Should throw exception for non-positive max messages per session")
    void setMaxMessagesPerSession_NonPositiveValue_ShouldThrowException() {
        assertThrows(IllegalArgumentException.class, () -> config.setMaxMessagesPerSession(0));
        assertThrows(IllegalArgumentException.class, () -> config.setMaxMessagesPerSession(-1));
    }
    
    @Test
    @DisplayName("Should validate positive values for max context messages")
    void setMaxContextMessages_PositiveValue_ShouldSucceed() {
        config.setMaxContextMessages(20);
        assertEquals(20, config.getMaxContextMessages());
    }
    
    @Test
    @DisplayName("Should throw exception for non-positive max context messages")
    void setMaxContextMessages_NonPositiveValue_ShouldThrowException() {
        assertThrows(IllegalArgumentException.class, () -> config.setMaxContextMessages(0));
        assertThrows(IllegalArgumentException.class, () -> config.setMaxContextMessages(-1));
    }
    
    @Test
    @DisplayName("Should validate positive values for cleanup interval")
    void setCleanupIntervalMinutes_PositiveValue_ShouldSucceed() {
        config.setCleanupIntervalMinutes(60);
        assertEquals(60, config.getCleanupIntervalMinutes());
    }
    
    @Test
    @DisplayName("Should throw exception for non-positive cleanup interval")
    void setCleanupIntervalMinutes_NonPositiveValue_ShouldThrowException() {
        assertThrows(IllegalArgumentException.class, () -> config.setCleanupIntervalMinutes(0));
        assertThrows(IllegalArgumentException.class, () -> config.setCleanupIntervalMinutes(-1));
    }
    
    @Test
    @DisplayName("Should validate positive values for session expiration")
    void setSessionExpirationMinutes_PositiveValue_ShouldSucceed() {
        config.setSessionExpirationMinutes(180);
        assertEquals(180, config.getSessionExpirationMinutes());
    }
    
    @Test
    @DisplayName("Should throw exception for non-positive session expiration")
    void setSessionExpirationMinutes_NonPositiveValue_ShouldThrowException() {
        assertThrows(IllegalArgumentException.class, () -> config.setSessionExpirationMinutes(0));
        assertThrows(IllegalArgumentException.class, () -> config.setSessionExpirationMinutes(-1));
    }
    
    @Test
    @DisplayName("Should validate positive values for max active sessions")
    void setMaxActiveSessions_PositiveValue_ShouldSucceed() {
        config.setMaxActiveSessions(2000);
        assertEquals(2000, config.getMaxActiveSessions());
    }
    
    @Test
    @DisplayName("Should throw exception for non-positive max active sessions")
    void setMaxActiveSessions_NonPositiveValue_ShouldThrowException() {
        assertThrows(IllegalArgumentException.class, () -> config.setMaxActiveSessions(0));
        assertThrows(IllegalArgumentException.class, () -> config.setMaxActiveSessions(-1));
    }
    
    @Test
    @DisplayName("Should validate positive values for max memory usage")
    void setMaxMemoryUsageMB_PositiveValue_ShouldSucceed() {
        config.setMaxMemoryUsageMB(200);
        assertEquals(200, config.getMaxMemoryUsageMB());
    }
    
    @Test
    @DisplayName("Should throw exception for non-positive max memory usage")
    void setMaxMemoryUsageMB_NonPositiveValue_ShouldThrowException() {
        assertThrows(IllegalArgumentException.class, () -> config.setMaxMemoryUsageMB(0));
        assertThrows(IllegalArgumentException.class, () -> config.setMaxMemoryUsageMB(-1));
    }
    
    @Test
    @DisplayName("Should set and get automatic cleanup flag")
    void setEnableAutomaticCleanup_ShouldWork() {
        config.setEnableAutomaticCleanup(false);
        assertFalse(config.isEnableAutomaticCleanup());
        
        config.setEnableAutomaticCleanup(true);
        assertTrue(config.isEnableAutomaticCleanup());
    }
    
    @Test
    @DisplayName("Should validate configuration successfully with valid values")
    void validate_ValidConfiguration_ShouldSucceed() {
        // Default configuration should be valid
        assertDoesNotThrow(() -> config.validate());
        
        // Custom valid configuration
        config.setMaxMessagesPerSession(100);
        config.setMaxContextMessages(20);
        config.setCleanupIntervalMinutes(15);
        config.setSessionExpirationMinutes(60);
        
        assertDoesNotThrow(() -> config.validate());
    }
    
    @Test
    @DisplayName("Should throw exception when max context messages exceeds max messages per session")
    void validate_MaxContextExceedsMaxMessages_ShouldThrowException() {
        config.setMaxMessagesPerSession(10);
        config.setMaxContextMessages(20);
        
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> config.validate()
        );
        assertEquals("Max context messages cannot exceed max messages per session", exception.getMessage());
    }
    
    @Test
    @DisplayName("Should throw exception when cleanup interval exceeds session expiration")
    void validate_CleanupIntervalExceedsExpiration_ShouldThrowException() {
        config.setCleanupIntervalMinutes(120);
        config.setSessionExpirationMinutes(60);
        
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> config.validate()
        );
        assertEquals("Cleanup interval should be less than session expiration time", exception.getMessage());
    }
    
    @Test
    @DisplayName("Should throw exception when cleanup interval equals session expiration")
    void validate_CleanupIntervalEqualsExpiration_ShouldThrowException() {
        config.setCleanupIntervalMinutes(60);
        config.setSessionExpirationMinutes(60);
        
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> config.validate()
        );
        assertEquals("Cleanup interval should be less than session expiration time", exception.getMessage());
    }
}