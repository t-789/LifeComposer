package org.example.lifecomposer.Controller;

import jakarta.validation.Valid;
import org.example.lifecomposer.Entity.ChatMessage;
import org.example.lifecomposer.Entity.User;
import org.example.lifecomposer.Repository.UserRepository;
import org.example.lifecomposer.Service.ChatService;
import org.example.lifecomposer.dto.ChatRequest;
import org.example.lifecomposer.dto.ChatResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;
    private final UserRepository userRepository;

    public ChatController(ChatService chatService, UserRepository userRepository) {
        this.chatService = chatService;
        this.userRepository = userRepository;
    }

    @PostMapping("/send")
    public ResponseEntity<?> sendMessage(@Valid @RequestBody ChatRequest request,
                                          Authentication authentication) {
        Integer userId = resolveUserId(authentication);
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }
        ChatResponse response = chatService.sendMessage(userId, request.getMessage());
        return ResponseEntity.ok(response);
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
}
