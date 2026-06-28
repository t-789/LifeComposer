package org.example.lifecomposer.Controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.example.lifecomposer.Entity.User;
import org.example.lifecomposer.Service.QaService;
import org.example.lifecomposer.config.LlmConfig;
import org.example.lifecomposer.dto.QaRequest;
import org.example.lifecomposer.dto.QaResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/qa")
public class QaController {

    private final QaService qaService;
    private final LlmConfig llmConfig;

    public QaController(QaService qaService, LlmConfig llmConfig) {
        this.qaService = qaService;
        this.llmConfig = llmConfig;
    }

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        LlmConfig.UseCaseConfig qa = llmConfig.resolveOrDefault("qa");
        Map<String, Object> result = new HashMap<>();
        result.put("status", "ok");
        result.put("provider", qa.getProvider());
        result.put("model", qa.getModel());
        result.put("enabled", qa.isEnabled());
        result.put("mocked", !qa.isEnabled());
        result.put("timestamp", Instant.now().toString());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/ask")
    public ResponseEntity<?> ask(@Valid @RequestBody QaRequest request, HttpServletRequest httpRequest) {
        // FIX: null-safe session retrieval to prevent NPE
        HttpSession session = httpRequest.getSession(false);
        User user = session != null ? (User) session.getAttribute("user") : null;
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("用户未登录");
        }

        try {
            QaResponse response = qaService.askQuestion(user.getId().longValue(), request);
            return ResponseEntity.ok(response);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
