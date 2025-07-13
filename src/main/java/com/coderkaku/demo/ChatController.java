package com.coderkaku.demo;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import io.github.wimdeblauwe.htmx.spring.boot.mvc.HtmxRequest;
import reactor.core.publisher.Flux;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

@Controller
@RequestMapping("/chat")
public class ChatController {

    private final ChatClient.Builder chatClientBuilder;
    private final MarkdownService markdownService;

    public ChatController(ChatClient.Builder chatClientBuilder, MarkdownService markdownService) {
        this.chatClientBuilder = chatClientBuilder;
        this.markdownService = markdownService;
    }

    @GetMapping
    public String chat(@RequestParam String message, Model model, HtmxRequest htmxRequest) {
        try {
            System.out.println("Received chat request: " + message);
            System.out.println("Creating chat client...");

            ChatClient chatClient = chatClientBuilder.build();
            System.out.println("Chat client created successfully");

            System.out.println("Sending request to LLM...");
            String response = chatClient
                    .prompt(message)
                    .call()
                    .content();

            System.out.println("Chat response received successfully: " +
                    (response != null ? response.substring(0, Math.min(100, response.length())) : "null response"));

            // Convert markdown to HTML
            String htmlResponse = markdownService.convertMarkdownToHtml(response);

            // Add the response to the model for Thymeleaf
            model.addAttribute("response", response);
            model.addAttribute("htmlResponse", htmlResponse);
            model.addAttribute("message", message);

            // If it's an HTMX request, return just the response fragment
            if (htmxRequest.isHtmxRequest()) {
                return "fragments/chat-response";
            }

            // Otherwise return the full page
            return "index";

        } catch (Exception e) {
            System.err.println("Error processing chat request: " + e.getMessage());
            e.printStackTrace();
            model.addAttribute("error", "Error processing request: " + e.getMessage());

            if (htmxRequest.isHtmxRequest()) {
                return "fragments/chat-error";
            }

            return "index";
        }
    }

    @GetMapping("/stream")
    public SseEmitter streamChat(@RequestParam String message) {
        SseEmitter emitter = new SseEmitter(60000L); // 60 seconds timeout

        CompletableFuture.runAsync(() -> {
            try {
                System.out.println("Received streaming chat request: " + message);

                // Try streaming first, fallback to regular if not supported
                try {
                    System.out.println("Starting streaming request to LLM...");

                    ChatClient chatClient = chatClientBuilder.build();

                    System.out.println("Chat client created, attempting to stream...");

                    Flux<String> responseFlux = chatClient
                            .prompt(message)
                            .stream()
                            .content();

                    System.out.println("Stream flux created, subscribing...");

                    StringBuilder fullResponse = new StringBuilder();

                    responseFlux.subscribe(
                            chunk -> {
                                try {
                                    System.out.println("Received streaming chunk: " + chunk);
                                    fullResponse.append(chunk);

                                    // Convert full accumulated response to HTML
                                    String htmlContent = markdownService.convertMarkdownToHtml(fullResponse.toString());

                                    System.out.println("Sending chunk to frontend...");
                                    // Send the full HTML response so far
                                    emitter.send(SseEmitter.event()
                                            .name("chunk")
                                            .data(htmlContent));

                                } catch (IOException e) {
                                    System.err.println("Error sending chunk: " + e.getMessage());
                                    emitter.completeWithError(e);
                                }
                            },
                            error -> {
                                System.err.println("Error in streaming: " + error.getMessage());
                                error.printStackTrace();
                                // Send error to frontend
                                try {
                                    emitter.send(SseEmitter.event()
                                            .name("error")
                                            .data("Streaming error: " + error.getMessage()));
                                } catch (IOException ioException) {
                                    System.err.println("Error sending error message: " + ioException.getMessage());
                                }
                                // Fallback to regular call
                                fallbackToRegularCall(message, emitter);
                            },
                            () -> {
                                System.out.println("Streaming completed successfully");
                                emitter.complete();
                            });

                } catch (Exception streamError) {
                    System.err.println(
                            "Streaming not supported, falling back to regular call: " + streamError.getMessage());
                    streamError.printStackTrace();
                    fallbackToRegularCall(message, emitter);
                }

            } catch (Exception e) {
                System.err.println("Error processing streaming chat request: " + e.getMessage());
                e.printStackTrace();

                // Send error to frontend
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data("Error: " + e.getMessage()));
                } catch (IOException ioException) {
                    System.err.println("Error sending error message: " + ioException.getMessage());
                }
                emitter.complete();
            }
        });

        return emitter;
    }

    private void fallbackToRegularCall(String message, SseEmitter emitter) {
        try {
            System.out.println("Executing fallback to regular call...");

            String response = chatClientBuilder.build()
                    .prompt(message)
                    .call()
                    .content();

            System.out.println("Fallback response received: " +
                    (response != null ? response.substring(0, Math.min(100, response.length())) : "null"));

            String htmlContent = markdownService.convertMarkdownToHtml(response);

            emitter.send(SseEmitter.event()
                    .name("chunk")
                    .data(htmlContent));

            emitter.complete();
            System.out.println("Fallback response completed");

        } catch (Exception e) {
            System.err.println("Error in fallback call: " + e.getMessage());
            e.printStackTrace();
            try {
                emitter.send(SseEmitter.event()
                        .name("error")
                        .data("Error: " + e.getMessage()));
            } catch (IOException ioException) {
                System.err.println("Error sending error message: " + ioException.getMessage());
            }
            emitter.complete();
        }
    }
}
