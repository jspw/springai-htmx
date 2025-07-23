package com.coderkaku.demo.models;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConversationMessageTest {

    @Test
    void testDefaultConstructor() {
        ConversationMessage message = new ConversationMessage();
        
        assertNotNull(message.getMetadata());
        assertNotNull(message.getTimestamp());
        assertTrue(message.getMetadata().isEmpty());
    }

    @Test
    void testConstructorWithRoleAndContent() {
        ConversationMessage message = new ConversationMessage("user", "Hello world");
        
        assertEquals("user", message.getRole());
        assertEquals("Hello world", message.getContent());
        assertNotNull(message.getTimestamp());
        assertNotNull(message.getMetadata());
    }

    @Test
    void testFullConstructor() {
        LocalDateTime timestamp = LocalDateTime.now();
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("key", "value");
        
        ConversationMessage message = new ConversationMessage("assistant", "Response", timestamp, metadata);
        
        assertEquals("assistant", message.getRole());
        assertEquals("Response", message.getContent());
        assertEquals(timestamp, message.getTimestamp());
        assertEquals("value", message.getMetadata("key"));
    }

    @Test
    void testMetadataOperations() {
        ConversationMessage message = new ConversationMessage();
        
        message.addMetadata("test", "value");
        assertEquals("value", message.getMetadata("test"));
        
        message.addMetadata("number", 42);
        assertEquals(42, message.getMetadata("number"));
    }

    @Test
    void testRoleCheckers() {
        ConversationMessage userMessage = new ConversationMessage("user", "Hello");
        ConversationMessage assistantMessage = new ConversationMessage("assistant", "Hi");
        
        assertTrue(userMessage.isUserMessage());
        assertFalse(userMessage.isAssistantMessage());
        
        assertTrue(assistantMessage.isAssistantMessage());
        assertFalse(assistantMessage.isUserMessage());
    }

    @Test
    void testEqualsAndHashCode() {
        LocalDateTime timestamp = LocalDateTime.now();
        ConversationMessage message1 = new ConversationMessage("user", "Hello", timestamp, null);
        ConversationMessage message2 = new ConversationMessage("user", "Hello", timestamp, null);
        
        assertEquals(message1, message2);
        assertEquals(message1.hashCode(), message2.hashCode());
    }
}