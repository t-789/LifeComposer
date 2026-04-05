package org.example.lifecomposer.Controller;

import org.example.lifecomposer.Repository.UserRepository;
import org.example.lifecomposer.dto.FeedbackRequest;
import org.example.lifecomposer.Entity.User;
import org.example.lifecomposer.Entity.UserType;
import org.example.lifecomposer.Service.FeedbackService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    private final FeedbackService feedbackService;
    private final UserRepository userRepository;

    public FeedbackController(FeedbackService feedbackService, UserRepository userRepository) {
        this.feedbackService = feedbackService;
        this.userRepository = userRepository;
    }

    @PostMapping("/submit")
    public ResponseEntity<?> submitFeedback(@RequestBody FeedbackRequest feedbackRequest, Authentication authentication) {
        Integer userId = null;
        String username = "Anonymous";

        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getPrincipal())) {
            User user = userRepository.findByUsername(authentication.getName());
            if (user != null) {
                userId = user.getId();
                username = user.getUsername();
            }
        }

        if (feedbackRequest.getContent() == null || feedbackRequest.getContent().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "反馈内容不能为空"));
        }

        boolean success = feedbackService.saveUserFeedback(
                userId,
                username,
                feedbackRequest.getContent(),
                feedbackRequest.getUrl(),
                feedbackRequest.getUserAgent()
        );

        return success
                ? ResponseEntity.ok(Map.of("message", "反馈提交成功"))
                : ResponseEntity.badRequest().body(Map.of("error", "反馈提交失败"));
    }

    @PostMapping("/system-error")
    public ResponseEntity<?> submitSystemError(@RequestBody FeedbackRequest feedbackRequest, Authentication authentication) {
        Integer userId = null;
        String username = "Anonymous";

        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getPrincipal())) {
            User user = userRepository.findByUsername(authentication.getName());
            if (user != null) {
                userId = user.getId();
                username = user.getUsername();
            }
        }

        boolean success = feedbackService.saveSystemFeedback(
                userId,
                username,
                feedbackRequest.getContent(),
                feedbackRequest.getUrl(),
                feedbackRequest.getUserAgent(),
                feedbackRequest.getStackTrace()
        );

        return success
                ? ResponseEntity.ok(Map.of("message", "系统错误报告已提交"))
                : ResponseEntity.badRequest().body(Map.of("error", "系统错误报告提交失败"));
    }

    @GetMapping("/all")
    public ResponseEntity<?> getAllFeedback(Authentication authentication) {
        if (isNotAdmin(authentication)) {
            return ResponseEntity.status(403).body(Map.of("error", "权限不足"));
        }
        return ResponseEntity.ok(feedbackService.getAllFeedback());
    }

    @GetMapping("/type/{type}")
    public ResponseEntity<?> getFeedbackByType(@PathVariable String type, Authentication authentication) {
        if (isNotAdmin(authentication)) {
            return ResponseEntity.status(403).body(Map.of("error", "权限不足"));
        }
        return ResponseEntity.ok(feedbackService.getFeedbackByType(type));
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<?> resolveFeedback(@PathVariable int id,
                                             @RequestBody(required = false) Map<String, Object> payload,
                                             Authentication authentication) {
        if (isNotAdmin(authentication)) {
            return ResponseEntity.status(403).body(Map.of("error", "权限不足"));
        }

        boolean resolved = payload == null || payload.get("resolved") == null || Boolean.TRUE.equals(payload.get("resolved"));
        boolean success = feedbackService.updateResolvedStatus(id, resolved, authentication.getName());

        return success
                ? ResponseEntity.ok(Map.of("message", "反馈状态更新成功"))
                : ResponseEntity.badRequest().body(Map.of("error", "反馈状态更新失败"));
    }

    private boolean isNotAdmin(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return true;
        }
        User user = userRepository.findByUsername(authentication.getName());
        return user == null || user.getType() == null || user.getType() != UserType.ADMIN;
    }
}
