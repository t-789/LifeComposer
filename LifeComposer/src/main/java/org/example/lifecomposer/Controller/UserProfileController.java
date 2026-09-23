package org.example.lifecomposer.Controller;

import jakarta.validation.Valid;
import org.example.lifecomposer.Entity.User;
import org.example.lifecomposer.Repository.UserRepository;
import org.example.lifecomposer.Service.CapabilityStateService;
import org.example.lifecomposer.Service.UserProfileService;
import org.example.lifecomposer.dto.UserProfileDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/profiles")
public class UserProfileController {

    private final UserProfileService userProfileService;
    private final CapabilityStateService capabilityStateService;
    private final UserRepository userRepository;

    public UserProfileController(UserProfileService userProfileService,
                                 CapabilityStateService capabilityStateService,
                                 UserRepository userRepository) {
        this.userProfileService = userProfileService;
        this.capabilityStateService = capabilityStateService;
        this.userRepository = userRepository;
    }

    @GetMapping("/me")
    public ResponseEntity<?> getMyProfile(Authentication authentication) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("用户未登录");
        }

        UserProfileDto profile = userProfileService.getProfile(currentUser.getId().longValue());
        if (profile == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("用户档案不存在");
        }

        return ResponseEntity.ok(profile);
    }

    /** v0.1 M4: standard capability tags with level/evidence/source for the current user. */
    @GetMapping("/me/capabilities")
    public ResponseEntity<?> getMyCapabilities(Authentication authentication) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("用户未登录");
        }
        return ResponseEntity.ok(capabilityStateService.statesFor(currentUser.getId().longValue()));
    }

    @PutMapping("/me")
    public ResponseEntity<?> upsertMyProfile(Authentication authentication,
                                              @Valid @RequestBody UserProfileDto dto) {
        User currentUser = getCurrentUser(authentication);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("用户未登录");
        }

        UserProfileDto saved = userProfileService.saveOrUpdate(currentUser.getId().longValue(), dto);
        return ResponseEntity.ok(saved);
    }

    private User getCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }
        return userRepository.findByUsername(authentication.getName());
    }
}
