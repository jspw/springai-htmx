package com.coderkaku.demo.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.coderkaku.demo.services.MarkdownService;
import com.coderkaku.demo.services.ConversationService;

import io.github.wimdeblauwe.htmx.spring.boot.mvc.HtmxRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import reactor.core.publisher.Flux;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

@Controller
@RequestMapping("/chat")
public class ChatController {

    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);

    private final ChatClient.Builder chatClientBuilder;
    private final MarkdownService markdownService;
    private final ConversationService conversationService;

    private volatile long lastRequestTime = 0;
    private static final long MIN_REQUEST_INTERVAL = 2000;

    public ChatController(ChatClient.Builder chatClientBuilder, MarkdownService markdownService,
            ConversationService conversationService) {
        this.chatClientBuilder = chatClientBuilder;
        this.markdownService = markdownService;
        this.conversationService = conversationService;
    }

    @PostMapping
    public String handleChatMessage(@RequestParam String message, Model model, HtmxRequest htmxRequest,
            HttpServletRequest request, HttpSession session) {
        logger.info("=== POST /chat called with message: " + message + " (Session: " + session.getId() + ") ===");

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastRequestTime < MIN_REQUEST_INTERVAL) {
            logger.info("Request rate limited - too frequent");
            model.addAttribute("error", "Please wait before sending another message");
            if (htmxRequest.isHtmxRequest()) {
                return "fragments/chat-error";
            }
            return "index";
        }
        lastRequestTime = currentTime;

        if (message == null || message.trim().isEmpty()) {
            model.addAttribute("error", "Please enter a message");
            if (htmxRequest.isHtmxRequest()) {
                return "fragments/chat-error";
            }
            return "index";
        }

        try {
            // Store user message in conversation memory
            conversationService.addUserMessage(session, message);
            logger.info("User message stored in conversation memory (Session: " + session.getId() + ")");
        } catch (Exception e) {
            logger.warn("Failed to store user message in conversation memory (Session: " + session.getId() + "): "
                    + e.getMessage());
            // Continue processing even if memory storage fails
        }

        model.addAttribute("message", message);
        model.addAttribute("streamUrl", request.getContextPath() + "/chat/stream?message="
                + java.net.URLEncoder.encode(message, java.nio.charset.StandardCharsets.UTF_8));

        if (htmxRequest.isHtmxRequest()) {
            return "fragments/user-message-with-stream";
        }

        return "index";
    }

    @GetMapping("/stream")
    public SseEmitter streamChatResponse(@RequestParam String message, HttpSession session) {
        String sessionId = session.getId();
        logger.info("=== SSE /stream called with message: " + message + " (Session: " + sessionId + ") ===");

        if (message == null || message.trim().isEmpty()) {
            logger.warn("Error: Empty message provided to stream endpoint (Session: " + sessionId + ")");
            SseEmitter emitter = new SseEmitter(1000L);
            try {
                emitter.send(SseEmitter.event()
                        .name("error-message")
                        .data("<div class='error-text'>No message provided</div>"));
                emitter.send(SseEmitter.event()
                        .name("complete")
                        .data("stream-complete"));
                emitter.complete();
            } catch (IOException e) {
                logger.error("Error sending empty message error (Session: " + sessionId + "): " + e.getMessage());
                emitter.completeWithError(e);
            }
            return emitter;
        }

        SseEmitter emitter = new SseEmitter(60000L);

        CompletableFuture.runAsync(() -> {
            try {
                logger.info("Received streaming chat request: " + message + " (Session: " + sessionId + ")");

                // Send initial acknowledgment to test connection
                try {
                    emitter.send(SseEmitter.event()
                            .name("message")
                            .data("<div class='typing-indicator'>Thinking...</div>"));
                    logger.info("Initial connection test successful (Session: " + sessionId + ")");
                } catch (IOException connectionTest) {
                    logger.error("Initial connection test failed (Session: " + sessionId + "): "
                            + connectionTest.getMessage());
                    throw new RuntimeException("Connection failed at startup", connectionTest);
                }

                try {
                    logger.info("Starting streaming request to LLM... (Session: " + sessionId + ")");

                    // Build contextual prompt using conversation memory
                    String contextualPrompt;
                    try {
                        contextualPrompt = conversationService.buildContextualPrompt(session, message);
                        logger.info("Built contextual prompt for LLM (Session: " + sessionId + ")");
                        if (conversationService.containsPronouns(message)) {
                            logger.info("Message contains pronouns - using contextual resolution (Session: " + sessionId
                                    + ")");
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to build contextual prompt, using original message (Session: " + sessionId
                                + "): " + e.getMessage());
                        contextualPrompt = message;
                    }

                    ChatClient chatClient = chatClientBuilder.build();

                    logger.info("Chat client created, attempting to stream... (Session: " + sessionId + ")");
                    logger.info("About to call .stream() method... (Session: " + sessionId + ")");

                    Flux<String> responseFlux = chatClient
                            .prompt(contextualPrompt)
                            .stream()
                            .content();

                    logger.info("Stream flux created, subscribing... (Session: " + sessionId + ")");

                    StringBuilder fullResponse = new StringBuilder();

                    responseFlux.subscribe(
                            chunk -> {
                                try {
                                    if (!isEmitterClosed(emitter)) {
                                        fullResponse.append(chunk);
                                        String htmlContent = markdownService
                                                .convertMarkdownToHtml(fullResponse.toString());
                                        emitter.send(SseEmitter.event()
                                                .name("message")
                                                .data(htmlContent));
                                    }
                                } catch (IOException e) {
                                    if (isBrokenPipe(e)) {
                                        logger.info(
                                                "Client disconnected during streaming (Session: " + sessionId + ")");
                                    } else {
                                        logger.error(
                                                "Error sending chunk (Session: " + sessionId + "): " + e.getMessage());
                                    }
                                    safeCompleteWithError(emitter, e);
                                }
                            },
                            error -> {
                                logger.error("Error in streaming (Session: " + sessionId + "): " + error.getMessage());
                                error.printStackTrace(); // Print full stack trace for debugging

                                if (!isEmitterClosed(emitter)) {
                                    try {
                                        String errorHtml = "<div class='error-text'>Streaming error: "
                                                + error.getMessage()
                                                + "</div>";
                                        emitter.send(SseEmitter.event()
                                                .name("error-message")
                                                .data(errorHtml));
                                        emitter.send(SseEmitter.event()
                                                .name("complete")
                                                .data("stream-complete"));
                                        emitter.complete();
                                    } catch (IOException ioException) {
                                        if (isBrokenPipe(ioException)) {
                                            logger.info("Client disconnected while sending error (Session: " + sessionId
                                                    + ")");
                                        } else {
                                            logger.error("Error sending error message (Session: " + sessionId + "): "
                                                    + ioException.getMessage());
                                        }
                                        // Don't fallback if we couldn't send the error message due to connection issues
                                        safeCompleteWithError(emitter, ioException);
                                    }
                                } else {
                                    logger.info("Emitter already closed, trying fallback (Session: " + sessionId + ")");
                                    fallbackToRegularCall(message, emitter, sessionId, session);
                                }
                            },
                            () -> {
                                logger.info("Streaming completed successfully (Session: " + sessionId + ")");

                                // Store assistant response in conversation memory
                                try {
                                    String assistantResponse = fullResponse.toString();
                                    if (!assistantResponse.trim().isEmpty()) {
                                        conversationService.addAssistantMessage(session, assistantResponse);
                                        logger.info("Assistant response stored in conversation memory (Session: "
                                                + sessionId + ")");
                                    }
                                } catch (Exception e) {
                                    logger.warn("Failed to store assistant response in conversation memory (Session: "
                                            + sessionId + "): " + e.getMessage());
                                }

                                if (!isEmitterClosed(emitter)) {
                                    try {
                                        emitter.send(SseEmitter.event()
                                                .name("complete")
                                                .data("stream-complete"));
                                        emitter.complete();
                                    } catch (IOException e) {
                                        if (isBrokenPipe(e)) {
                                            logger.info("Client disconnected during completion (Session: " + sessionId
                                                    + ")");
                                        } else {
                                            logger.error(
                                                    "Error sending completion signal (Session: " + sessionId + "): "
                                                            + e.getMessage());
                                        }
                                    }
                                }
                            });

                } catch (Exception streamError) {
                    logger.error("Streaming not supported or failed, attempting fallback (Session: " + sessionId + "): "
                            + streamError.getMessage());
                    streamError.printStackTrace(); // Print full stack trace for debugging

                    // Try to send an informative error message before fallback
                    if (!isEmitterClosed(emitter)) {
                        try {
                            String errorHtml = "<div class='error-text'>Streaming unavailable, switching to standard response...</div>";
                            emitter.send(SseEmitter.event()
                                    .name("error-message")
                                    .data(errorHtml));
                        } catch (IOException ioException) {
                            logger.warn("Could not send fallback notice (Session: " + sessionId + "): "
                                    + ioException.getMessage());
                        }
                    }

                    fallbackToRegularCall(message, emitter, sessionId, session);
                }

            } catch (Exception e) {
                logger.error("Error processing streaming chat request (Session: " + sessionId + "): " + e.getMessage());
                e.printStackTrace(); // Print full stack trace for debugging

                if (!isEmitterClosed(emitter)) {
                    try {
                        String errorHtml = "<div class='error-text'>Server error: " + e.getMessage() + "</div>";
                        emitter.send(SseEmitter.event()
                                .name("error-message")
                                .data(errorHtml));
                        emitter.send(SseEmitter.event()
                                .name("complete")
                                .data("stream-complete"));
                        emitter.complete();
                    } catch (IOException ioException) {
                        logger.error(
                                "Error sending error message (Session: " + sessionId + "): "
                                        + ioException.getMessage());
                        safeCompleteWithError(emitter, ioException);
                    }
                } else {
                    logger.info("Emitter already closed during error handling (Session: " + sessionId + ")");
                }
            }
        });

        return emitter;
    }

    private void fallbackToRegularCall(String message, SseEmitter emitter, String sessionId, HttpSession session) {
        if (isEmitterClosed(emitter)) {
            logger.info("Emitter already closed, skipping fallback call (Session: " + sessionId + ")");
            return;
        }

        try {
            logger.info("Executing fallback to regular call... (Session: " + sessionId + ")");

            // Build contextual prompt for fallback call as well
            String contextualPrompt;
            try {
                contextualPrompt = conversationService.buildContextualPrompt(session, message);
                logger.info("Built contextual prompt for fallback call (Session: " + sessionId + ")");
            } catch (Exception e) {
                logger.warn("Failed to build contextual prompt for fallback, using original message (Session: "
                        + sessionId + "): " + e.getMessage());
                contextualPrompt = message;
            }

            String response = chatClientBuilder.build()
                    .prompt(contextualPrompt)
                    .call()
                    .content();

            logger.info("Fallback response received (Session: " + sessionId + "): " +
                    (response != null ? response.substring(0, Math.min(100, response.length())) : "null"));

            // Store assistant response in conversation memory
            try {
                if (response != null && !response.trim().isEmpty()) {
                    conversationService.addAssistantMessage(session, response);
                    logger.info(
                            "Assistant fallback response stored in conversation memory (Session: " + sessionId + ")");
                }
            } catch (Exception e) {
                logger.warn("Failed to store assistant fallback response in conversation memory (Session: " + sessionId
                        + "): " + e.getMessage());
            }

            if (!isEmitterClosed(emitter)) {
                try {
                    String htmlContent = markdownService.convertMarkdownToHtml(response);
                    emitter.send(SseEmitter.event()
                            .name("message")
                            .data(htmlContent));
                    emitter.send(SseEmitter.event()
                            .name("complete")
                            .data("stream-complete"));
                    emitter.complete();
                    logger.info("Fallback response completed (Session: " + sessionId + ")");
                } catch (IOException e) {
                    if (isBrokenPipe(e)) {
                        logger.info("Client disconnected during fallback response (Session: " + sessionId + ")");
                    } else {
                        logger.error("Error sending fallback response (Session: " + sessionId + "): " + e.getMessage());
                    }
                }
            }

        } catch (Exception e) {
            logger.error("Error in fallback call (Session: " + sessionId + "): " + e.getMessage());
            e.printStackTrace(); // Print full stack trace for debugging

            if (!isEmitterClosed(emitter)) {
                try {
                    String errorHtml = "<div class='error-text'>Server error: " + e.getMessage() + "</div>";
                    emitter.send(SseEmitter.event()
                            .name("error-message")
                            .data(errorHtml));
                    emitter.send(SseEmitter.event()
                            .name("complete")
                            .data("stream-complete"));
                    emitter.complete();
                } catch (IOException ioException) {
                    if (isBrokenPipe(ioException)) {
                        logger.info("Client disconnected during fallback error handling (Session: " + sessionId + ")");
                    } else {
                        logger.error("Error sending fallback error message (Session: " + sessionId + "): "
                                + ioException.getMessage());
                    }
                    safeCompleteWithError(emitter, ioException);
                }
            }
        }
    }

    private boolean isEmitterClosed(SseEmitter emitter) {
        try {
            // Use reflection to check if emitter is completed
            java.lang.reflect.Field completedField = SseEmitter.class.getDeclaredField("complete");
            completedField.setAccessible(true);
            return (Boolean) completedField.get(emitter);
        } catch (Exception e) {
            // If we can't determine the state, assume it's open
            return false;
        }
    }

    private boolean isBrokenPipe(Exception e) {
        return e instanceof IOException &&
                (e.getMessage().contains("Broken pipe") ||
                        e.getMessage().contains("Connection reset") ||
                        e.getMessage().contains("Connection aborted"));
    }

    private void safeCompleteWithError(SseEmitter emitter, Exception e) {
        try {
            if (!isEmitterClosed(emitter)) {
                emitter.completeWithError(e);
            }
        } catch (Exception ignored) {
            // Ignore any errors during completion
        }
    }
}
