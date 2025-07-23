package com.coderkaku.demo.models;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a single message in a conversation between user and assistant.
 * Contains role, content, timestamp, and metadata for context tracking.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public class ConversationMessage {
    
    private String role;
    private String content;
    private LocalDateTime timestamp = LocalDateTime.now();
    private Map<String, Object> metadata = new HashMap<>();
    
    /**
     * Constructor with role and content
     * @param role The role of the message sender ("user" or "assistant")
     * @param content The message content
     */
    public ConversationMessage(String role, String content) {
        this.role = role;
        this.content = content;
        this.timestamp = LocalDateTime.now();
        this.metadata = new HashMap<>();
    }
    
    /**
     * Custom setter for metadata to ensure defensive copying
     * @param metadata Additional metadata for the message
     */
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
    }
    
    // Utility methods
    
    /**
     * Add metadata entry
     * @param key The metadata key
     * @param value The metadata value
     */
    public void addMetadata(String key, Object value) {
        if (this.metadata == null) {
            this.metadata = new HashMap<>();
        }
        this.metadata.put(key, value);
    }
    
    /**
     * Get metadata value by key
     * @param key The metadata key
     * @return The metadata value or null if not found
     */
    public Object getMetadata(String key) {
        return this.metadata != null ? this.metadata.get(key) : null;
    }
    
    /**
     * Check if this is a user message
     * @return true if role is "user"
     */
    public boolean isUserMessage() {
        return "user".equals(this.role);
    }
    
    /**
     * Check if this is an assistant message
     * @return true if role is "assistant"
     */
    public boolean isAssistantMessage() {
        return "assistant".equals(this.role);
    }
    

}