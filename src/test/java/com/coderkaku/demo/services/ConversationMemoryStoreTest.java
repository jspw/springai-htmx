package com.coderkaku.demo.services;

import com.coderkaku.demo.models.ConversationMemory;
import com.coderkaku.demo.configs.ConversationMemoryConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.servlet.http.HttpSession;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationMemoryStoreTest {
    
    @Mock
    private HttpSession mockSession;
    
    private ConversationMemoryStore store;
    private ConversationMemory testMemory;
    
    @BeforeEach
    void setUp() {
        ConversationMemoryConfig config = new ConversationMemoryConfig();
        store = new ConversationMemoryStore(config);
        testMemory = new ConversationMemory("test-session-123");
        testMemory.addUserMessage("Hello, I need help with Python");
        testMemory.addAssistantMessage("I'd be happy to help you with Python!");
        
        lenient().when(mockSession.getId()).thenReturn("test-session-123");
    }
    
    @Test
    void testStoreConversation_Success() {
        // Act
        store.storeConversation(mockSession, testMemory);
        
        // Assert
        verify(mockSession).setAttribute("conversationMemory", testMemory);
        assertEquals(1, store.getActiveSessionCount());
    }
    
    @Test
    void testStoreConversation_NullSession_ThrowsException() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> store.storeConversation(null, testMemory)
        );
        assertEquals("Session cannot be null", exception.getMessage());
    }
    
    @Test
    void testStoreConversation_NullMemory_ThrowsException() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> store.storeConversation(mockSession, null)
        );
        assertEquals("ConversationMemory cannot be null", exception.getMessage());
    }
    
    @Test
    void testStoreConversation_SessionException_ThrowsRuntimeException() {
        // Arrange
        doThrow(new RuntimeException("Session error")).when(mockSession).setAttribute(anyString(), any());
        
        // Act & Assert
        RuntimeException exception = assertThrows(
            RuntimeException.class,
            () -> store.storeConversation(mockSession, testMemory)
        );
        assertEquals("Failed to store conversation memory", exception.getMessage());
    }
    
    @Test
    void testRetrieveConversation_Success() {
        // Arrange
        when(mockSession.getAttribute("conversationMemory")).thenReturn(testMemory);
        
        // Act
        ConversationMemory result = store.retrieveConversation(mockSession);
        
        // Assert
        assertNotNull(result);
        assertEquals(testMemory.getSessionId(), result.getSessionId());
        assertEquals(2, result.getMessageCount());
        verify(mockSession).getAttribute("conversationMemory");
    }
    
    @Test
    void testRetrieveConversation_NotFound_ReturnsNull() {
        // Arrange
        when(mockSession.getAttribute("conversationMemory")).thenReturn(null);
        
        // Act
        ConversationMemory result = store.retrieveConversation(mockSession);
        
        // Assert
        assertNull(result);
        verify(mockSession).getAttribute("conversationMemory");
    }
    
    @Test
    void testRetrieveConversation_NullSession_ThrowsException() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> store.retrieveConversation(null)
        );
        assertEquals("Session cannot be null", exception.getMessage());
    }
    
    @Test
    void testRetrieveConversation_SessionException_ReturnsNull() {
        // Arrange
        when(mockSession.getAttribute("conversationMemory")).thenThrow(new RuntimeException("Session error"));
        
        // Act
        ConversationMemory result = store.retrieveConversation(mockSession);
        
        // Assert
        assertNull(result);
    }
    
    @Test
    void testRemoveConversation_Success() {
        // Arrange
        when(mockSession.getAttribute("conversationMemory")).thenReturn(testMemory);
        
        // Act
        ConversationMemory result = store.removeConversation(mockSession);
        
        // Assert
        assertNotNull(result);
        assertEquals(testMemory.getSessionId(), result.getSessionId());
        verify(mockSession).getAttribute("conversationMemory");
        verify(mockSession).removeAttribute("conversationMemory");
    }
    
    @Test
    void testRemoveConversation_NotFound_ReturnsNull() {
        // Arrange
        when(mockSession.getAttribute("conversationMemory")).thenReturn(null);
        
        // Act
        ConversationMemory result = store.removeConversation(mockSession);
        
        // Assert
        assertNull(result);
        verify(mockSession).getAttribute("conversationMemory");
        verify(mockSession, never()).removeAttribute("conversationMemory");
    }
    
    @Test
    void testRemoveConversation_NullSession_ThrowsException() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> store.removeConversation(null)
        );
        assertEquals("Session cannot be null", exception.getMessage());
    }
    
    @Test
    void testRemoveConversation_SessionException_ReturnsNull() {
        // Arrange
        when(mockSession.getAttribute("conversationMemory")).thenThrow(new RuntimeException("Session error"));
        
        // Act
        ConversationMemory result = store.removeConversation(mockSession);
        
        // Assert
        assertNull(result);
    }
    
    @Test
    void testHasConversation_Exists_ReturnsTrue() {
        // Arrange
        when(mockSession.getAttribute("conversationMemory")).thenReturn(testMemory);
        
        // Act
        boolean result = store.hasConversation(mockSession);
        
        // Assert
        assertTrue(result);
        verify(mockSession).getAttribute("conversationMemory");
    }
    
    @Test
    void testHasConversation_NotExists_ReturnsFalse() {
        // Arrange
        when(mockSession.getAttribute("conversationMemory")).thenReturn(null);
        
        // Act
        boolean result = store.hasConversation(mockSession);
        
        // Assert
        assertFalse(result);
        verify(mockSession).getAttribute("conversationMemory");
    }
    
    @Test
    void testHasConversation_NullSession_ThrowsException() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> store.hasConversation(null)
        );
        assertEquals("Session cannot be null", exception.getMessage());
    }
    
    @Test
    void testHasConversation_SessionException_ReturnsFalse() {
        // Arrange
        when(mockSession.getAttribute("conversationMemory")).thenThrow(new RuntimeException("Session error"));
        
        // Act
        boolean result = store.hasConversation(mockSession);
        
        // Assert
        assertFalse(result);
    }
    
    @Test
    void testGetOrCreateConversation_ExistingConversation_ReturnsExisting() {
        // Arrange
        when(mockSession.getAttribute("conversationMemory")).thenReturn(testMemory);
        
        // Act
        ConversationMemory result = store.getOrCreateConversation(mockSession);
        
        // Assert
        assertNotNull(result);
        assertEquals(testMemory.getSessionId(), result.getSessionId());
        assertEquals(2, result.getMessageCount());
        verify(mockSession).getAttribute("conversationMemory");
        verify(mockSession, never()).setAttribute(eq("conversationMemory"), any());
    }
    
    @Test
    void testGetOrCreateConversation_NoExistingConversation_CreatesNew() {
        // Arrange
        when(mockSession.getAttribute("conversationMemory")).thenReturn(null);
        
        // Act
        ConversationMemory result = store.getOrCreateConversation(mockSession);
        
        // Assert
        assertNotNull(result);
        assertEquals("test-session-123", result.getSessionId());
        assertEquals(0, result.getMessageCount());
        verify(mockSession).getAttribute("conversationMemory");
        verify(mockSession).setAttribute(eq("conversationMemory"), any(ConversationMemory.class));
    }
    
    @Test
    void testGetOrCreateConversation_NullSession_ThrowsException() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> store.getOrCreateConversation(null)
        );
        assertEquals("Session cannot be null", exception.getMessage());
    }
    
    @Test
    void testUpdateLastActivity_ExistingConversation_UpdatesActivity() {
        // Arrange
        when(mockSession.getAttribute("conversationMemory")).thenReturn(testMemory);
        LocalDateTime originalActivity = testMemory.getLastActivity();
        
        // Wait a small amount to ensure timestamp difference
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Act
        store.updateLastActivity(mockSession);
        
        // Assert
        verify(mockSession).getAttribute("conversationMemory");
        verify(mockSession).setAttribute(eq("conversationMemory"), any(ConversationMemory.class));
        assertTrue(testMemory.getLastActivity().isAfter(originalActivity));
    }
    
    @Test
    void testUpdateLastActivity_NoExistingConversation_DoesNothing() {
        // Arrange
        when(mockSession.getAttribute("conversationMemory")).thenReturn(null);
        
        // Act
        store.updateLastActivity(mockSession);
        
        // Assert
        verify(mockSession).getAttribute("conversationMemory");
        verify(mockSession, never()).setAttribute(anyString(), any());
    }
    
    @Test
    void testUpdateLastActivity_NullSession_ThrowsException() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> store.updateLastActivity(null)
        );
        assertEquals("Session cannot be null", exception.getMessage());
    }
    
    @Test
    void testGetActiveSessionCount_InitiallyZero() {
        // Act
        int count = store.getActiveSessionCount();
        
        // Assert
        assertEquals(0, count);
    }
    
    @Test
    void testGetActiveSessionCount_AfterStoringConversation_IncrementsCount() {
        // Act
        store.storeConversation(mockSession, testMemory);
        int count = store.getActiveSessionCount();
        
        // Assert
        assertEquals(1, count);
    }
    
    @Test
    void testSessionTracking_MultipleOperations() {
        // Arrange
        HttpSession session2 = mock(HttpSession.class);
        when(session2.getId()).thenReturn("test-session-456");
        ConversationMemory memory2 = new ConversationMemory("test-session-456");
        
        // Act - Store conversations for two sessions
        store.storeConversation(mockSession, testMemory);
        store.storeConversation(session2, memory2);
        
        // Assert
        assertEquals(2, store.getActiveSessionCount());
        
        // Act - Remove one conversation
        when(mockSession.getAttribute("conversationMemory")).thenReturn(testMemory);
        store.removeConversation(mockSession);
        
        // Assert
        assertEquals(1, store.getActiveSessionCount());
    }
    
    @Test
    void testShutdown_ExecutorShutdown() throws InterruptedException {
        // Act
        store.shutdown();
        
        // Wait a moment for shutdown to complete
        Thread.sleep(100);
        
        // Assert - No exception should be thrown
        // The test passes if shutdown completes without error
        assertTrue(true);
    }
    
    @Test
    void testShutdown_MultipleCallsSafe() {
        // Act - Multiple shutdown calls should be safe
        store.shutdown();
        store.shutdown();
        
        // Assert - No exception should be thrown
        assertTrue(true);
    }
    
    @Test
    void testStoreConversation_UpdatesSessionTracker() {
        // Act
        store.storeConversation(mockSession, testMemory);
        
        // Assert
        assertEquals(1, store.getActiveSessionCount());
        
        // Act - Store again for same session
        store.storeConversation(mockSession, testMemory);
        
        // Assert - Should still be 1 (same session)
        assertEquals(1, store.getActiveSessionCount());
    }
    
    @Test
    void testRetrieveConversation_UpdatesSessionTracker() {
        // Arrange
        when(mockSession.getAttribute("conversationMemory")).thenReturn(testMemory);
        
        // Act
        store.retrieveConversation(mockSession);
        
        // Assert
        assertEquals(1, store.getActiveSessionCount());
    }
    
    @Test
    void testRemoveConversation_RemovesFromSessionTracker() {
        // Arrange - First store a conversation
        store.storeConversation(mockSession, testMemory);
        assertEquals(1, store.getActiveSessionCount());
        
        // Arrange - Mock getAttribute for removal
        when(mockSession.getAttribute("conversationMemory")).thenReturn(testMemory);
        
        // Act
        store.removeConversation(mockSession);
        
        // Assert
        assertEquals(0, store.getActiveSessionCount());
    }
    
    @Test
    void testGetOrCreateConversation_WithInvalidSessionAttribute() {
        // Arrange - Return a non-ConversationMemory object
        when(mockSession.getAttribute("conversationMemory")).thenReturn("invalid-object");
        
        // Act
        ConversationMemory result = store.getOrCreateConversation(mockSession);
        
        // Assert - Should create new conversation since existing attribute is invalid
        assertNotNull(result);
        assertEquals("test-session-123", result.getSessionId());
        assertEquals(0, result.getMessageCount());
        verify(mockSession).setAttribute(eq("conversationMemory"), any(ConversationMemory.class));
    }
    
    @Test
    void testRemoveConversation_WithInvalidSessionAttribute() {
        // Arrange - Return a non-ConversationMemory object
        when(mockSession.getAttribute("conversationMemory")).thenReturn("invalid-object");
        
        // Act
        ConversationMemory result = store.removeConversation(mockSession);
        
        // Assert - Should return null since attribute is not a ConversationMemory
        assertNull(result);
        verify(mockSession).getAttribute("conversationMemory");
        verify(mockSession, never()).removeAttribute("conversationMemory");
    }
}