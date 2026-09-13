package org.example.lifecomposer.Controller;

import jakarta.validation.Valid;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.lifecomposer.Entity.ChatMessage;
import org.example.lifecomposer.Entity.User;
import org.example.lifecomposer.Repository.UserRepository;
import org.example.lifecomposer.Service.ChatService;
import org.example.lifecomposer.agent.AgentEventListener;
import org.example.lifecomposer.agent.LlmUnavailableException;
import org.example.lifecomposer.dto.ChatRequest;
import org.example.lifecomposer.dto.ChatResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger LOG = LogManager.getLogger(ChatController.class);
    private static final long SSE_TIMEOUT_MILLIS = 300_000L;

    private final ChatService chatService;
    private final UserRepository userRepository;
    private final ExecutorService chatStreamExecutor;

    public ChatController(ChatService chatService,
                          UserRepository userRepository,
                          @Qualifier("chatStreamExecutor") ExecutorService chatStreamExecutor) {
        this.chatService = chatService;
        this.userRepository = userRepository;
        this.chatStreamExecutor = chatStreamExecutor;
    }

    @PostMapping("/send")
    public ResponseEntity<?> sendMessage(@Valid @RequestBody ChatRequest request,
                                         Authentication authentication) {
        Integer userId = resolveUserId(authentication);
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }
        try {
            ChatResponse response = chatService.sendMessage(userId, request.getMessage());
            return ResponseEntity.ok(response);
        } catch (LlmUnavailableException e) {
            return ResponseEntity.status(503).body(Map.of(
                    "error", e.getCode(),
                    "message", "LLM 不可用，请稍后重试"));
        }
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMessage(@Valid @RequestBody ChatRequest request,
                                    Authentication authentication) {
        Integer userId = resolveUserId(authentication);
        if (userId == null) {
            SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
            emitter.completeWithError(new IllegalStateException("未登录"));
            return emitter;
        }

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        chatStreamExecutor.execute(() -> {
            SseAgentEventListener listener = new SseAgentEventListener(emitter);
            try {
                chatService.streamMessage(userId, request.getMessage(), listener);
                send(emitter, "done", Map.of());
                emitter.complete();
            } catch (ClientDisconnectedException e) {
                LOG.info("SSE client disconnected during chat stream for user {}", userId);
                emitter.completeWithError(e);
            } catch (LlmUnavailableException e) {
                completeWithErrorEvent(emitter, e.getCode(), "LLM 不可用，请稍后重试");
            } catch (Exception e) {
                LOG.error("Chat stream failed for user {}: {}", userId, e.getMessage(), e);
                completeWithErrorEvent(emitter, "INTERNAL_ERROR", "服务异常，请稍后重试");
            }
        });
        return emitter;
    }

    private void completeWithErrorEvent(SseEmitter emitter, String code, String message) {
        try {
            send(emitter, "error", Map.of("code", code, "message", message));
            send(emitter, "done", Map.of());
            emitter.complete();
        } catch (ClientDisconnectedException e) {
            emitter.completeWithError(e);
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    }

    private void send(SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(data, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            throw new ClientDisconnectedException(e);
        }
    }

    @GetMapping("/history")
    public ResponseEntity<?> getHistory(Authentication authentication) {
        Integer userId = resolveUserId(authentication);
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }
        List<ChatMessage> messages = chatService.getHistory(userId);
        return ResponseEntity.ok(messages);
    }

    @DeleteMapping("/context")
    public ResponseEntity<?> clearContext(Authentication authentication) {
        Integer userId = resolveUserId(authentication);
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }
        int deleted = chatService.clearContext(userId);
        return ResponseEntity.ok(Map.of("deleted", deleted));
    }

    private Integer resolveUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }
        User user = userRepository.findByUsername(authentication.getName());
        return user != null ? user.getId() : null;
    }

    /** Forwards agent lifecycle events as SSE named events. */
    private final class SseAgentEventListener implements AgentEventListener {

        private final SseEmitter emitter;

        private SseAgentEventListener(SseEmitter emitter) {
            this.emitter = emitter;
        }

        @Override
        public void onThinkingStart(Instant startedAt) {
            send(emitter, "thinking_start", Map.of("startedAt", startedAt.toString()));
        }

        @Override
        public void onThinkingTick(long elapsedSeconds) {
            send(emitter, "thinking_tick", Map.of("elapsedSeconds", elapsedSeconds));
        }

        @Override
        public void onThinkingEnd(long elapsedMs) {
            send(emitter, "thinking_end", Map.of("elapsedMs", elapsedMs));
        }

        @Override
        public void onToolCall(String callId, String name, String description, String argumentsJson) {
            send(emitter, "tool_call", Map.of(
                    "callId", callId,
                    "name", name,
                    "description", description,
                    "arguments", argumentsJson));
        }

        @Override
        public void onToolResult(String callId, String name, boolean ok, String resultJson) {
            send(emitter, "tool_result", Map.of(
                    "callId", callId,
                    "name", name,
                    "ok", ok,
                    "result", resultJson));
        }

        @Override
        public void onToken(String delta) {
            send(emitter, "token", Map.of("delta", delta));
        }

        @Override
        public void onAssistantMessage(String content, String createTime) {
            send(emitter, "assistant_message", Map.of(
                    "content", content,
                    "createTime", createTime));
        }

        @Override
        public void onError(String code, String message) {
            send(emitter, "error", Map.of("code", code, "message", message));
        }

        @Override
        public void onDone() {
            send(emitter, "done", Map.of());
        }
    }

    /** Raised when the browser closes the SSE connection mid-stream. */
    private static final class ClientDisconnectedException extends RuntimeException {
        private ClientDisconnectedException(Throwable cause) {
            super("SSE client disconnected", cause);
        }
    }
}
