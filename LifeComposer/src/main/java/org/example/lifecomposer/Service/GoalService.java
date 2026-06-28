package org.example.lifecomposer.Service;

import org.example.lifecomposer.Entity.Goal;
import org.example.lifecomposer.Repository.GoalRepository;
import org.example.lifecomposer.dto.GoalDto;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class GoalService {

    private static final Set<String> VALID_STATUSES = Set.of("ACTIVE", "COMPLETED", "ARCHIVED");

    private final GoalRepository goalRepository;

    public GoalService(GoalRepository goalRepository) {
        this.goalRepository = goalRepository;
    }

    public GoalDto createGoal(Long userId, GoalDto dto) {
        Goal goal = new Goal();
        goal.setUserId(userId);
        goal.setTitle(dto.getTitle());
        goal.setDescription(dto.getDescription());
        goal.setCategory(dto.getCategory());
        goal.setPriority(dto.getPriority());
        String status = dto.getStatus() != null ? dto.getStatus() : "ACTIVE";
        if (!VALID_STATUSES.contains(status)) {
            throw new IllegalStateException("无效的目标状态: " + status + "，有效值为: " + VALID_STATUSES);
        }
        goal.setStatus(status);
        goal.setTargetDate(dto.getTargetDate());
        goal.setProgress(dto.getProgress() != null ? dto.getProgress() : 0);

        Long id = goalRepository.insert(goal);
        // Re-fetch to get database-generated timestamps
        return toDto(goalRepository.findById(id));
    }

    public List<GoalDto> listGoals(Long userId) {
        return goalRepository.findByUserId(userId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public GoalDto getGoal(Long id, Long userId) {
        Goal goal = goalRepository.findById(id);
        if (goal == null) {
            throw new IllegalStateException("目标不存在");
        }
        if (!goal.getUserId().equals(userId)) {
            throw new IllegalStateException("无权访问此目标");
        }
        return toDto(goal);
    }

    public GoalDto updateGoal(Long id, Long userId, GoalDto dto) {
        Goal goal = goalRepository.findById(id);
        if (goal == null) {
            throw new IllegalStateException("目标不存在");
        }
        if (!goal.getUserId().equals(userId)) {
            throw new IllegalStateException("无权修改此目标");
        }

        goal.setTitle(dto.getTitle());
        goal.setDescription(dto.getDescription());
        goal.setCategory(dto.getCategory());
        goal.setPriority(dto.getPriority());
        if (dto.getStatus() != null) {
            if (!VALID_STATUSES.contains(dto.getStatus())) {
                throw new IllegalStateException("无效的目标状态: " + dto.getStatus() + "，有效值为: " + VALID_STATUSES);
            }
            goal.setStatus(dto.getStatus());
        }
        goal.setTargetDate(dtoTargetDateIfSet(dto.getTargetDate(), goal.getTargetDate()));
        if (dto.getProgress() != null) {
            goal.setProgress(dto.getProgress());
        }

        goalRepository.update(goal);

        // Re-fetch to get updated_at
        return toDto(goalRepository.findById(id));
    }

    private String dtoTargetDateIfSet(String dtoVal, String currentVal) {
        return dtoVal != null ? dtoVal : currentVal;
    }

    public void archiveGoal(Long id, Long userId) {
        Goal goal = goalRepository.findById(id);
        if (goal == null) {
            throw new IllegalStateException("目标不存在");
        }
        if (!goal.getUserId().equals(userId)) {
            throw new IllegalStateException("无权删除此目标");
        }
        goalRepository.archive(id);
    }

    private GoalDto toDto(Goal goal) {
        if (goal == null) {
            return null;
        }
        GoalDto dto = new GoalDto();
        dto.setId(goal.getId());
        dto.setTitle(goal.getTitle());
        dto.setDescription(goal.getDescription());
        dto.setCategory(goal.getCategory());
        dto.setPriority(goal.getPriority());
        dto.setStatus(goal.getStatus());
        dto.setTargetDate(goal.getTargetDate());
        dto.setProgress(goal.getProgress());
        dto.setCreatedAt(goal.getCreatedAt());
        dto.setUpdatedAt(goal.getUpdatedAt());
        return dto;
    }
}
