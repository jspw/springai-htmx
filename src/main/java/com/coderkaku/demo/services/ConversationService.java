package com.coderkaku.demo.services;

import com.coderkaku.demo.models.ConversationMemory;
import com.coderkaku.demo.models.ConversationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Main orchestration service for conversation memory management.
 * Coordinates between ConversationMemoryStore and ContextBuilder to provide
 * comprehensive conversation memory functionality.
 */
@Service
public class ConversationService {
    
    private static final Logger logger = LoggerFactory.getLogger(ConversationService.class);
    
    private final ConversationMemoryStore memoryStore;
    private final ContextBuilder contextBuilder;
    
    public ConversationService(ConversationMemoryStore memoryStore, ContextBuilder contextBuilder) {
        this.memoryStore = memoryStore;
        this.contextBuilder = contextBuilder;
    }
    
    /**
     * Add a user message to the conversation history
     * @param session The HTTP session
     * @param message The user message content
     * @return The created ConversationMessage
     * @throws IllegalArgumentException if session or message is null/empty
     */
    public ConversationMessage addUserMessage(HttpSession session, String message) {
        validateSession(session);
        validateMessage(message);
        
        try {
            ConversationMemory memory = memoryStore.getOrCreateConversation(session);
            ConversationMessage userMessage = memory.addUserMessage(message);
            
            // Extract context metadata from the user message
            contextBuilder.extractContextFromMessage(message, memory.getContextMetadata());
            
            // Store the updated conversation
            memoryStore.storeConversation(session, memory);
            
            return userMessage;
        } catch (Exception e) {
            logger.error("Failed to add user message to conversation for session: {}", session.getId(), e);
            throw new RuntimeException("Failed to add user message to conversation", e);
        }
    }
    
    /**
     * Add an assistant message to the conversation history
     * @param session The HTTP session
     * @param message The assistant message content
     * @return The created ConversationMessage
     * @throws IllegalArgumentException if session or message is null/empty
     */
    public ConversationMessage addAssistantMessage(HttpSession session, String message) {
        validateSession(session);
        validateMessage(message);
        
        try {
            ConversationMemory memory = memoryStore.getOrCreateConversation(session);
            ConversationMessage assistantMessage = memory.addAssistantMessage(message);
            
            // Store the updated conversation
            memoryStore.storeConversation(session, memory);
            
            return assistantMessage;
        } catch (Exception e) {
            logger.error("Failed to add assistant message to conversation for session: {}", session.getId(), e);
            throw new RuntimeException("Failed to add assistant message to conversation", e);
        }
    }
    
    /**
     * Build a contextual prompt that includes conversation history and context
     * @param session The HTTP session
     * @param currentMessage The current user message
     * @return A contextual prompt string
     * @throws IllegalArgumentException if session or message is null/empty
     */
    public String buildContextualPrompt(HttpSession session, String currentMessage) {
        validateSession(session);
        validateMessage(currentMessage);
        
        try {
            ConversationMemory memory = memoryStore.retrieveConversation(session);
            
            if (memory == null) {
                // No conversation history, return the current message as-is
                return currentMessage;
            }
            
            // Update last activity
            memoryStore.updateLastActivity(session);
            
            // Build contextual prompt using ContextBuilder
            String result = contextBuilder.buildPromptWithContext(memory, currentMessage);
            
            return result;
        } catch (Exception e) {
            logger.warn("Failed to build contextual prompt for session: {}, falling back to original message", 
                       session.getId(), e);
            
            // Graceful fallback - return original message if context building fails
            return currentMessage;
        }
    }
    
