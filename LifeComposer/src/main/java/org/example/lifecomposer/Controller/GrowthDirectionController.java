package org.example.lifecomposer.Controller;

import org.example.lifecomposer.Entity.User;
import org.example.lifecomposer.Repository.UserRepository;
import org.example.lifecomposer.dto.DirectionRecommendationDto;
import org.example.lifecomposer.dto.PathPlanDto;
import org.example.lifecomposer.recommendation.GrowthDirection;
import org.example.lifecomposer.recommendation.RecommendationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** v0.1 M5: direction catalog, recommendations, capability gap and path plans. */
@RestController
@RequestMapping("/api/growth-directions")
public class GrowthDirectionController {

    private final RecommendationService recommendationService;
    private final UserRepository userRepository;

    public GrowthDirectionController(RecommendationService recommendationService,
                                     UserRepository userRepository) {
        this.recommendationService = recommendationService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<?> listDirections() {
        List<Map<String, Object>> result = recommendationService.catalog().all().stream()
                .map(this::summary)
                .toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/recommendations")
    public ResponseEntity<?> recommendations(Authentication authentication) {
        User user = currentUser(authentication);
        if (user == null) {
            return unauthorized();
        }
        List<DirectionRecommendationDto> result = recommendationService.recommend(user.getId().longValue());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{directionId}/path")
    public ResponseEntity<?> path(@PathVariable String directionId, Authentication authentication) {
        User user = currentUser(authentication);
        if (user == null) {
            return unauthorized();
        }
        PathPlanDto plan = recommendationService.path(user.getId().longValue(), directionId);
        if (plan == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "DIRECTION_NOT_FOUND", "message", "成长方向不存在"));
        }
        return ResponseEntity.ok(plan);
    }

    @GetMapping("/{directionId}/gap")
    public ResponseEntity<?> gap(@PathVariable String directionId, Authentication authentication) {
        User user = currentUser(authentication);
        if (user == null) {
            return unauthorized();
        }
        var gap = recommendationService.gap(user.getId().longValue(), directionId);
        if (gap == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "DIRECTION_NOT_FOUND", "message", "成长方向不存在"));
        }
        return ResponseEntity.ok(gap);
    }

    private Map<String, Object> summary(GrowthDirection direction) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("directionId", direction.getId());
        map.put("name", direction.getName());
        map.put("description", direction.getDescription());
        map.put("targetTags", direction.getTargetTags());
        map.put("entryTags", direction.getEntryTags());
        map.put("requiredHoursPerWeek", direction.getRequiredHoursPerWeek());
        map.put("difficulty", direction.getDifficulty());
        map.put("preparationMonths", direction.getPreparationMonths());
        map.put("resourceIds", direction.getResourceIds());
        return map;
    }

    private ResponseEntity<?> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "未登录"));
    }

    private User currentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }
        return userRepository.findByUsername(authentication.getName());
    }
}
