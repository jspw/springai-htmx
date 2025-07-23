package com.coderkaku.demo.models;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Represents the complete conversation memory for a chat session.
 * Contains session ID, message history, context metadata, and activity tracking.
 */
@Data
@NoArgsConstructor
public class ConversationMemory {
    
    private String sessionId;
    private List<ConversationMessage> messages = new ArrayList<>();
    private Map<String, Object> contextMetadata = new HashMap<>();
    private LocalDateTime lastActivity = LocalDateTime.now();
    
    /**
     * Constructor with session ID
     * @param sessionId The unique session identifier
     */
    public ConversationMemory(String sessionId) {
        this.sessionId = sessionId;
        this.messages = new ArrayList<>();
        this.contextMetadata = new HashMap<>();
        this.lastActivity = LocalDateTime.now();
    }
    
    /**
     * Full constructor
     * @param sessionId The unique session identifier
     * @param messages List of conversation messages
     * @param contextMetadata Context metadata map
     * @param lastActivity Last activity timestamp
     */
    public ConversationMemory(String sessionId, List<ConversationMessage> messages, 
                            Map<String, Object> contextMetadata, LocalDateTime lastActivity) {
        this.sessionId = sessionId;
        this.messages = messages != null ? new ArrayList<>(messages) : new ArrayList<>();
        this.contextMetadata = contextMetadata != null ? new HashMap<>(contextMetadata) : new HashMap<>();
        this.lastActivity = lastActivity != null ? lastActivity : LocalDateTime.now();
    }
    
    /**
     * Custom setter for messages to ensure defensive copying and activity update
     * @param messages List of conversation messages
     */
    public void setMessages(List<ConversationMessage> messages) {
        this.messages = messages != null ? new ArrayList<>(messages) : new ArrayList<>();
        updateLastActivity();
    }
    
    /**
     * Custom setter for contextMetadata to ensure defensive copying
     * @param contextMetadata Context metadata map
     */
    public void setContextMetadata(Map<String, Object> contextMetadata) {
        this.contextMetadata = contextMetadata != null ? new HashMap<>(contextMetadata) : new HashMap<>();
    }
    
    // Message management utility methods
    
    /**
     * Add a message to the conversation
     * @param message The message to add
     */
    public void addMessage(ConversationMessage message) {
        if (message != null) {
            this.messages.add(message);
            updateLastActivity();
        }
    }
    
    /**
     * Add a user message to the conversation
     * @param content The message content
     * @return The created ConversationMessage
     */
    public ConversationMessage addUserMessage(String content) {
        ConversationMessage message = new ConversationMessage("user", content);
        addMessage(message);
        return message;
    }
    
    /**
     * Add an assistant message to the conversation
     * @param content The message content
     * @return The created ConversationMessage
     */
    public ConversationMessage addAssistantMessage(String content) {
        ConversationMessage message = new ConversationMessage("assistant", content);
        addMessage(message);
        return message;
    }
    
    /**
     * Get the most recent message
     * @return The last message or null if no messages exist
     */
    public ConversationMessage getLastMessage() {
        return messages.isEmpty() ? null : messages.get(messages.size() - 1);
    }
    
    /**
     * Get messages by role
     * @param role The role to filter by ("user" or "assistant")
     * @return List of messages with the specified role
     */
    public List<ConversationMessage> getMessagesByRole(String role) {
        return messages.stream()
                .filter(msg -> role.equals(msg.getRole()))
                .collect(Collectors.toList());
    }
    
    /**
     * Get user messages only
     * @return List of user messages
     */
    public List<ConversationMessage> getUserMessages() {
        return getMessagesByRole("user");
    }
    
    /**
     * Get assistant messages only
     * @return List of assistant messages
     */
    public List<ConversationMessage> getAssistantMessages() {
        return getMessagesByRole("assistant");
    }
    
    /**
     * Get messages within a time range
     * @param from Start time (inclusive)
     * @param to End time (inclusive)
     * @return List of messages within the time range
     */
    public List<ConversationMessage> getMessagesInTimeRange(LocalDateTime from, LocalDateTime to) {
        return messages.stream()
                .filter(msg -> !msg.getTimestamp().isBefore(from) && !msg.getTimestamp().isAfter(to))
                .collect(Collectors.toList());
    }
    
    /**
     * Get the total number of messages
     * @return Message count
     */
    public int getMessageCount() {
        return messages.size();
    }
    
    /**
     * Check if conversation is empty
     * @return true if no messages exist
     */
    public boolean isEmpty() {
        return messages.isEmpty();
    }
    
    /**
     * Clear all messages
     */
    public void clearMessages() {
        this.messages.clear();
        updateLastActivity();
    }
    
    // Context metadata utility methods
    
    /**
     * Add context metadata entry
     * @param key The metadata key
     * @param value The metadata value
     */
    public void addContextMetadata(String key, Object value) {
        if (this.contextMetadata == null) {
            this.contextMetadata = new HashMap<>();
        }
        this.contextMetadata.put(key, value);
    }
    
    /**
     * Get context metadata value by key
     * @param key The metadata key
     * @return The metadata value or null if not found
     */
    public Object getContextMetadata(String key) {
        return this.contextMetadata != null ? this.contextMetadata.get(key) : null;
    }
    
    /**
     * Remove context metadata entry
     * @param key The metadata key to remove
     * @return The removed value or null if not found
     */
    public Object removeContextMetadata(String key) {
        return this.contextMetadata != null ? this.contextMetadata.remove(key) : null;
    }
    
    /**
     * Check if context metadata contains a key
     * @param key The metadata key
     * @return true if key exists
     */
    public boolean hasContextMetadata(String key) {
        return this.contextMetadata != null && this.contextMetadata.containsKey(key);
    }
    
    /**
     * Clear all context metadata
     */
    public void clearContextMetadata() {
        if (this.contextMetadata != null) {
            this.contextMetadata.clear();
        }
    }
    
    // Activity tracking
    
    /**
     * Update the last activity timestamp to now
     */
    public void updateLastActivity() {
        this.lastActivity = LocalDateTime.now();
    }
    
    /**
     * Check if the conversation has been inactive for a specified duration
     * @param minutes Minutes of inactivity to check
     * @return true if inactive for the specified duration
     */
    public boolean isInactiveFor(long minutes) {
        return lastActivity.isBefore(LocalDateTime.now().minusMinutes(minutes));
    }
    

}