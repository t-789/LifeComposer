package org.example.lifecomposer.Controller;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.lifecomposer.Entity.User;
import org.example.lifecomposer.Repository.UserRepository;
import org.example.lifecomposer.Service.ChatQuotaDecision;
import org.example.lifecomposer.Service.ChatQuotaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/** Admin-only reset of the current Asia/Shanghai chat quota. */
@RestController
@RequestMapping("/api/users/admin/chat-quota")
public class AdminChatQuotaController {

    private static final Logger LOG = LogManager.getLogger(AdminChatQuotaController.class);

    private final ChatQuotaService chatQuotaService;
    private final UserRepository userRepository;

    public AdminChatQuotaController(ChatQuotaService chatQuotaService,
                                    UserRepository userRepository) {
        this.chatQuotaService = chatQuotaService;
        this.userRepository = userRepository;
    }

    @PostMapping("/reset/{userId}")
    public ResponseEntity<?> resetToday(@PathVariable int userId, Authentication authentication) {
        if (isNotAdmin(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "权限不足"));
        }
        User target = userRepository.findById(userId);
        if (target == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "用户不存在"));
        }

        // No date parameter by design: only the current natural day is resettable.
        String usageDate = chatQuotaService.today();
        ChatQuotaDecision decision = chatQuotaService.resetToday(userId);
        LOG.info("AUDIT event=admin_reset_chat_quota admin={} targetUserId={} usageDate={} "
                        + "usedToday={} remainingToday={} dailyLimit={}",
                authentication.getName(), userId, usageDate,
                decision.usedToday(), decision.remainingToday(), decision.dailyLimit());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", userId);
        body.put("usageDate", usageDate);
        body.put("usedToday", decision.usedToday());
        body.put("remainingToday", decision.remainingToday());
        body.put("dailyLimit", decision.dailyLimit());
        body.put("minuteLimit", decision.minuteLimit());
        body.put("minuteRemaining", decision.minuteRemaining());
        return ResponseEntity.ok(body);
    }

    private boolean isNotAdmin(Authentication authentication) {
        return authentication == null
                || authentication.getAuthorities() == null
                || authentication.getAuthorities().stream()
                .noneMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}
