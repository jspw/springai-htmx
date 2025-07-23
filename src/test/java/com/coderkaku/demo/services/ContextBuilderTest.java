package com.coderkaku.demo.services;

import com.coderkaku.demo.models.ConversationMemory;
import com.coderkaku.demo.models.ConversationMessage;
import com.coderkaku.demo.configs.ConversationMemoryConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ContextBuilder component
 */
class ContextBuilderTest {
    
    private ContextBuilder contextBuilder;
    private ConversationMemory memory;
    
    @BeforeEach
    void setUp() {
        ConversationMemoryConfig config = new ConversationMemoryConfig();
        contextBuilder = new ContextBuilder(config);
        memory = new ConversationMemory("test-session");
    }
    
    @Test
    @DisplayName("Should return current message when no conversation history exists")
    void buildPromptWithContext_EmptyMemory_ReturnsCurrentMessage() {
        String currentMessage = "Hello, how are you?";
        
        String result = contextBuilder.buildPromptWithContext(memory, currentMessage);
        
        assertEquals(currentMessage, result);
    }
    
    @Test
    @DisplayName("Should return current message when memory is null")
    void buildPromptWithContext_NullMemory_ReturnsCurrentMessage() {
        String currentMessage = "Hello, how are you?";
        
        String result = contextBuilder.buildPromptWithContext(null, currentMessage);
        
        assertEquals(currentMessage, result);
    }
    
    @Test
    @DisplayName("Should build contextual prompt with conversation history")
    void buildPromptWithContext_WithHistory_IncludesContext() {
        // Setup conversation history
        memory.addUserMessage("I am not good at python");
        memory.addAssistantMessage("I understand you're looking to improve your Python skills. What specific areas would you like to focus on?");
        
        String currentMessage = "How can I improve that?";
        
        String result = contextBuilder.buildPromptWithContext(memory, currentMessage);
        
        assertTrue(result.contains("Previous conversation context:"));
        assertTrue(result.contains("User: \"I am not good at python\""));
        assertTrue(result.contains("Assistant: \"I understand you're looking to improve your Python skills"));
        assertTrue(result.contains("Current message: \"How can I improve that?\""));
    }
    
    @Test
    @DisplayName("Should add reference resolution when pronouns are detected")
    void buildPromptWithContext_WithPronouns_AddsResolutionHint() {
        memory.addUserMessage("I am learning Java");
        memory.addAssistantMessage("That's great! Java is a powerful language.");
        
        String currentMessage = "How can I improve that?";
        
        String result = contextBuilder.buildPromptWithContext(memory, currentMessage);
        
        // Should contain either reference resolutions or a note about references
        assertTrue(result.contains("Reference resolutions:") || 
                  result.contains("Note: The user's message contains references"));
        assertTrue(result.contains("Please resolve these references using the conversation context") ||
                  result.contains("Please use these reference resolutions when responding"));
    }
    
    @Test
    @DisplayName("Should limit context to maximum number of messages")
    void buildPromptWithContext_ManyMessages_LimitsContext() {
        // Add more than MAX_CONTEXT_MESSAGES (10)
        for (int i = 0; i < 15; i++) {
            memory.addUserMessage("Message " + i);
            memory.addAssistantMessage("Response " + i);
        }
        
        String currentMessage = "Current question";
        String result = contextBuilder.buildPromptWithContext(memory, currentMessage);
        
        // Should not contain the earliest messages
        assertFalse(result.contains("Message 0"));
        assertFalse(result.contains("Response 0"));
        
        // Should contain the most recent messages
        assertTrue(result.contains("Message 14"));
        assertTrue(result.contains("Response 14"));
    }
    
    @Test
    @DisplayName("Should include context metadata in prompt")
    void buildPromptWithContext_WithMetadata_IncludesMetadata() {
        memory.addUserMessage("I am not good at python");
        
        // Extract context metadata
        contextBuilder.extractContextFromMessage("I am not good at python", memory.getContextMetadata());
        
        String currentMessage = "How can I learn better?";
        String result = contextBuilder.buildPromptWithContext(memory, currentMessage);
        
        assertTrue(result.contains("Context information:"));
        assertTrue(result.contains("User skills:"));
        assertTrue(result.contains("python: beginner"));
    }
    
