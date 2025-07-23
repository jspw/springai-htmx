package com.coderkaku.demo.models;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConversationMemoryTest {

    @Test
    void testDefaultConstructor() {
        ConversationMemory memory = new ConversationMemory();
        
        assertNotNull(memory.getMessages());
        assertNotNull(memory.getContextMetadata());
        assertNotNull(memory.getLastActivity());
        assertTrue(memory.isEmpty());
        assertEquals(0, memory.getMessageCount());
    }

    @Test
    void testConstructorWithSessionId() {
        ConversationMemory memory = new ConversationMemory("session-123");
        
        assertEquals("session-123", memory.getSessionId());
        assertTrue(memory.isEmpty());
    }

    @Test
    void testAddMessage() {
        ConversationMemory memory = new ConversationMemory("session-123");
        ConversationMessage message = new ConversationMessage("user", "Hello");
        
        memory.addMessage(message);
        
        assertEquals(1, memory.getMessageCount());
        assertFalse(memory.isEmpty());
        assertEquals(message, memory.getLastMessage());
    }

    @Test
    void testAddUserAndAssistantMessages() {
        ConversationMemory memory = new ConversationMemory("session-123");
        
        ConversationMessage userMsg = memory.addUserMessage("Hello");
        ConversationMessage assistantMsg = memory.addAssistantMessage("Hi there");
        
        assertEquals(2, memory.getMessageCount());
        assertEquals("user", userMsg.getRole());
        assertEquals("Hello", userMsg.getContent());
        assertEquals("assistant", assistantMsg.getRole());
        assertEquals("Hi there", assistantMsg.getContent());
        assertEquals(assistantMsg, memory.getLastMessage());
    }

    @Test
    void testGetMessagesByRole() {
        ConversationMemory memory = new ConversationMemory("session-123");
        
        memory.addUserMessage("First user message");
        memory.addAssistantMessage("First assistant message");
        memory.addUserMessage("Second user message");
        
        List<ConversationMessage> userMessages = memory.getUserMessages();
        List<ConversationMessage> assistantMessages = memory.getAssistantMessages();
        
        assertEquals(2, userMessages.size());
        assertEquals(1, assistantMessages.size());
        assertEquals("First user message", userMessages.get(0).getContent());
        assertEquals("Second user message", userMessages.get(1).getContent());
        assertEquals("First assistant message", assistantMessages.get(0).getContent());
    }

    @Test
    void testContextMetadataOperations() {
        ConversationMemory memory = new ConversationMemory("session-123");
        
        memory.addContextMetadata("userSkill", "python: beginner");
        memory.addContextMetadata("topic", "programming");
        
        assertEquals("python: beginner", memory.getContextMetadata("userSkill"));
        assertEquals("programming", memory.getContextMetadata("topic"));
        assertTrue(memory.hasContextMetadata("userSkill"));
        assertFalse(memory.hasContextMetadata("nonexistent"));
        
        Object removed = memory.removeContextMetadata("userSkill");
        assertEquals("python: beginner", removed);
        assertFalse(memory.hasContextMetadata("userSkill"));
    }

    @Test
    void testClearOperations() {
        ConversationMemory memory = new ConversationMemory("session-123");
        
        memory.addUserMessage("Test message");
        memory.addContextMetadata("test", "value");
        
        assertFalse(memory.isEmpty());
        assertTrue(memory.hasContextMetadata("test"));
        
        memory.clearMessages();
        assertTrue(memory.isEmpty());
        
        memory.clearContextMetadata();
        assertFalse(memory.hasContextMetadata("test"));
    }

    @Test
    void testInactivityCheck() {
        ConversationMemory memory = new ConversationMemory("session-123");
        
        // Should not be inactive immediately
        assertFalse(memory.isInactiveFor(1));
        
        // Set last activity to 2 minutes ago
        memory.setLastActivity(LocalDateTime.now().minusMinutes(2));
        
        // Should be inactive for 1 minute
        assertTrue(memory.isInactiveFor(1));
        // Should not be inactive for 3 minutes
        assertFalse(memory.isInactiveFor(3));
    }

    @Test
    void testGetMessagesInTimeRange() {
        ConversationMemory memory = new ConversationMemory("session-123");
        LocalDateTime now = LocalDateTime.now();
        
        // Add messages with specific timestamps
        ConversationMessage msg1 = new ConversationMessage("user", "Message 1", now.minusMinutes(5), null);
        ConversationMessage msg2 = new ConversationMessage("user", "Message 2", now.minusMinutes(3), null);
        ConversationMessage msg3 = new ConversationMessage("user", "Message 3", now.minusMinutes(1), null);
        
        memory.addMessage(msg1);
        memory.addMessage(msg2);
        memory.addMessage(msg3);
        
        List<ConversationMessage> messagesInRange = memory.getMessagesInTimeRange(
            now.minusMinutes(4), now.minusMinutes(2)
        );
        
        assertEquals(1, messagesInRange.size());
        assertEquals("Message 2", messagesInRange.get(0).getContent());
    }

    @Test
    void testUpdateLastActivity() {
        ConversationMemory memory = new ConversationMemory("session-123");
        LocalDateTime initialActivity = memory.getLastActivity();
        
        // Wait a bit and update activity
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        memory.updateLastActivity();
        
        assertTrue(memory.getLastActivity().isAfter(initialActivity));
    }
}