package org.example.lifecomposer.Controller;

import jakarta.validation.Valid;
import org.example.lifecomposer.Entity.User;
import org.example.lifecomposer.Repository.UserRepository;
import org.example.lifecomposer.Service.CreditActivityService;
import org.example.lifecomposer.dto.CreditActivityDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/credit-activities")
public class CreditActivityController {

    private final CreditActivityService creditActivityService;
    private final UserRepository userRepository;

    public CreditActivityController(CreditActivityService creditActivityService,
                                    UserRepository userRepository) {
        this.creditActivityService = creditActivityService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<?> listActivities(Authentication authentication) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("用户未登录");
        }
        return ResponseEntity.ok(creditActivityService.listActivities(currentUser.getId().longValue()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getActivity(@PathVariable Long id, Authentication authentication) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("用户未登录");
        }
        try {
            return ResponseEntity.ok(creditActivityService.getActivity(id, currentUser.getId().longValue()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping
    public ResponseEntity<?> createActivity(Authentication authentication,
                                            @Valid @RequestBody CreditActivityDto dto) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("用户未登录");
        }
        try {
            return ResponseEntity.ok(creditActivityService.createActivity(currentUser.getId().longValue(), dto));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateActivity(@PathVariable Long id,
                                            Authentication authentication,
                                            @Valid @RequestBody CreditActivityDto dto) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("用户未登录");
        }
        try {
            return ResponseEntity.ok(creditActivityService.updateActivity(id, currentUser.getId().longValue(), dto));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteActivity(@PathVariable Long id, Authentication authentication) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("用户未登录");
        }
        try {
            creditActivityService.deleteActivity(id, currentUser.getId().longValue());
            Map<String, String> result = new HashMap<>();
            result.put("message", "加分记录已删除");
            return ResponseEntity.ok(result);
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