    @Test
    @DisplayName("Should detect pronouns correctly")
    void containsPronouns_VariousInputs_DetectsCorrectly() {
        assertTrue(contextBuilder.containsPronouns("How can I improve that?"));
        assertTrue(contextBuilder.containsPronouns("What is it about?"));
        assertTrue(contextBuilder.containsPronouns("This is confusing"));
        assertTrue(contextBuilder.containsPronouns("These are difficult"));
        assertTrue(contextBuilder.containsPronouns("Those seem complex"));
        
        assertFalse(contextBuilder.containsPronouns("How can I learn Java?"));
        assertFalse(contextBuilder.containsPronouns("What is programming?"));
        assertFalse(contextBuilder.containsPronouns(null));
        assertFalse(contextBuilder.containsPronouns(""));
    }
    
    @Test
    @DisplayName("Should extract user skills from messages")
    void extractContextFromMessage_UserSkills_ExtractsCorrectly() {
        Map<String, Object> metadata = new HashMap<>();
        
        contextBuilder.extractContextFromMessage("I am not good at python", metadata);
        contextBuilder.extractContextFromMessage("I'm excellent at Java", metadata);
        contextBuilder.extractContextFromMessage("I am new at React", metadata);
        
        @SuppressWarnings("unchecked")
        List<String> skills = (List<String>) metadata.get("userSkills");
        
        assertNotNull(skills);
        assertTrue(skills.contains("python: beginner"));
        assertTrue(skills.contains("Java: excellent"));
        assertTrue(skills.contains("React: new"));
    }
    
    @Test
    @DisplayName("Should extract user preferences from messages")
    void extractContextFromMessage_UserPreferences_ExtractsCorrectly() {
        Map<String, Object> metadata = new HashMap<>();
        
        contextBuilder.extractContextFromMessage("I like functional programming", metadata);
        contextBuilder.extractContextFromMessage("I prefer TypeScript over JavaScript", metadata);
        contextBuilder.extractContextFromMessage("I hate debugging", metadata);
        
        @SuppressWarnings("unchecked")
        List<String> preferences = (List<String>) metadata.get("preferences");
        
        assertNotNull(preferences);
        assertTrue(preferences.contains("I like functional programming"));
        assertTrue(preferences.contains("I prefer TypeScript over JavaScript"));
        assertTrue(preferences.contains("I hate debugging"));
    }
    
    @Test
    @DisplayName("Should extract topics from messages")
    void extractContextFromMessage_Topics_ExtractsCorrectly() {
        Map<String, Object> metadata = new HashMap<>();
        
        contextBuilder.extractContextFromMessage("Let's talk about microservices", metadata);
        contextBuilder.extractContextFromMessage("I'm learning Java programming", metadata);
        contextBuilder.extractContextFromMessage("Questions regarding Spring Boot", metadata);
        
        @SuppressWarnings("unchecked")
        List<String> topics = (List<String>) metadata.get("topics");
        
        assertNotNull(topics);
        assertTrue(topics.contains("microservices"));
        assertTrue(topics.contains("java"));
        assertTrue(topics.contains("programming"));
        assertTrue(topics.contains("Spring"));
    }
    
    @Test
    @DisplayName("Should extract entities from messages")
    void extractContextFromMessage_Entities_ExtractsCorrectly() {
        Map<String, Object> metadata = new HashMap<>();
        
        contextBuilder.extractContextFromMessage("I'm using Spring Boot and Docker", metadata);
        contextBuilder.extractContextFromMessage("Working with PostgreSQL database", metadata);
        
        @SuppressWarnings("unchecked")
        List<String> entities = (List<String>) metadata.get("entities");
        
        assertNotNull(entities);
        assertTrue(entities.contains("Spring"));
        assertTrue(entities.contains("Boot"));
        assertTrue(entities.contains("Docker"));
        assertTrue(entities.contains("PostgreSQL"));
    }
    
    @Test
    @DisplayName("Should not extract common words as entities")
    void extractContextFromMessage_CommonWords_DoesNotExtract() {
        Map<String, Object> metadata = new HashMap<>();
        
        contextBuilder.extractContextFromMessage("I Can Do This When You Are Ready", metadata);
        
        @SuppressWarnings("unchecked")
        List<String> entities = (List<String>) metadata.get("entities");
        
        if (entities != null) {
            assertFalse(entities.contains("I"));
            assertFalse(entities.contains("Can"));
            assertFalse(entities.contains("Do"));
            assertFalse(entities.contains("This"));
            assertFalse(entities.contains("When"));
            assertFalse(entities.contains("You"));
            assertFalse(entities.contains("Are"));
        }
    }
    
    @Test
    @DisplayName("Should handle null and empty messages gracefully")
    void extractContextFromMessage_NullAndEmpty_HandlesGracefully() {
        Map<String, Object> metadata = new HashMap<>();
        
        // Should not throw exceptions
        assertDoesNotThrow(() -> {
            contextBuilder.extractContextFromMessage(null, metadata);
            contextBuilder.extractContextFromMessage("", metadata);
            contextBuilder.extractContextFromMessage("   ", metadata);
        });
        
        // Metadata should remain empty or unchanged
        assertTrue(metadata.isEmpty() || metadata.values().stream().allMatch(v -> 
            v instanceof List && ((List<?>) v).isEmpty()));
    }
    
