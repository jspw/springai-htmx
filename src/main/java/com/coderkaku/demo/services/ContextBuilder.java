package com.coderkaku.demo.services;

import com.coderkaku.demo.models.ConversationMemory;
import com.coderkaku.demo.models.ConversationMessage;
import com.coderkaku.demo.configs.ConversationMemoryConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Component responsible for building contextual prompts from conversation history
 * and extracting context metadata from messages.
 */
@Component
public class ContextBuilder {
    
    // Patterns for identifying references and context clues
    private static final Pattern PRONOUN_PATTERN = Pattern.compile("\\b(that|it|this|these|those)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern DEMONSTRATIVE_PATTERN = Pattern.compile("\\b(this|that|these|those)\\s+(\\w+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern REFERENCE_PATTERN = Pattern.compile("\\b(the (above|previous|last|earlier|mentioned))\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SKILL_PATTERN = Pattern.compile("\\b(I am|I'm)\\s+(not\\s+)?(good|bad|terrible|excellent|great|new|experienced)\\s+at\\s+(\\w+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PREFERENCE_PATTERN = Pattern.compile("\\b(I (like|prefer|hate|dislike|love|want|need))\\s+(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TOPIC_PATTERN = Pattern.compile("\\b(about|regarding|concerning|related to)\\s+(\\w+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern QUESTION_REFERENCE_PATTERN = Pattern.compile("\\b(how (do|can) I|what (is|are)|why (is|are|do|does))\\s+(.+?)\\?", Pattern.CASE_INSENSITIVE);
    
    private final ConversationMemoryConfig config;
    
    @Autowired
    public ContextBuilder(ConversationMemoryConfig config) {
        this.config = config;
    }
    
    /**
     * Build a contextual prompt that includes relevant conversation history
     * @param memory The conversation memory containing message history
     * @param currentMessage The current user message
     * @return A formatted prompt with context
     */
    public String buildPromptWithContext(ConversationMemory memory, String currentMessage) {
        if (memory == null || memory.isEmpty()) {
            return currentMessage;
        }
        
        StringBuilder contextPrompt = new StringBuilder();
        
        // Add conversation context if there are previous messages
        List<ConversationMessage> recentMessages = getRecentMessages(memory);
        if (!recentMessages.isEmpty()) {
            contextPrompt.append("Previous conversation context:\n");
            
            for (ConversationMessage message : recentMessages) {
                String role = message.isUserMessage() ? "User" : "Assistant";
                contextPrompt.append(role).append(": \"").append(message.getContent()).append("\"\n");
            }
            
            contextPrompt.append("\n");
        }
        
        // Add context metadata if available
        String metadataContext = buildMetadataContext(memory.getContextMetadata());
        if (!metadataContext.isEmpty()) {
            contextPrompt.append("Context information:\n");
            contextPrompt.append(metadataContext);
            contextPrompt.append("\n");
        }
        
        // Add current message
        contextPrompt.append("Current message: \"").append(currentMessage).append("\"");
        
        // Add reference resolution if references are detected
        if (containsReferences(currentMessage)) {
            Map<String, String> resolvedReferences = resolveReferences(currentMessage, memory);
            
            if (!resolvedReferences.isEmpty()) {
                contextPrompt.append("\n\nReference resolutions:\n");
                for (Map.Entry<String, String> entry : resolvedReferences.entrySet()) {
                    contextPrompt.append("- \"").append(entry.getKey()).append("\" likely refers to: ")
                               .append(entry.getValue()).append("\n");
                }
                contextPrompt.append("\nPlease use these reference resolutions when responding to the user's message.");
            } else {
                contextPrompt.append("\n\nNote: The user's message contains references (like 'that', 'it', 'this'). ");
                contextPrompt.append("Please resolve these references using the conversation context above.");
            }
        }
        
        return contextPrompt.toString();
    }
    
    /**
     * Extract context metadata from a message and update the provided metadata map
     * @param message The message to analyze
     * @param contextMetadata The metadata map to update
     */
    public void extractContextFromMessage(String message, Map<String, Object> contextMetadata) {
        if (message == null || message.trim().isEmpty()) {
            return;
        }
        
        // Extract user skills
        extractUserSkills(message, contextMetadata);
        
        // Extract user preferences
        extractUserPreferences(message, contextMetadata);
        
        // Extract topics
        extractTopics(message, contextMetadata);
        
        // Extract entities (technologies, concepts, etc.)
        extractEntities(message, contextMetadata);
    }
    
    /**
     * Check if a message contains pronouns that might need resolution
     * @param message The message to check
     * @return true if pronouns are found
     */
    public boolean containsPronouns(String message) {
        return message != null && PRONOUN_PATTERN.matcher(message).find();
    }
    
    /**
     * Check if a message contains references that need resolution
     * @param message The message to check
     * @return true if references are found
     */
    public boolean containsReferences(String message) {
        if (message == null) return false;
        
        return PRONOUN_PATTERN.matcher(message).find() ||
               DEMONSTRATIVE_PATTERN.matcher(message).find() ||
               REFERENCE_PATTERN.matcher(message).find();
    }
    
    /**
     * Attempt to resolve references in a message using conversation context
     * @param message The message containing references
     * @param memory The conversation memory for context
     * @return A map of potential reference resolutions
     */
    public Map<String, String> resolveReferences(String message, ConversationMemory memory) {
        Map<String, String> resolutions = new HashMap<>();
        
        if (message == null || memory == null || memory.isEmpty()) {
            return resolutions;
        }
        
        List<ConversationMessage> recentMessages = getRecentMessages(memory);
        if (recentMessages.isEmpty()) {
            return resolutions;
        }
        
        // Try to resolve demonstrative references (this/that + noun)
        resolutions.putAll(resolveDemonstrativeReferences(message, recentMessages));
        
        // Try to resolve simple pronouns
        resolutions.putAll(resolvePronounReferences(message, recentMessages));
        
        // Try to resolve explicit references (the above, the previous, etc.)
        resolutions.putAll(resolveExplicitReferences(message, recentMessages));
        
        return resolutions;
    }
    
    /**
     * Resolve demonstrative references like "this problem", "that approach"
     * @param message The current message
     * @param recentMessages Recent conversation messages
     * @return Map of resolved references
     */
    private Map<String, String> resolveDemonstrativeReferences(String message, List<ConversationMessage> recentMessages) {
        Map<String, String> resolutions = new HashMap<>();
        var matcher = DEMONSTRATIVE_PATTERN.matcher(message);
        
        while (matcher.find()) {
            String demonstrative = matcher.group(1); // this/that/these/those
            String noun = matcher.group(2); // the noun being referenced
            String fullReference = matcher.group(0); // full match
            
            // Look for the noun in recent messages
            String resolution = findNounInRecentMessages(noun, recentMessages);
            if (resolution != null) {
                resolutions.put(fullReference, resolution);
            }
        }
        
        return resolutions;
    }
    
    /**
     * Resolve simple pronoun references like "it", "that"
     * @param message The current message
     * @param recentMessages Recent conversation messages
     * @return Map of resolved references
     */
    private Map<String, String> resolvePronounReferences(String message, List<ConversationMessage> recentMessages) {
        Map<String, String> resolutions = new HashMap<>();
        
        if (!recentMessages.isEmpty()) {
            // Get the most recent assistant message as the likely referent
            ConversationMessage lastAssistantMessage = null;
            for (int i = recentMessages.size() - 1; i >= 0; i--) {
                if (recentMessages.get(i).isAssistantMessage()) {
                    lastAssistantMessage = recentMessages.get(i);
                    break;
                }
            }
            
            if (lastAssistantMessage != null) {
                String content = lastAssistantMessage.getContent();
                // Extract key concepts from the last assistant message
                String keyConcept = extractKeyConcept(content);
                
                if (keyConcept != null) {
                    var matcher = PRONOUN_PATTERN.matcher(message);
                    while (matcher.find()) {
                        String pronoun = matcher.group(1);
                        resolutions.put(pronoun, keyConcept);
                    }
                }
            }
        }
        
        return resolutions;
    }
    
    /**
     * Resolve explicit references like "the above", "the previous"
     * @param message The current message
     * @param recentMessages Recent conversation messages
     * @return Map of resolved references
     */
    private Map<String, String> resolveExplicitReferences(String message, List<ConversationMessage> recentMessages) {
        Map<String, String> resolutions = new HashMap<>();
        var matcher = REFERENCE_PATTERN.matcher(message);
        
        while (matcher.find()) {
            String reference = matcher.group(0); // full match
            String referenceType = matcher.group(2); // above/previous/last/earlier/mentioned
            
            String resolution = null;
            switch (referenceType.toLowerCase()) {
                case "above":
                case "previous":
                case "last":
                case "earlier":
                    if (!recentMessages.isEmpty()) {
                        ConversationMessage lastMessage = recentMessages.get(recentMessages.size() - 1);
                        resolution = extractKeyConcept(lastMessage.getContent());
                    }
                    break;
                case "mentioned":
                    // Look for recently mentioned concepts
                    resolution = findRecentlyMentionedConcept(recentMessages);
                    break;
            }
            
            if (resolution != null) {
                resolutions.put(reference, resolution);
            }
        }
        
        return resolutions;
    }
    
    /**
     * Find a specific noun in recent messages
     * @param noun The noun to search for
     * @param recentMessages Recent conversation messages
     * @return Context where the noun was found, or null
     */
    private String findNounInRecentMessages(String noun, List<ConversationMessage> recentMessages) {
        for (int i = recentMessages.size() - 1; i >= 0; i--) {
            ConversationMessage message = recentMessages.get(i);
            String content = message.getContent().toLowerCase();
            
            if (content.contains(noun.toLowerCase())) {
                // Return a snippet of context around the noun
                return extractContextAroundNoun(message.getContent(), noun);
            }
        }
        return null;
    }
    
    /**
     * Extract key concept from a message (first significant noun or phrase)
     * @param content The message content
     * @return The key concept or null
     */
    private String extractKeyConcept(String content) {
        if (content == null || content.trim().isEmpty()) {
            return null;
        }
        
        // Look for technical terms first
        String[] techTerms = {
            "algorithm", "function", "method", "class", "variable", "array", "list",
            "database", "query", "API", "framework", "library", "pattern", "design",
            "error", "exception", "bug", "issue", "problem", "solution", "approach"
        };
        
        String lowerContent = content.toLowerCase();
        for (String term : techTerms) {
            if (lowerContent.contains(term)) {
                return term;
            }
        }
        
        // Extract first capitalized word that might be a concept
        Pattern conceptPattern = Pattern.compile("\\b[A-Z][a-zA-Z]{2,}\\b");
        var matcher = conceptPattern.matcher(content);
        if (matcher.find()) {
            String concept = matcher.group();
            if (!isCommonWord(concept)) {
                return concept;
            }
        }
        
        // Fallback: return first few words
        String[] words = content.trim().split("\\s+");
        if (words.length > 0) {
            return words.length > 3 ? 
                String.join(" ", Arrays.copyOf(words, 3)) + "..." : 
                String.join(" ", words);
        }
        
        return null;
    }
    
    /**
     * Extract context around a specific noun in content
     * @param content The full content
     * @param noun The noun to find context for
     * @return Context snippet around the noun
     */
    private String extractContextAroundNoun(String content, String noun) {
        String[] words = content.split("\\s+");
        for (int i = 0; i < words.length; i++) {
            if (words[i].toLowerCase().contains(noun.toLowerCase())) {
                // Extract context window around the noun
                int start = Math.max(0, i - 3);
                int end = Math.min(words.length, i + 4);
                return String.join(" ", Arrays.copyOfRange(words, start, end));
            }
        }
        return content.length() > 50 ? content.substring(0, 50) + "..." : content;
    }
    
    /**
     * Find recently mentioned concept across multiple messages
     * @param recentMessages Recent conversation messages
     * @return The most recently mentioned concept
     */
    private String findRecentlyMentionedConcept(List<ConversationMessage> recentMessages) {
        // Look through recent messages for repeated concepts
        Map<String, Integer> conceptCounts = new HashMap<>();
        
        for (ConversationMessage message : recentMessages) {
            String concept = extractKeyConcept(message.getContent());
            if (concept != null) {
                conceptCounts.put(concept, conceptCounts.getOrDefault(concept, 0) + 1);
            }
        }
        
        // Return the most frequently mentioned concept
        return conceptCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }
    
    /**
     * Get the most recent messages for context, limited by configuration
     * @param memory The conversation memory
     * @return List of recent messages
     */
    private List<ConversationMessage> getRecentMessages(ConversationMemory memory) {
        List<ConversationMessage> messages = memory.getMessages();
        int maxContextMessages = config.getMaxContextMessages();
        
        if (messages.size() <= maxContextMessages) {
            return new ArrayList<>(messages);
        }
        
        // Return the most recent messages
        return messages.subList(messages.size() - maxContextMessages, messages.size());
    }
    
    /**
     * Build context string from metadata
     * @param contextMetadata The metadata map
     * @return Formatted context string
     */
    private String buildMetadataContext(Map<String, Object> contextMetadata) {
        if (contextMetadata == null || contextMetadata.isEmpty()) {
            return "";
        }
        
        StringBuilder context = new StringBuilder();
        
        // Add user skills
        Object skills = contextMetadata.get("userSkills");
        if (skills instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> skillList = (List<String>) skills;
            if (!skillList.isEmpty()) {
                context.append("- User skills: ").append(String.join(", ", skillList)).append("\n");
            }
        }
        
        // Add topics
        Object topics = contextMetadata.get("topics");
        if (topics instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> topicList = (List<String>) topics;
            if (!topicList.isEmpty()) {
                context.append("- Discussion topics: ").append(String.join(", ", topicList)).append("\n");
            }
        }
        
        // Add preferences
        Object preferences = contextMetadata.get("preferences");
        if (preferences instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> prefList = (List<String>) preferences;
            if (!prefList.isEmpty()) {
                context.append("- User preferences: ").append(String.join(", ", prefList)).append("\n");
            }
        }
        
        // Add entities
        Object entities = contextMetadata.get("entities");
        if (entities instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> entityList = (List<String>) entities;
            if (!entityList.isEmpty()) {
                context.append("- Mentioned entities: ").append(String.join(", ", entityList)).append("\n");
            }
        }
        
        return context.toString();
    }
    
    /**
     * Extract user skills from message content
     * @param message The message to analyze
     * @param contextMetadata The metadata map to update
     */
    @SuppressWarnings("unchecked")
    private void extractUserSkills(String message, Map<String, Object> contextMetadata) {
        var matcher = SKILL_PATTERN.matcher(message);
        
        List<String> skills = (List<String>) contextMetadata.computeIfAbsent("userSkills", k -> new ArrayList<String>());
        
        while (matcher.find()) {
            String proficiency = matcher.group(2) != null ? "beginner" : matcher.group(3);
            String skill = matcher.group(4);
            String skillEntry = skill + ": " + proficiency;
            
            if (!skills.contains(skillEntry)) {
                skills.add(skillEntry);
            }
        }
    }
    
    /**
     * Extract user preferences from message content
     * @param message The message to analyze
     * @param contextMetadata The metadata map to update
     */
    @SuppressWarnings("unchecked")
    private void extractUserPreferences(String message, Map<String, Object> contextMetadata) {
        var matcher = PREFERENCE_PATTERN.matcher(message);
        
        List<String> preferences = (List<String>) contextMetadata.computeIfAbsent("preferences", k -> new ArrayList<String>());
        
        while (matcher.find()) {
            String preference = matcher.group(1) + " " + matcher.group(3);
            if (!preferences.contains(preference)) {
                preferences.add(preference);
            }
        }
    }
    
    /**
     * Extract topics from message content
     * @param message The message to analyze
     * @param contextMetadata The metadata map to update
     */
    @SuppressWarnings("unchecked")
    private void extractTopics(String message, Map<String, Object> contextMetadata) {
        var matcher = TOPIC_PATTERN.matcher(message);
        
        List<String> topics = (List<String>) contextMetadata.computeIfAbsent("topics", k -> new ArrayList<String>());
        
        while (matcher.find()) {
            String topic = matcher.group(2);
            if (!topics.contains(topic)) {
                topics.add(topic);
            }
        }
        
        // Also extract common programming/technology terms
        extractTechnologyTopics(message, topics);
    }
    
    /**
     * Extract technology and programming related topics
     * @param message The message to analyze
     * @param topics The topics list to update
     */
    private void extractTechnologyTopics(String message, List<String> topics) {
        String[] techKeywords = {
            "java", "python", "javascript", "typescript", "react", "spring", "boot",
            "database", "sql", "api", "rest", "microservices", "docker", "kubernetes",
            "programming", "coding", "development", "software", "web", "frontend", "backend"
        };
        
        String lowerMessage = message.toLowerCase();
        for (String keyword : techKeywords) {
            if (lowerMessage.contains(keyword) && !topics.contains(keyword)) {
                topics.add(keyword);
            }
        }
    }
    
    /**
     * Extract entities (technologies, concepts, etc.) from message content
     * @param message The message to analyze
     * @param contextMetadata The metadata map to update
     */
    @SuppressWarnings("unchecked")
    private void extractEntities(String message, Map<String, Object> contextMetadata) {
        List<String> entities = (List<String>) contextMetadata.computeIfAbsent("entities", k -> new ArrayList<String>());
        
        // Extract capitalized words that might be entities (technologies, frameworks, etc.)
        Pattern entityPattern = Pattern.compile("\\b[A-Z][a-zA-Z]+\\b");
        var matcher = entityPattern.matcher(message);
        
        while (matcher.find()) {
            String entity = matcher.group();
            // Filter out common words that aren't entities
            if (!isCommonWord(entity) && !entities.contains(entity)) {
                entities.add(entity);
            }
        }
    }
    
    /**
     * Check if a word is a common word that shouldn't be treated as an entity
     * @param word The word to check
     * @return true if it's a common word
     */
    private boolean isCommonWord(String word) {
        String[] commonWords = {
            "I", "You", "The", "This", "That", "How", "What", "When", "Where", "Why",
            "Can", "Could", "Should", "Would", "Will", "Do", "Does", "Did", "Have",
            "Has", "Had", "Is", "Are", "Was", "Were", "Be", "Been", "Being"
        };
        
        return Arrays.asList(commonWords).contains(word);
    }
}