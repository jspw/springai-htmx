# The Great Spring AI Streaming Mystery: A Debugging Odyssey

## Prologue: The Weekend Experiment

It all began on a lazy Saturday afternoon. As a Java/JS developer who usually works with Spring Boot, I decided to venture into the exciting world of AI development. My experience with AI had been limited to Python-based RAG projects in hackathons, so the prospect of building an AI chat application using Spring AI and HTMX was both thrilling and slightly intimidating.

The goal was simple yet ambitious: create a chat application with smooth, real-time streaming capabilities powered by local LLM models running on LM Studio. I wanted to combine the power of Spring AI with the reactive nature of HTMX to create a seamless user experience.

## Chapter 1: The Initial Success

The journey started with a bang! The non-streaming chat functionality worked like a charm using Spring AI's `ChatClient`. I was able to send messages to LM Studio and receive responses without any issues. The integration was smooth, and I was feeling confident about the project.

```java
@Autowired
private ChatClient chatClient;

public String chat(String message) {
    return chatClient.prompt(message).call().content();
}
```

Simple, clean, and working perfectly. I was ready to take it to the next level: streaming.

## Chapter 2: The Streaming Nightmare Begins

When I switched to streaming, everything fell apart. The application wasn't even hitting LM Studio's endpoint! Instead of the expected streaming response, I was getting timeout errors and media type negotiation failures.

```java
public Flux<String> streamChat(String message) {
    return chatClient.prompt(message).stream().content();
}
```

The logs showed:
```
WARN --- [demo] [nio-8080-exec-7] .w.s.m.s.DefaultHandlerExceptionResolver : 
Resolved [org.springframework.web.context.request.async.AsyncRequestTimeoutException]

WARN --- [demo] [nio-8080-exec-7] .m.HtmxExceptionHandlerExceptionResolver : 
Resolved [org.springframework.web.HttpMediaTypeNotAcceptableException: No acceptable representation]
```

This was the beginning of what would become an epic debugging journey.

## Chapter 3: The Theories and Experiments

### Theory 1: HTTP Protocol Issues
My first hypothesis was that this was a protocol problem. Maybe LM Studio didn't support HTTP/2 for streaming, or there was a mismatch between HTTP/1.1 and HTTP/2.

I tried configuring custom HTTP clients with different protocols:
```java
@Bean
public WebClient.Builder webClientBuilder() {
    HttpClient httpClient = HttpClient.create()
        .protocol(HttpProtocol.HTTP11); // Tried HTTP/2 too
    
    return WebClient.builder()
        .clientConnector(new ReactorClientHttpConnector(httpClient));
}
```

**Result**: ❌ Still didn't work.

### Theory 2: LM Studio Compatibility
Maybe LM Studio had specific requirements or compatibility issues with Spring AI's streaming implementation.

**Result**: ❌ Other tools worked fine with LM Studio streaming.

### Theory 3: Custom HttpClient Configuration
Perhaps Spring AI needed a specific HTTP client configuration for streaming to work properly.

**Result**: ❌ Various configurations didn't help.

### Theory 4: Spring AI's Default Configuration
Maybe there was something wrong with Spring AI's default HTTP client setup.

**Result**: ❌ The defaults should work for most cases.

## Chapter 4: The Plot Twist

While experimenting with custom Reactor Netty HttpClient configurations, my GitHub Copilot suggested adding the `reactor-netty-http` dependency:

```xml
<dependency>
    <groupId>io.projectreactor.netty</groupId>
    <artifactId>reactor-netty-http</artifactId>
</dependency>
```

**And suddenly, everything worked!** ��

Streaming started working perfectly. But this raised more questions than it answered. Why did this fix the issue? What was Spring AI using before? Was this a dependency management problem?

## Chapter 5: The Deep Dive Investigation

### The Dependency Tree Revelation

I decided to investigate the dependency chain. I ran `mvn dependency:tree` on my project to see what was actually being pulled in:

```bash
mvn dependency:tree | grep reactor-netty
```

**Shockingly, there was no `reactor-netty-http` dependency anywhere!**

This was the first clue that something was fundamentally wrong with the dependency resolution.

### The Spring AI Source Code Exploration

I cloned the Spring AI repository and started exploring the source code to understand what was happening.

#### Step 1: ChatClient.java
I found the `ChatClient` interface in `spring-ai-client-chat/src/main/java/org/springframework/ai/chat/client/ChatClient.java`. This was the high-level interface that provided the fluent API:

```java
public interface ChatClient {
    static ChatClient create(ChatModel chatModel) {
        return create(chatModel, ObservationRegistry.NOOP);
    }
    
    ChatClientRequestSpec prompt();
    ChatClientRequestSpec prompt(String content);
    ChatClientRequestSpec prompt(Prompt prompt);
}
```

The `ChatClient` was just a wrapper around a `ChatModel` implementation.