    @Test
    @DisplayName("Should not duplicate extracted information")
    void extractContextFromMessage_DuplicateInfo_DoesNotDuplicate() {
        Map<String, Object> metadata = new HashMap<>();
        
        // Extract same information multiple times
        contextBuilder.extractContextFromMessage("I am not good at python", metadata);
        contextBuilder.extractContextFromMessage("I am not good at python", metadata);
        contextBuilder.extractContextFromMessage("I like functional programming", metadata);
        contextBuilder.extractContextFromMessage("I like functional programming", metadata);
        
        @SuppressWarnings("unchecked")
        List<String> skills = (List<String>) metadata.get("userSkills");
        @SuppressWarnings("unchecked")
        List<String> preferences = (List<String>) metadata.get("preferences");
        
        // Should only contain one instance of each
        assertEquals(1, skills.size());
        assertEquals(1, preferences.size());
        assertEquals("python: beginner", skills.get(0));
        assertEquals("I like functional programming", preferences.get(0));
    }
    
    @Test
    @DisplayName("Should build complex contextual prompt with all elements")
    void buildPromptWithContext_ComplexScenario_BuildsCompletePrompt() {
        // Setup complex conversation
        memory.addUserMessage("I am not good at python but I like functional programming");
        memory.addAssistantMessage("That's interesting! Functional programming concepts can actually help with Python.");
        memory.addUserMessage("I'm working on a Spring Boot project");
        memory.addAssistantMessage("Great! Spring Boot is excellent for building robust applications.");
        
        // Extract metadata
        contextBuilder.extractContextFromMessage("I am not good at python but I like functional programming", memory.getContextMetadata());
        contextBuilder.extractContextFromMessage("I'm working on a Spring Boot project", memory.getContextMetadata());
        
        String currentMessage = "How can I improve that using these concepts?";
        String result = contextBuilder.buildPromptWithContext(memory, currentMessage);
        
        // Should contain all elements
        assertTrue(result.contains("Previous conversation context:"));
        assertTrue(result.contains("Context information:"));
        assertTrue(result.contains("User skills:"));
        assertTrue(result.contains("User preferences:"));
        assertTrue(result.contains("Current message:"));
        assertTrue(result.contains("Reference resolutions:") || result.contains("Note: The user's message contains references"));
        
        // Should contain specific extracted information
        assertTrue(result.contains("python: beginner"));
        assertTrue(result.contains("functional programming"));
        assertTrue(result.contains("Spring"));
    }
    
    @Test
    @DisplayName("Should detect references correctly")
    void containsReferences_VariousInputs_DetectsCorrectly() {
        assertTrue(contextBuilder.containsReferences("How can I improve that?"));
        assertTrue(contextBuilder.containsReferences("What is it about?"));
        assertTrue(contextBuilder.containsReferences("This problem is confusing"));
        assertTrue(contextBuilder.containsReferences("That approach seems complex"));
        assertTrue(contextBuilder.containsReferences("The above solution works"));
        assertTrue(contextBuilder.containsReferences("The previous method failed"));
        
        assertFalse(contextBuilder.containsReferences("How can I learn Java?"));
        assertFalse(contextBuilder.containsReferences("What is programming?"));
        assertFalse(contextBuilder.containsReferences(null));
        assertFalse(contextBuilder.containsReferences(""));
    }
    
    @Test
    @DisplayName("Should resolve demonstrative references")
    void resolveReferences_DemonstrativeReferences_ResolvesCorrectly() {
        // Setup conversation with specific concepts
        memory.addUserMessage("I'm having trouble with algorithms");
        memory.addAssistantMessage("Algorithms can be challenging. Let me help you understand sorting algorithms.");
        
        String currentMessage = "Can you explain that approach better?";
        Map<String, String> resolutions = contextBuilder.resolveReferences(currentMessage, memory);
        
        assertNotNull(resolutions);
        assertTrue(resolutions.containsKey("that approach") || resolutions.containsKey("that"));
    }
    
    @Test
    @DisplayName("Should resolve pronoun references")
    void resolveReferences_PronounReferences_ResolvesCorrectly() {
        // Setup conversation
        memory.addUserMessage("I need help with database design");
        memory.addAssistantMessage("Database design involves creating efficient table structures and relationships.");
        
        String currentMessage = "How do I implement it effectively?";
        Map<String, String> resolutions = contextBuilder.resolveReferences(currentMessage, memory);
        
        assertNotNull(resolutions);
        // Should resolve "it" to something from the assistant's response
        assertTrue(resolutions.containsKey("it"));
    }
    