    /**
     * Get the current conversation memory for a session
     * @param session The HTTP session
     * @return The ConversationMemory or null if none exists
     * @throws IllegalArgumentException if session is null
     */
    public ConversationMemory getConversation(HttpSession session) {
        validateSession(session);
        
        try {
            return memoryStore.retrieveConversation(session);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Get or create a conversation memory for a session
     * @param session The HTTP session
     * @return The ConversationMemory (existing or newly created)
     * @throws IllegalArgumentException if session is null
     */
    public ConversationMemory getOrCreateConversation(HttpSession session) {
        validateSession(session);
        
        try {
            return memoryStore.getOrCreateConversation(session);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get or create conversation", e);
        }
    }
    
    /**
     * Check if a conversation exists for the given session
     * @param session The HTTP session
     * @return true if conversation exists, false otherwise
     * @throws IllegalArgumentException if session is null
     */
    public boolean hasConversation(HttpSession session) {
        validateSession(session);
        
        try {
            return memoryStore.hasConversation(session);
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Clear the conversation history for a session
     * @param session The HTTP session
     * @return The removed ConversationMemory or null if none existed
     * @throws IllegalArgumentException if session is null
     */
    public ConversationMemory clearConversation(HttpSession session) {
        validateSession(session);
        
        try {
            return memoryStore.removeConversation(session);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Get the conversation history as a list of messages
     * @param session The HTTP session
     * @return List of ConversationMessage objects, empty list if no conversation exists
     * @throws IllegalArgumentException if session is null
     */
    public List<ConversationMessage> getConversationHistory(HttpSession session) {
        validateSession(session);
        
        try {
            ConversationMemory memory = memoryStore.retrieveConversation(session);
            return memory != null ? memory.getMessages() : List.of();
        } catch (Exception e) {
            return List.of();
        }
    }
    
    /**
     * Get the number of messages in the conversation
     * @param session The HTTP session
     * @return The message count, 0 if no conversation exists
     * @throws IllegalArgumentException if session is null
     */
    public int getMessageCount(HttpSession session) {
        validateSession(session);
        
        try {
            ConversationMemory memory = memoryStore.retrieveConversation(session);
            return memory != null ? memory.getMessageCount() : 0;
        } catch (Exception e) {
            return 0;
        }
    }
    
    /**
     * Get the last activity timestamp for a conversation
     * @param session The HTTP session
     * @return The last activity timestamp or null if no conversation exists
     * @throws IllegalArgumentException if session is null
     */
    public LocalDateTime getLastActivity(HttpSession session) {
        validateSession(session);
        
        try {
            ConversationMemory memory = memoryStore.retrieveConversation(session);
            return memory != null ? memory.getLastActivity() : null;
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Update the last activity timestamp for a conversation
     * @param session The HTTP session
     * @throws IllegalArgumentException if session is null
     */
    public void updateLastActivity(HttpSession session) {
        validateSession(session);
        
        try {
            memoryStore.updateLastActivity(session);
        } catch (Exception e) {
            // Silently ignore update failures
        }
    }
    
    /**
     * Check if the conversation has been inactive for a specified duration
     * @param session The HTTP session
     * @param minutes Minutes of inactivity to check
     * @return true if inactive for the specified duration, false otherwise
     * @throws IllegalArgumentException if session is null
     */
    public boolean isConversationInactive(HttpSession session, long minutes) {
        validateSession(session);
        
        try {
            ConversationMemory memory = memoryStore.retrieveConversation(session);
            return memory != null && memory.isInactiveFor(minutes);
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Get the total number of active conversation sessions
     * @return The count of active sessions
     */
    public int getActiveSessionCount() {
        try {
            return memoryStore.getActiveSessionCount();
        } catch (Exception e) {
            return 0;
        }
    }
    
    /**
     * Add a message with metadata to the conversation
     * @param session The HTTP session
     * @param role The message role ("user" or "assistant")
     * @param content The message content
     * @param metadata Additional metadata for the message
     * @return The created ConversationMessage
     * @throws IllegalArgumentException if session, role, or content is null/empty
     */
    public ConversationMessage addMessageWithMetadata(HttpSession session, String role, 
                                                     String content, java.util.Map<String, Object> metadata) {
        validateSession(session);
        validateMessage(content);
        
        if (role == null || role.trim().isEmpty()) {
            throw new IllegalArgumentException("Message role cannot be null or empty");
        }
        
        try {
            ConversationMemory memory = memoryStore.getOrCreateConversation(session);
            ConversationMessage message = new ConversationMessage(role, content);
            
            if (metadata != null && !metadata.isEmpty()) {
                message.setMetadata(metadata);
            }
            
            memory.addMessage(message);
            
            // Extract context metadata if it's a user message
            if ("user".equals(role)) {
                contextBuilder.extractContextFromMessage(content, memory.getContextMetadata());
            }
            
            // Store the updated conversation
            memoryStore.storeConversation(session, memory);
            
            return message;
        } catch (Exception e) {
            throw new RuntimeException("Failed to add message with metadata to conversation", e);
        }
    }
    
    /**
     * Get context metadata for a conversation
     * @param session The HTTP session
     * @return The context metadata map, empty map if no conversation exists
     * @throws IllegalArgumentException if session is null
     */
    public java.util.Map<String, Object> getContextMetadata(HttpSession session) {
        validateSession(session);
        
        try {
            ConversationMemory memory = memoryStore.retrieveConversation(session);
            return memory != null ? memory.getContextMetadata() : java.util.Map.of();
        } catch (Exception e) {
            return java.util.Map.of();
        }
    }
    
    /**
     * Check if a message contains pronouns that might need context resolution
     * @param message The message to check
     * @return true if pronouns are detected
     */
    public boolean containsPronouns(String message) {
        return contextBuilder.containsPronouns(message);
    }
    
    // Private validation methods
    
    private void validateSession(HttpSession session) {
        if (session == null) {
            throw new IllegalArgumentException("Session cannot be null");
        }
    }
    
    private void validateMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            throw new IllegalArgumentException("Message cannot be null or empty");
        }
    }
}