#### Step 2: OpenAiChatModel.java
I traced the call to `OpenAiChatModel.java` in `models/spring-ai-openai/src/main/java/org/springframework/ai/openai/OpenAiChatModel.java`. This was where the real streaming logic lived:

```java
@Override
public Flux<ChatResponse> stream(Prompt prompt) {
    Prompt requestPrompt = buildRequestPrompt(prompt);
    return internalStream(requestPrompt, null);
}

public Flux<ChatResponse> internalStream(Prompt prompt, ChatResponse previousChatResponse) {
    return Flux.deferContextual(contextView -> {
        ChatCompletionRequest request = createRequest(prompt, true);
        
        Flux<OpenAiApi.ChatCompletionChunk> completionChunks = 
            this.openAiApi.chatCompletionStream(request, getAdditionalHttpHeaders(prompt));
        
        // ... complex streaming logic ...
    });
}
```

#### Step 3: OpenAiApi.java
The actual HTTP calls were made in `OpenAiApi.java`:

```java
public Flux<ChatCompletionChunk> chatCompletionStream(ChatCompletionRequest chatRequest,
        MultiValueMap<String, String> additionalHttpHeader) {
    
    return this.webClient.post()
        .uri(this.completionsPath)
        .headers(headers -> {
            headers.addAll(additionalHttpHeader);
            addDefaultHeadersIfMissing(headers);
        })
        .body(Mono.just(chatRequest), ChatCompletionRequest.class)
        .retrieve()
        .bodyToFlux(String.class)
        .takeUntil(SSE_DONE_PREDICATE)
        .filter(SSE_DONE_PREDICATE.negate())
        .map(content -> ModelOptionsUtils.jsonToObject(content, ChatCompletionChunk.class))
        // ... more streaming logic ...
}
```

**This was the smoking gun!** The code was using `WebClient` for streaming, which required proper HTTP client support for Server-Sent Events (SSE).

### The Dependency Tree Investigation

I ran `mvn dependency:tree` on the Spring AI OpenAI module itself to see what dependencies it had:

```
[INFO] org.springframework.ai:spring-ai-openai:jar:1.1.0-SNAPSHOT
[INFO] +- org.springframework:spring-webflux:jar:6.2.7:compile
[INFO] +- org.slf4j:slf4j-api:jar:2.0.17:compile
[INFO] +- org.springframework.ai:spring-ai-model:jar:1.1.0-SNAPSHOT:compile
[INFO] +- org.springframework.ai:spring-ai-retry:jar:1.1.0-SNAPSHOT:compile
[INFO] +- com.github.victools:jsonschema-generator:jar:4.37.0:compile
[INFO] +- com.github.victools:jsonschema-module-jackson:jar:4.37.0:compile
[INFO] +- org.springframework:spring-context-support:jar:6.2.7:compile
```

**Notice what's missing: NO `reactor-netty-http` dependency!**

This was the critical discovery. `spring-webflux` was included, but `reactor-netty-http` was nowhere to be found.

## Chapter 6: The Spring Framework Source Code Deep Dive

### The Spring WebFlux Investigation

I decided to go even deeper and explore the Spring Framework source code itself. I cloned the Spring Framework repository and found the `spring-webflux` module.

#### The Gradle File Discovery

Inside the `spring-webflux` project, I found the `spring-webflux.gradle` file. And there it was - the smoking gun:

```gradle
dependencies {
    api(project(":spring-beans"))
    api(project(":spring-core"))
    api(project(":spring-web"))
    api("io.projectreactor:reactor-core")
    compileOnly("com.google.code.findbugs:jsr305")
    optional(project(":spring-context"))
    optional(project(":spring-context-support"))
    optional("com.fasterxml.jackson.core:jackson-databind")
    optional("com.fasterxml.jackson.dataformat:jackson-dataformat-smile")
    optional("com.google.protobuf:protobuf-java-util")
    optional("io.projectreactor.netty:reactor-netty-http")  // <-- HERE IT IS!
}
```

**The key word was `optional`!**

### Understanding Optional Dependencies

In Gradle/Maven, `optional` dependencies are:
- **NOT** automatically included when you depend on the module
- **NOT** transitively pulled in
- **Available** if you explicitly add them
- **Used** by the module if present, but the module works without them

This was the root cause of all my problems!

## Chapter 7: The Complete Picture Emerges

### Why This Happened

1. **Spring AI** depends on `spring-webflux`
2. **Spring WebFlux** declares `reactor-netty-http` as **optional**
3. **My project** only had `spring-boot-starter-web` (servlet-based)
4. **WebClient** fell back to JDK HTTP client (Spring Boot 3.2+ default)
5. **JDK HTTP client** doesn't handle SSE properly for streaming
6. **Result**: Timeouts and media type errors

### The Dependency Chain Reality

**Without explicit reactor-netty-http:**
```
spring-ai-openai
  └── spring-webflux (API + optional reactor-netty-http)
      ├── WebClient API ✅
      ├── reactor-netty-http ❌ (optional, not included)
      └── Fallback to JDK HTTP client ❌ (no SSE support)
```