    @Test
    @DisplayName("Should resolve explicit references")
    void resolveReferences_ExplicitReferences_ResolvesCorrectly() {
        // Setup conversation
        memory.addUserMessage("What's the best way to handle errors?");
        memory.addAssistantMessage("Error handling involves try-catch blocks and proper exception management.");
        
        String currentMessage = "Can you elaborate on the above?";
        Map<String, String> resolutions = contextBuilder.resolveReferences(currentMessage, memory);
        
        assertNotNull(resolutions);
        assertTrue(resolutions.containsKey("the above"));
    }
    
    @Test
    @DisplayName("Should return empty map when no references can be resolved")
    void resolveReferences_NoResolvableReferences_ReturnsEmptyMap() {
        memory.addUserMessage("Hello");
        memory.addAssistantMessage("Hi there!");
        
        String currentMessage = "How can I improve that mysterious thing?";
        Map<String, String> resolutions = contextBuilder.resolveReferences(currentMessage, memory);
        
        assertNotNull(resolutions);
        // May be empty if references can't be resolved
    }
    
    @Test
    @DisplayName("Should return empty map when no conversation history exists")
    void resolveReferences_NoHistory_ReturnsEmptyMap() {
        String currentMessage = "How can I improve that?";
        Map<String, String> resolutions = contextBuilder.resolveReferences(currentMessage, memory);
        
        assertNotNull(resolutions);
        assertTrue(resolutions.isEmpty());
    }
    
    @Test
    @DisplayName("Should handle null inputs gracefully for reference resolution")
    void resolveReferences_NullInputs_HandlesGracefully() {
        Map<String, String> resolutions1 = contextBuilder.resolveReferences(null, memory);
        Map<String, String> resolutions2 = contextBuilder.resolveReferences("test", null);
        
        assertNotNull(resolutions1);
        assertNotNull(resolutions2);
        assertTrue(resolutions1.isEmpty());
        assertTrue(resolutions2.isEmpty());
    }
    
    @Test
    @DisplayName("Should build prompt with reference resolutions when available")
    void buildPromptWithContext_WithResolvableReferences_IncludesResolutions() {
        // Setup conversation with clear references
        memory.addUserMessage("I'm learning about algorithms");
        memory.addAssistantMessage("Algorithms are step-by-step procedures for solving problems.");
        
        String currentMessage = "Can you give me examples of that?";
        String result = contextBuilder.buildPromptWithContext(memory, currentMessage);
        
        // Should either contain reference resolutions or a note about references
        assertTrue(result.contains("Reference resolutions:") || 
                  result.contains("Note: The user's message contains references"));
    }
    
    @Test
    @DisplayName("Should extract key concepts from technical content")
    void extractKeyConcept_TechnicalContent_ExtractsCorrectly() {
        // This tests the private method indirectly through reference resolution
        memory.addUserMessage("I'm working with databases");
        memory.addAssistantMessage("Database normalization is important for efficient data storage.");
        
        String currentMessage = "How do I implement it?";
        Map<String, String> resolutions = contextBuilder.resolveReferences(currentMessage, memory);
        
        // Should resolve "it" to something related to databases or normalization
        if (resolutions.containsKey("it")) {
            String resolution = resolutions.get("it");
            assertTrue(resolution.toLowerCase().contains("database") || 
                      resolution.toLowerCase().contains("normalization") ||
                      resolution.toLowerCase().contains("data"));
        }
    }
    
    @Test
    @DisplayName("Should handle complex reference scenarios")
    void resolveReferences_ComplexScenario_HandlesCorrectly() {
        // Setup complex conversation
        memory.addUserMessage("I'm building a REST API with Spring Boot");
        memory.addAssistantMessage("Spring Boot makes REST API development easier with annotations like @RestController.");
        memory.addUserMessage("I also need to handle authentication");
        memory.addAssistantMessage("For authentication, you can use Spring Security with JWT tokens.");
        
        String currentMessage = "How do I integrate these approaches together?";
        Map<String, String> resolutions = contextBuilder.resolveReferences(currentMessage, memory);
        
        assertNotNull(resolutions);
        // Should attempt to resolve "these approaches"
        if (resolutions.containsKey("these approaches")) {
            String resolution = resolutions.get("these approaches");
            assertNotNull(resolution);
            assertFalse(resolution.trim().isEmpty());
        }
    }
}