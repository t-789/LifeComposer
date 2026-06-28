package org.example.lifecomposer.Controller;

import org.example.lifecomposer.Entity.User;
import org.example.lifecomposer.Repository.UserRepository;
import org.example.lifecomposer.Service.PlanningHistoryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/planning/history")
public class PlanningHistoryController {

    private final PlanningHistoryService planningHistoryService;
    private final UserRepository userRepository;

    public PlanningHistoryController(PlanningHistoryService planningHistoryService,
                                     UserRepository userRepository) {
        this.planningHistoryService = planningHistoryService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<?> listRecords(Authentication authentication) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("用户未登录");
        }
        return ResponseEntity.ok(planningHistoryService.listRecords(currentUser.getId().longValue()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getRecord(@PathVariable Long id, Authentication authentication) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("用户未登录");
        }
        try {
            return ResponseEntity.ok(planningHistoryService.getRecord(id, currentUser.getId().longValue()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    private User getCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }
        return userRepository.findByUsername(authentication.getName());
    }
}