**With explicit reactor-netty-http:**
```
spring-ai-openai
  └── spring-webflux (API + optional reactor-netty-http)
      ├── WebClient API ✅
      ├── reactor-netty-http ✅ (explicitly added)
      └── Proper SSE streaming ✅
```

### Why My Custom Configs "Worked"

When I added custom Reactor Netty configuration:
```java
HttpClient httpClient = HttpClient.create()
    .protocol(HttpProtocol.HTTP11);
```

The act of importing and using Reactor Netty classes forced the classloader to load the correct `reactor-netty-http` dependency, which then made streaming work.

### The "Plot Twist" Explained

When I commented out my custom config but streaming still worked:
- The **first successful run** with custom config had already loaded the correct `reactor-netty-http` classes into the JVM
- The classloader **cached** these classes, so subsequent runs worked even without explicit configuration
- This is a classic case of **class loading side effects** masking the real issue

## Chapter 8: The Apache HttpClient 5 Confusion

### The Additional Discovery

Looking back at my original `pom.xml`, I had included:

```xml
<dependency>
    <groupId>org.apache.httpcomponents.client5</groupId>
    <artifactId>httpclient5</artifactId>
</dependency>
```

This was actually **making things worse**! Spring Boot's auto-configuration was detecting Apache HttpClient 5 and configuring it as the default HTTP client, which completely broke WebClient's streaming capabilities.

### Why Removing It Didn't Fix Everything

Even after removing `httpclient5`, streaming still failed because:
- I still only had `spring-boot-starter-web` (servlet-based)
- Spring Boot 3.2+ uses JDK HTTP client by default for WebClient in servlet apps
- JDK HTTP client doesn't support SSE streaming properly
- I still needed `reactor-netty-http` explicitly

## Chapter 9: The Final Solution

### The Simple Fix

The solution was incredibly simple - just add this dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>io.projectreactor.netty</groupId>
    <artifactId>reactor-netty-http</artifactId>
</dependency>
```

### Alternative Solutions

1. **Add spring-boot-starter-webflux** (most idiomatic):
   ```xml
   <dependency>
       <groupId>org.springframework.boot</groupId>
       <artifactId>spring-boot-starter-webflux</artifactId>
   </dependency>
   ```

2. **Remove conflicting dependencies**:
   - Remove `httpclient5` if not needed
   - Ensure no other HTTP client dependencies are interfering

## Chapter 10: The Lessons Learned

### Technical Lessons

1. **Dependency management can be tricky** - transitive dependencies don't always work as expected
2. **Spring Boot's auto-configuration** can override expected behavior
3. **WebClient ≠ Reactor Netty** - they're separate concerns
4. **Streaming requires specific HTTP client implementations** that handle SSE properly
5. **Optional dependencies** can cause runtime issues that are hard to debug
6. **Source code exploration** is crucial for understanding complex dependency issues

### Debugging Lessons

1. **Start with the basics** - check dependency trees
2. **Follow the call chain** - trace from high-level API to low-level implementation
3. **Explore source code** - don't just rely on documentation
4. **Test incrementally** - isolate variables to understand what's causing issues
5. **Understand the framework's design decisions** - optional dependencies are intentional

### Documentation Gaps

This experience revealed several documentation gaps:
1. **Spring AI** should explicitly mention the need for `reactor-netty-http` when using streaming
2. **Spring WebFlux** documentation should clarify the optional nature of `reactor-netty-http`
3. **Spring Boot** should better document HTTP client auto-configuration behavior

## Epilogue: The Aftermath

After adding the `reactor-netty-http` dependency, streaming worked perfectly with zero custom configuration needed. No HTTP protocol tweaks, no custom HttpClient setup - just the missing dependency!

The chat application now works flawlessly with:
- Real-time streaming responses
- Smooth HTMX integration
- Local LLM models via LM Studio
- Proper error handling and timeouts

### The Impact

This debugging journey taught me more about Spring Boot dependency management than I ever expected to learn. It also highlighted the importance of understanding the underlying architecture and not just relying on high-level APIs.

The solution was simple, but the journey to get there was complex and educational. This is exactly the kind of deep technical debugging that makes software development both challenging and rewarding.

### The Code That Finally Worked

```java
@RestController
public class ChatController {
    
    @Autowired
    private ChatClient chatClient;
    
    @PostMapping("/chat/stream")
    public Flux<String> streamChat(@RequestBody String message) {
        return chatClient.prompt(message).stream().content();
    }
}
```

With the simple addition of:
```xml
<dependency>
    <groupId>io.projectreactor.netty</groupId>
    <artifactId>reactor-netty-http</artifactId>
</dependency>
```

**The end result**: A beautiful, streaming chat application that works seamlessly with local LLM models. 🎉

---

*This debugging odyssey took me from simple API usage to deep framework internals, from dependency management mysteries to Spring Framework source code exploration. It's a perfect example of how complex software systems can hide simple solutions behind layers of abstraction and configuration.*