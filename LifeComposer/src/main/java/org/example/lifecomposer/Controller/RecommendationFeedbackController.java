package org.example.lifecomposer.Controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.example.lifecomposer.Entity.User;
import org.example.lifecomposer.Repository.UserRepository;
import org.example.lifecomposer.Service.RecommendationFeedbackService;
import org.example.lifecomposer.dto.DirectionRecommendationDto;
import org.example.lifecomposer.dto.RecommendationFeedbackDto;
import org.example.lifecomposer.dto.RecommendationFeedbackRequest;
import org.example.lifecomposer.recommendation.RecommendationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** v0.1 M6: structured recommendation feedback endpoints. */
@RestController
@RequestMapping("/api/recommendation-feedback")
public class RecommendationFeedbackController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RecommendationFeedbackService feedbackService;
    private final RecommendationService recommendationService;
    private final UserRepository userRepository;

    public RecommendationFeedbackController(RecommendationFeedbackService feedbackService,
                                            RecommendationService recommendationService,
                                            UserRepository userRepository) {
        this.feedbackService = feedbackService;
        this.recommendationService = recommendationService;
        this.userRepository = userRepository;
    }

    @PostMapping
    public ResponseEntity<?> submit(@Valid @RequestBody RecommendationFeedbackRequest request,
                                    Authentication authentication) {
        User user = currentUser(authentication);
        if (user == null) {
            return unauthorized();
        }
        Long userId = user.getId().longValue();
        DirectionRecommendationDto recommendation =
                recommendationService.recommendationFor(userId, request.getDirectionId());
        String scoringVersion = recommendation == null ? null : recommendation.getScoringVersion();
        String snapshotJson = snapshot(recommendation);
        RecommendationFeedbackDto dto = feedbackService.record(userId,
                request.getDirectionId(), request.getFeedbackType(), request.getNote(), scoringVersion, snapshotJson);
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/me")
    public ResponseEntity<?> myFeedback(Authentication authentication) {
        User user = currentUser(authentication);
        if (user == null) {
            return unauthorized();
        }
        List<RecommendationFeedbackDto> list = feedbackService.listFor(user.getId().longValue());
        return ResponseEntity.ok(list);
    }

    private String snapshot(DirectionRecommendationDto recommendation) {
        if (recommendation == null) {
            return null;
        }
        try {
            java.util.Map<String, Object> snapshot = new java.util.LinkedHashMap<>();
            snapshot.put("directionId", recommendation.getDirectionId());
            snapshot.put("score", recommendation.getScore());
            snapshot.put("classification", recommendation.getClassification());
            snapshot.put("matchedTags", recommendation.getMatchedTags());
            snapshot.put("missingTags", recommendation.getMissingTags());
            snapshot.put("scoringVersion", recommendation.getScoringVersion());
            return MAPPER.writeValueAsString(snapshot);
        } catch (Exception e) {
            return null;
        }
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
