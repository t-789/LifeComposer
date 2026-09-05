package org.example.lifecomposer.Service;

import org.example.lifecomposer.Entity.CollegeCreditRule;
import org.example.lifecomposer.Entity.CreditActivity;
import org.example.lifecomposer.Repository.CollegeCreditRuleRepository;
import org.example.lifecomposer.Repository.CreditActivityRepository;
import org.example.lifecomposer.dto.CreditActivityDto;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CreditActivityService {

    // Enum sets keep the same credit_type CHECK semantics as the DB constraint and
    // the same category/comp_level/award_tier domains as college_credit_rules, so that
    // user records stay consistent with the rules they will be matched against.
    private static final Set<String> VALID_CREDIT_TYPES = Set.of("graduation", "recommendation");
    private static final Set<String> VALID_CATEGORIES = Set.of(
            "competition", "lecture", "course", "project", "paper", "patent", "sports", "arts", "veteran");
    private static final Set<String> VALID_COMP_LEVELS = Set.of(
            "S", "A+", "A", "B+", "B", "national", "provincial", "school");
    private static final Set<String> VALID_AWARD_TIERS = Set.of(
            "first", "second", "third", "special", "participation");

    private final CreditActivityRepository creditActivityRepository;
    private final CollegeCreditRuleRepository collegeCreditRuleRepository;

    public CreditActivityService(CreditActivityRepository creditActivityRepository,
                                 CollegeCreditRuleRepository collegeCreditRuleRepository) {
        this.creditActivityRepository = creditActivityRepository;
        this.collegeCreditRuleRepository = collegeCreditRuleRepository;
    }

    public List<CreditActivityDto> listActivities(Long userId) {
        return creditActivityRepository.findByUserId(userId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public CreditActivityDto getActivity(Long id, Long userId) {
        CreditActivity activity = requireOwnedActivity(id, userId, "无权访问此加分记录");
        return toDto(activity);
    }

    public CreditActivityDto createActivity(Long userId, CreditActivityDto dto) {
        validateCreditType(dto.getCreditType());
        validateCategory(dto.getCategory());
        validateCompLevel(dto.getCompLevel());
        validateAwardTier(dto.getAwardTier());
        if (dto.getCredits() == null || dto.getCredits() < 0) {
            throw new IllegalStateException("分值为空或为负数");
        }
        validateRuleId(dto.getRuleId());

        CreditActivity activity = new CreditActivity();
        activity.setUserId(userId);
        applyDto(activity, dto);
        // 审核状态由服务端控制：新建记录一律为未审核。
        activity.setVerified(0);

        Long id = creditActivityRepository.insert(activity);
        // Re-fetch to get the database-generated id/timestamp.
        return toDto(creditActivityRepository.findById(id));
    }

    public CreditActivityDto updateActivity(Long id, Long userId, CreditActivityDto dto) {
        CreditActivity activity = requireOwnedActivity(id, userId, "无权修改此加分记录");

        validateCreditType(dto.getCreditType());
        validateCategory(dto.getCategory());
        validateCompLevel(dto.getCompLevel());
        validateAwardTier(dto.getAwardTier());
        if (dto.getCredits() == null || dto.getCredits() < 0) {
            throw new IllegalStateException("分值为空或为负数");
        }
        validateRuleId(dto.getRuleId());

        applyDto(activity, dto);
        // 审核状态不由用户通过 PUT 修改。
        activity.setVerified(activity.getVerified() != null ? activity.getVerified() : 0);

        creditActivityRepository.update(activity);
        return toDto(creditActivityRepository.findById(id));
    }

    public void deleteActivity(Long id, Long userId) {
        CreditActivity activity = requireOwnedActivity(id, userId, "无权删除此加分记录");
        boolean deleted = creditActivityRepository.deleteByIdAndUserId(activity.getId(), userId);
        if (!deleted) {
            throw new IllegalStateException("删除加分记录失败");
        }
    }

    private CreditActivity requireOwnedActivity(Long id, Long userId, String forbiddenMessage) {
        CreditActivity activity = creditActivityRepository.findById(id);
        if (activity == null) {
            throw new IllegalStateException("加分记录不存在");
        }
        if (!activity.getUserId().equals(userId)) {
            throw new IllegalStateException(forbiddenMessage);
        }
        return activity;
    }

    private void applyDto(CreditActivity activity, CreditActivityDto dto) {
        activity.setRuleId(dto.getRuleId());
        activity.setCreditType(dto.getCreditType());
        activity.setCategory(dto.getCategory());
        activity.setCompName(dto.getCompName());
        activity.setCompLevel(dto.getCompLevel());
        activity.setAwardTier(dto.getAwardTier());
        activity.setCredits(dto.getCredits());
        activity.setObtainedDate(dto.getObtainedDate());
        activity.setCertificateRef(dto.getCertificateRef());
        activity.setNotes(dto.getNotes());
    }

    private void validateCreditType(String creditType) {
        if (creditType == null || creditType.isBlank()) {
            throw new IllegalStateException("加分类型不能为空");
        }
        if (!VALID_CREDIT_TYPES.contains(creditType)) {
            throw new IllegalStateException("无效的加分类型: " + creditType + "，有效值: " + VALID_CREDIT_TYPES);
        }
    }

    private void validateCategory(String category) {
        if (category == null || category.isBlank()) {
            throw new IllegalStateException("来源类别不能为空");
        }
        if (!VALID_CATEGORIES.contains(category)) {
            throw new IllegalStateException("无效的来源类别: " + category + "，有效值: " + VALID_CATEGORIES);
        }
    }

    private void validateCompLevel(String compLevel) {
        if (compLevel == null || compLevel.isBlank()) {
            return;
        }
        if (!VALID_COMP_LEVELS.contains(compLevel)) {
            throw new IllegalStateException("无效的竞赛级别: " + compLevel + "，有效值: " + VALID_COMP_LEVELS);
        }
    }

    private void validateAwardTier(String awardTier) {
        if (awardTier == null || awardTier.isBlank()) {
            return;
        }
        if (!VALID_AWARD_TIERS.contains(awardTier)) {
            throw new IllegalStateException("无效的奖项等级: " + awardTier + "，有效值: " + VALID_AWARD_TIERS);
        }
    }

    private void validateRuleId(Long ruleId) {
        if (ruleId == null) {
            return;
        }
        CollegeCreditRule rule = collegeCreditRuleRepository.findById(ruleId);
        if (rule == null) {
            throw new IllegalStateException("关联的加分规则不存在");
        }
    }

    private CreditActivityDto toDto(CreditActivity activity) {
        if (activity == null) {
            return null;
        }
        CreditActivityDto dto = new CreditActivityDto();
        dto.setId(activity.getId());
        dto.setRuleId(activity.getRuleId());
        dto.setCreditType(activity.getCreditType());
        dto.setCategory(activity.getCategory());
        dto.setCompName(activity.getCompName());
        dto.setCompLevel(activity.getCompLevel());
        dto.setAwardTier(activity.getAwardTier());
        dto.setCredits(activity.getCredits());
        dto.setObtainedDate(activity.getObtainedDate());
        dto.setCertificateRef(activity.getCertificateRef());
        dto.setVerified(activity.getVerified());
        dto.setNotes(activity.getNotes());
        dto.setCreatedAt(activity.getCreatedAt() != null ? activity.getCreatedAt().toString() : null);
        return dto;
    }
}
