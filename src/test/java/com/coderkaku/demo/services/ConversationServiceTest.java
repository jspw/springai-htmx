package com.coderkaku.demo.services;

import com.coderkaku.demo.models.ConversationMemory;
import com.coderkaku.demo.models.ConversationMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ConversationService
 */
@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {
    
    @Mock
    private ConversationMemoryStore mockMemoryStore;
    
    @Mock
    private ContextBuilder mockContextBuilder;
    
    @Mock
    private HttpSession mockSession;
    
    private ConversationService conversationService;
    private ConversationMemory testMemory;
    
    @BeforeEach
    void setUp() {
        conversationService = new ConversationService(mockMemoryStore, mockContextBuilder);
        testMemory = new ConversationMemory("test-session");
        
        lenient().when(mockSession.getId()).thenReturn("test-session");
    }
    
    @Test
    @DisplayName("Should add user message successfully")
    void addUserMessage_Success() {
        // Arrange
        String message = "Hello, I need help with Java";
        when(mockMemoryStore.getOrCreateConversation(mockSession)).thenReturn(testMemory);
        
        // Act
        ConversationMessage result = conversationService.addUserMessage(mockSession, message);
        
        // Assert
        assertNotNull(result);
        assertEquals("user", result.getRole());
        assertEquals(message, result.getContent());
        verify(mockMemoryStore).getOrCreateConversation(mockSession);
        verify(mockContextBuilder).extractContextFromMessage(eq(message), any());
        verify(mockMemoryStore).storeConversation(mockSession, testMemory);
    }
    
    @Test
    @DisplayName("Should throw exception when adding user message with null session")
    void addUserMessage_NullSession_ThrowsException() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> conversationService.addUserMessage(null, "test message")
        );
        assertEquals("Session cannot be null", exception.getMessage());
    }
    
    @Test
    @DisplayName("Should throw exception when adding user message with null message")
    void addUserMessage_NullMessage_ThrowsException() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> conversationService.addUserMessage(mockSession, null)
        );
        assertEquals("Message cannot be null or empty", exception.getMessage());
    }
    
    @Test
    @DisplayName("Should throw exception when adding user message with empty message")
    void addUserMessage_EmptyMessage_ThrowsException() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> conversationService.addUserMessage(mockSession, "   ")
        );
        assertEquals("Message cannot be null or empty", exception.getMessage());
    }
    
    @Test
    @DisplayName("Should handle memory store exception when adding user message")
    void addUserMessage_MemoryStoreException_ThrowsRuntimeException() {
        // Arrange
        String message = "Hello, I need help with Java";
        when(mockMemoryStore.getOrCreateConversation(mockSession))
            .thenThrow(new RuntimeException("Memory store error"));
        
        // Act & Assert
        RuntimeException exception = assertThrows(
            RuntimeException.class,
            () -> conversationService.addUserMessage(mockSession, message)
        );
        assertEquals("Failed to add user message to conversation", exception.getMessage());
    }
    
    @Test
    @DisplayName("Should add assistant message successfully")
    void addAssistantMessage_Success() {
        // Arrange
        String message = "I'd be happy to help you with Java!";
        when(mockMemoryStore.getOrCreateConversation(mockSession)).thenReturn(testMemory);
        
        // Act
        ConversationMessage result = conversationService.addAssistantMessage(mockSession, message);
        
        // Assert
        assertNotNull(result);
        assertEquals("assistant", result.getRole());
        assertEquals(message, result.getContent());
        verify(mockMemoryStore).getOrCreateConversation(mockSession);
        verify(mockMemoryStore).storeConversation(mockSession, testMemory);
        // Should not extract context from assistant messages
        verify(mockContextBuilder, never()).extractContextFromMessage(anyString(), any());
    }
    
    @Test
    @DisplayName("Should throw exception when adding assistant message with null session")
    void addAssistantMessage_NullSession_ThrowsException() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> conversationService.addAssistantMessage(null, "test message")
        );
        assertEquals("Session cannot be null", exception.getMessage());
    }
    
    @Test
    @DisplayName("Should build contextual prompt with existing conversation")
    void buildContextualPrompt_WithConversation_ReturnsContextualPrompt() {
        // Arrange
        String currentMessage = "How can I improve that?";
        String expectedPrompt = "Previous conversation context:\nUser: \"Hello\"\nCurrent message: \"How can I improve that?\"";
        
        testMemory.addUserMessage("Hello");
        testMemory.addAssistantMessage("Hi there!");
        
        when(mockMemoryStore.retrieveConversation(mockSession)).thenReturn(testMemory);
        when(mockContextBuilder.buildPromptWithContext(testMemory, currentMessage))
            .thenReturn(expectedPrompt);
        
        // Act
        String result = conversationService.buildContextualPrompt(mockSession, currentMessage);
        
        // Assert
        assertEquals(expectedPrompt, result);
        verify(mockMemoryStore).retrieveConversation(mockSession);
        verify(mockMemoryStore).updateLastActivity(mockSession);
        verify(mockContextBuilder).buildPromptWithContext(testMemory, currentMessage);
    }
    
    @Test
    @DisplayName("Should return current message when no conversation exists")
    void buildContextualPrompt_NoConversation_ReturnsCurrentMessage() {
        // Arrange
        String currentMessage = "Hello, how are you?";
        when(mockMemoryStore.retrieveConversation(mockSession)).thenReturn(null);
        
        // Act
        String result = conversationService.buildContextualPrompt(mockSession, currentMessage);
        
        // Assert
        assertEquals(currentMessage, result);
        verify(mockMemoryStore).retrieveConversation(mockSession);
        verify(mockMemoryStore, never()).updateLastActivity(mockSession);
        verify(mockContextBuilder, never()).buildPromptWithContext(any(), anyString());
    }
    
    @Test
    @DisplayName("Should return current message when context building fails")
    void buildContextualPrompt_ContextBuilderException_ReturnsCurrentMessage() {
        // Arrange
        String currentMessage = "How can I improve that?";
        when(mockMemoryStore.retrieveConversation(mockSession)).thenReturn(testMemory);
        when(mockContextBuilder.buildPromptWithContext(testMemory, currentMessage))
            .thenThrow(new RuntimeException("Context builder error"));
        
        // Act
        String result = conversationService.buildContextualPrompt(mockSession, currentMessage);
        
        // Assert
        assertEquals(currentMessage, result);
    }
    
    @Test
    @DisplayName("Should get conversation successfully")
    void getConversation_Success() {
        // Arrange
        when(mockMemoryStore.retrieveConversation(mockSession)).thenReturn(testMemory);
        
        // Act
        ConversationMemory result = conversationService.getConversation(mockSession);
        
        // Assert
        assertEquals(testMemory, result);
        verify(mockMemoryStore).retrieveConversation(mockSession);
    }
    
    @Test
    @DisplayName("Should return null when conversation doesn't exist")
    void getConversation_NotExists_ReturnsNull() {
        // Arrange
        when(mockMemoryStore.retrieveConversation(mockSession)).thenReturn(null);
        
        // Act
        ConversationMemory result = conversationService.getConversation(mockSession);
        
        // Assert
        assertNull(result);
        verify(mockMemoryStore).retrieveConversation(mockSession);
    }
    
    @Test
    @DisplayName("Should return null when memory store throws exception")
    void getConversation_MemoryStoreException_ReturnsNull() {
        // Arrange
        when(mockMemoryStore.retrieveConversation(mockSession))
            .thenThrow(new RuntimeException("Memory store error"));
        
        // Act
        ConversationMemory result = conversationService.getConversation(mockSession);
        
        // Assert
        assertNull(result);
    }
    
    @Test
    @DisplayName("Should get or create conversation successfully")
    void getOrCreateConversation_Success() {
        // Arrange
        when(mockMemoryStore.getOrCreateConversation(mockSession)).thenReturn(testMemory);
        
        // Act
        ConversationMemory result = conversationService.getOrCreateConversation(mockSession);
        
        // Assert
        assertEquals(testMemory, result);
        verify(mockMemoryStore).getOrCreateConversation(mockSession);
    }
    
    @Test
    @DisplayName("Should throw exception when get or create conversation fails")
    void getOrCreateConversation_MemoryStoreException_ThrowsRuntimeException() {
        // Arrange
        when(mockMemoryStore.getOrCreateConversation(mockSession))
            .thenThrow(new RuntimeException("Memory store error"));
        
        // Act & Assert
        RuntimeException exception = assertThrows(
            RuntimeException.class,
            () -> conversationService.getOrCreateConversation(mockSession)
        );
        assertEquals("Failed to get or create conversation", exception.getMessage());
    }
    
    @Test
    @DisplayName("Should check if conversation exists")
    void hasConversation_Exists_ReturnsTrue() {
        // Arrange
        when(mockMemoryStore.hasConversation(mockSession)).thenReturn(true);
        
        // Act
        boolean result = conversationService.hasConversation(mockSession);
        
        // Assert
        assertTrue(result);
        verify(mockMemoryStore).hasConversation(mockSession);
    }
    
    @Test
    @DisplayName("Should return false when conversation doesn't exist")
    void hasConversation_NotExists_ReturnsFalse() {
        // Arrange
        when(mockMemoryStore.hasConversation(mockSession)).thenReturn(false);
        
        // Act
        boolean result = conversationService.hasConversation(mockSession);
        
        // Assert
        assertFalse(result);
        verify(mockMemoryStore).hasConversation(mockSession);
    }
    
    @Test
    @DisplayName("Should return false when memory store throws exception")
    void hasConversation_MemoryStoreException_ReturnsFalse() {
        // Arrange
        when(mockMemoryStore.hasConversation(mockSession))
            .thenThrow(new RuntimeException("Memory store error"));
        
        // Act
        boolean result = conversationService.hasConversation(mockSession);
        
        // Assert
        assertFalse(result);
    }
    
    @Test
    @DisplayName("Should clear conversation successfully")
    void clearConversation_Success() {
        // Arrange
        when(mockMemoryStore.removeConversation(mockSession)).thenReturn(testMemory);
        
        // Act
        ConversationMemory result = conversationService.clearConversation(mockSession);
        
        // Assert
        assertEquals(testMemory, result);
        verify(mockMemoryStore).removeConversation(mockSession);
    }
    
    @Test
    @DisplayName("Should return null when clearing non-existent conversation")
    void clearConversation_NotExists_ReturnsNull() {
        // Arrange
        when(mockMemoryStore.removeConversation(mockSession)).thenReturn(null);
        
        // Act
        ConversationMemory result = conversationService.clearConversation(mockSession);
        
        // Assert
        assertNull(result);
        verify(mockMemoryStore).removeConversation(mockSession);
    }
    
    @Test
    @DisplayName("Should get conversation history successfully")
    void getConversationHistory_Success() {
        // Arrange
        testMemory.addUserMessage("Hello");
        testMemory.addAssistantMessage("Hi there!");
        when(mockMemoryStore.retrieveConversation(mockSession)).thenReturn(testMemory);
        
        // Act
        List<ConversationMessage> result = conversationService.getConversationHistory(mockSession);
        
        // Assert
        assertEquals(2, result.size());
        assertEquals("Hello", result.get(0).getContent());
        assertEquals("Hi there!", result.get(1).getContent());
        verify(mockMemoryStore).retrieveConversation(mockSession);
    }
    
    @Test
    @DisplayName("Should return empty list when no conversation exists")
    void getConversationHistory_NoConversation_ReturnsEmptyList() {
        // Arrange
        when(mockMemoryStore.retrieveConversation(mockSession)).thenReturn(null);
        
        // Act
        List<ConversationMessage> result = conversationService.getConversationHistory(mockSession);
        
        // Assert
        assertTrue(result.isEmpty());
        verify(mockMemoryStore).retrieveConversation(mockSession);
    }
    
    @Test
    @DisplayName("Should get message count successfully")
    void getMessageCount_Success() {
        // Arrange
        testMemory.addUserMessage("Hello");
        testMemory.addAssistantMessage("Hi there!");
        when(mockMemoryStore.retrieveConversation(mockSession)).thenReturn(testMemory);
        
        // Act
        int result = conversationService.getMessageCount(mockSession);
        
        // Assert
        assertEquals(2, result);
        verify(mockMemoryStore).retrieveConversation(mockSession);
    }
    
    @Test
    @DisplayName("Should return zero when no conversation exists")
    void getMessageCount_NoConversation_ReturnsZero() {
        // Arrange
        when(mockMemoryStore.retrieveConversation(mockSession)).thenReturn(null);
        
        // Act
        int result = conversationService.getMessageCount(mockSession);
        
        // Assert
        assertEquals(0, result);
        verify(mockMemoryStore).retrieveConversation(mockSession);
    }
    
    @Test
    @DisplayName("Should get last activity successfully")
    void getLastActivity_Success() {
        // Arrange
        LocalDateTime expectedTime = LocalDateTime.now();
        testMemory.setLastActivity(expectedTime);
        when(mockMemoryStore.retrieveConversation(mockSession)).thenReturn(testMemory);
        
        // Act
        LocalDateTime result = conversationService.getLastActivity(mockSession);
        
        // Assert
        assertEquals(expectedTime, result);
        verify(mockMemoryStore).retrieveConversation(mockSession);
    }
    
    @Test
    @DisplayName("Should return null when no conversation exists for last activity")
    void getLastActivity_NoConversation_ReturnsNull() {
        // Arrange
        when(mockMemoryStore.retrieveConversation(mockSession)).thenReturn(null);
        
        // Act
        LocalDateTime result = conversationService.getLastActivity(mockSession);
        
        // Assert
        assertNull(result);
        verify(mockMemoryStore).retrieveConversation(mockSession);
    }
    
    @Test
    @DisplayName("Should update last activity successfully")
    void updateLastActivity_Success() {
        // Act
        conversationService.updateLastActivity(mockSession);
        
        // Assert
        verify(mockMemoryStore).updateLastActivity(mockSession);
    }
    
    @Test
    @DisplayName("Should handle exception when updating last activity")
    void updateLastActivity_MemoryStoreException_HandlesGracefully() {
        // Arrange
        doThrow(new RuntimeException("Memory store error"))
            .when(mockMemoryStore).updateLastActivity(mockSession);
        
        // Act & Assert - Should not throw exception
        assertDoesNotThrow(() -> conversationService.updateLastActivity(mockSession));
    }
    
    @Test
    @DisplayName("Should check if conversation is inactive")
    void isConversationInactive_Success() {
        // Arrange
        LocalDateTime pastTime = LocalDateTime.now().minusMinutes(30);
        testMemory.setLastActivity(pastTime);
        when(mockMemoryStore.retrieveConversation(mockSession)).thenReturn(testMemory);
        
        // Act
        boolean result = conversationService.isConversationInactive(mockSession, 15);
        
        // Assert
        assertTrue(result);
        verify(mockMemoryStore).retrieveConversation(mockSession);
    }
    
    @Test
    @DisplayName("Should return false when conversation is active")
    void isConversationInactive_Active_ReturnsFalse() {
        // Arrange
        LocalDateTime recentTime = LocalDateTime.now().minusMinutes(5);
        testMemory.setLastActivity(recentTime);
        when(mockMemoryStore.retrieveConversation(mockSession)).thenReturn(testMemory);
        
        // Act
        boolean result = conversationService.isConversationInactive(mockSession, 15);
        
        // Assert
        assertFalse(result);
        verify(mockMemoryStore).retrieveConversation(mockSession);
    }
    
    @Test
    @DisplayName("Should get active session count")
    void getActiveSessionCount_Success() {
        // Arrange
        when(mockMemoryStore.getActiveSessionCount()).thenReturn(5);
        
        // Act
        int result = conversationService.getActiveSessionCount();
        
        // Assert
        assertEquals(5, result);
        verify(mockMemoryStore).getActiveSessionCount();
    }
    
    @Test
    @DisplayName("Should return zero when memory store throws exception for session count")
    void getActiveSessionCount_MemoryStoreException_ReturnsZero() {
        // Arrange
        when(mockMemoryStore.getActiveSessionCount())
            .thenThrow(new RuntimeException("Memory store error"));
        
        // Act
        int result = conversationService.getActiveSessionCount();
        
        // Assert
        assertEquals(0, result);
    }
    
    @Test
    @DisplayName("Should add message with metadata successfully")
    void addMessageWithMetadata_Success() {
        // Arrange
        String role = "user";
        String content = "Hello, I need help";
        Map<String, Object> metadata = Map.of("source", "web", "priority", "high");
        when(mockMemoryStore.getOrCreateConversation(mockSession)).thenReturn(testMemory);
        
        // Act
        ConversationMessage result = conversationService.addMessageWithMetadata(
            mockSession, role, content, metadata);
        
        // Assert
        assertNotNull(result);
        assertEquals(role, result.getRole());
        assertEquals(content, result.getContent());
        assertEquals("web", result.getMetadata("source"));
        assertEquals("high", result.getMetadata("priority"));
        verify(mockMemoryStore).getOrCreateConversation(mockSession);
        verify(mockContextBuilder).extractContextFromMessage(eq(content), any());
        verify(mockMemoryStore).storeConversation(mockSession, testMemory);
    }
    
    @Test
    @DisplayName("Should throw exception when adding message with null role")
    void addMessageWithMetadata_NullRole_ThrowsException() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> conversationService.addMessageWithMetadata(mockSession, null, "content", null)
        );
        assertEquals("Message role cannot be null or empty", exception.getMessage());
    }
    
    @Test
    @DisplayName("Should get context metadata successfully")
    void getContextMetadata_Success() {
        // Arrange
        Map<String, Object> expectedMetadata = Map.of("topics", List.of("java", "programming"));
        testMemory.setContextMetadata(expectedMetadata);
        when(mockMemoryStore.retrieveConversation(mockSession)).thenReturn(testMemory);
        
        // Act
        Map<String, Object> result = conversationService.getContextMetadata(mockSession);
        
        // Assert
        assertEquals(expectedMetadata, result);
        verify(mockMemoryStore).retrieveConversation(mockSession);
    }
    
    @Test
    @DisplayName("Should return empty map when no conversation exists for context metadata")
    void getContextMetadata_NoConversation_ReturnsEmptyMap() {
        // Arrange
        when(mockMemoryStore.retrieveConversation(mockSession)).thenReturn(null);
        
        // Act
        Map<String, Object> result = conversationService.getContextMetadata(mockSession);
        
        // Assert
        assertTrue(result.isEmpty());
        verify(mockMemoryStore).retrieveConversation(mockSession);
    }
    
    @Test
    @DisplayName("Should check if message contains pronouns")
    void containsPronouns_Success() {
        // Arrange
        String message = "How can I improve that?";
        when(mockContextBuilder.containsPronouns(message)).thenReturn(true);
        
        // Act
        boolean result = conversationService.containsPronouns(message);
        
        // Assert
        assertTrue(result);
        verify(mockContextBuilder).containsPronouns(message);
    }
    
    @Test
    @DisplayName("Should validate session parameter in all methods")
    void validateSession_NullSession_ThrowsException() {
        // Test various methods with null session
        assertThrows(IllegalArgumentException.class, 
            () -> conversationService.getConversation(null));
        assertThrows(IllegalArgumentException.class, 
            () -> conversationService.hasConversation(null));
        assertThrows(IllegalArgumentException.class, 
            () -> conversationService.clearConversation(null));
        assertThrows(IllegalArgumentException.class, 
            () -> conversationService.getConversationHistory(null));
        assertThrows(IllegalArgumentException.class, 
            () -> conversationService.getMessageCount(null));
        assertThrows(IllegalArgumentException.class, 
            () -> conversationService.getLastActivity(null));
        assertThrows(IllegalArgumentException.class, 
            () -> conversationService.updateLastActivity(null));
        assertThrows(IllegalArgumentException.class, 
            () -> conversationService.isConversationInactive(null, 10));
        assertThrows(IllegalArgumentException.class, 
            () -> conversationService.getContextMetadata(null));
        assertThrows(IllegalArgumentException.class, 
            () -> conversationService.buildContextualPrompt(null, "test"));
    }
}