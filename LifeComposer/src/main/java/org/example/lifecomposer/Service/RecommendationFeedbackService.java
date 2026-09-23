package org.example.lifecomposer.Service;

import org.example.lifecomposer.Entity.RecommendationFeedback;
import org.example.lifecomposer.Exception.ProfileApiException;
import org.example.lifecomposer.Repository.RecommendationFeedbackRepository;
import org.example.lifecomposer.dto.RecommendationFeedbackDto;
import org.example.lifecomposer.recommendation.GrowthDirectionCatalog;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/** Collects auditable recommendation feedback; no online learning in v0.1. */
@Service
public class RecommendationFeedbackService {

    public static final Set<String> ALLOWED_TYPES = Set.of(
            "useful", "irrelevant", "too_hard", "time_mismatch", "goal_changed");

    private final RecommendationFeedbackRepository repository;
    private final GrowthDirectionCatalog directionCatalog;

    public RecommendationFeedbackService(RecommendationFeedbackRepository repository,
                                         GrowthDirectionCatalog directionCatalog) {
        this.repository = repository;
        this.directionCatalog = directionCatalog;
    }

    public RecommendationFeedbackDto record(Long userId, String directionId, String feedbackType,
                                            String note, String scoringVersion, String snapshotJson) {
        if (directionId == null || directionId.isBlank()) {
            throw new ProfileApiException("INVALID_FEEDBACK", "directionId 不能为空");
        }
        if (directionCatalog.byId(directionId) == null) {
            throw new ProfileApiException("DIRECTION_NOT_FOUND", "成长方向不存在: " + directionId);
        }
        String type = feedbackType == null ? "" : feedbackType.trim().toLowerCase();
        if (!ALLOWED_TYPES.contains(type)) {
            throw new ProfileApiException("INVALID_FEEDBACK",
                    "feedbackType 必须是 " + ALLOWED_TYPES);
        }
        RecommendationFeedback feedback = new RecommendationFeedback();
        feedback.setUserId(userId);
        feedback.setDirectionId(directionId);
        feedback.setFeedbackType(type);
        feedback.setNote(note);
        feedback.setScoringVersion(scoringVersion);
        feedback.setRecommendationSnapshotJson(snapshotJson);
        repository.insert(feedback);
        return toDto(feedback);
    }

    public List<RecommendationFeedbackDto> listFor(Long userId) {
        return repository.findByUserId(userId).stream().map(this::toDto).toList();
    }

    private RecommendationFeedbackDto toDto(RecommendationFeedback feedback) {
        RecommendationFeedbackDto dto = new RecommendationFeedbackDto();
        dto.setId(feedback.getId());
        dto.setDirectionId(feedback.getDirectionId());
        dto.setFeedbackType(feedback.getFeedbackType());
        dto.setNote(feedback.getNote());
        dto.setScoringVersion(feedback.getScoringVersion());
        dto.setCreatedAt(feedback.getCreatedAt());
        return dto;
    }
}
