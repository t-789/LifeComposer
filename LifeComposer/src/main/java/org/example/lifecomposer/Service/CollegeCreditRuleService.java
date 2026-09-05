package org.example.lifecomposer.Service;

import org.example.lifecomposer.Entity.CollegeCreditRule;
import org.example.lifecomposer.Repository.CollegeCreditRuleRepository;
import org.example.lifecomposer.dto.CollegeCreditRuleDto;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CollegeCreditRuleService {

    // Enum sets mirror the CHECK constraints defined in SCHEMA.md for college_credit_rules.
    private static final Set<String> VALID_CREDIT_TYPES = Set.of("graduation", "recommendation");
    private static final Set<String> VALID_CATEGORIES = Set.of(
            "competition", "lecture", "course", "project", "paper", "patent", "sports", "arts", "veteran");
    private static final Set<String> VALID_COMP_LEVELS = Set.of(
            "S", "A+", "A", "B+", "B", "national", "provincial", "school");
    private static final Set<String> VALID_AWARD_TIERS = Set.of(
            "first", "second", "third", "special", "participation");

    private final CollegeCreditRuleRepository collegeCreditRuleRepository;

    public CollegeCreditRuleService(CollegeCreditRuleRepository collegeCreditRuleRepository) {
        this.collegeCreditRuleRepository = collegeCreditRuleRepository;
    }

    public List<CollegeCreditRuleDto> listRules(String college, String creditType, String category) {
        validateCreditTypeIfPresent(creditType);
        validateCategoryIfPresent(category);
        return collegeCreditRuleRepository.findAll(college, creditType, category).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public CollegeCreditRuleDto getRule(Long id) {
        CollegeCreditRule rule = collegeCreditRuleRepository.findById(id);
        if (rule == null) {
            throw new IllegalStateException("加分规则不存在");
        }
        return toDto(rule);
    }

    public CollegeCreditRuleDto createRule(CollegeCreditRuleDto dto) {
        validateCreditType(dto.getCreditType());
        validateCategory(dto.getCategory());
        validateCompLevel(dto.getCompLevel());
        validateAwardTier(dto.getAwardTier());
        if (dto.getCredits() == null || dto.getCredits() < 0) {
            throw new IllegalStateException("分值为空或为负数");
        }
        if (dto.getCategoryCap() != null && dto.getCategoryCap() < 0) {
            throw new IllegalStateException("类别学分上限为负数");
        }

        CollegeCreditRule rule = new CollegeCreditRule();
        rule.setCollege(dto.getCollege());
        rule.setCreditType(dto.getCreditType());
        rule.setCategory(dto.getCategory());
        rule.setCompLevel(dto.getCompLevel());
        rule.setCompName(dto.getCompName());
        rule.setAwardTier(dto.getAwardTier());
        rule.setCredits(dto.getCredits());
        rule.setCategoryCap(dto.getCategoryCap());
        rule.setTeamFormula(dto.getTeamFormula());
        rule.setStudentCohort(dto.getStudentCohort());
        rule.setDocSource(dto.getDocSource());
        rule.setLevelsJson(dto.getLevelsJson());
        rule.setNotes(dto.getNotes());

        Long id = collegeCreditRuleRepository.insert(rule);
        // Re-fetch to get the database-generated id/timestamp.
        return toDto(collegeCreditRuleRepository.findById(id));
    }

    private void validateCreditTypeIfPresent(String creditType) {
        if (creditType != null && !creditType.isBlank() && !VALID_CREDIT_TYPES.contains(creditType)) {
            throw new IllegalStateException("无效的加分类型: " + creditType + "，有效值: " + VALID_CREDIT_TYPES);
        }
    }

    private void validateCategoryIfPresent(String category) {
        if (category != null && !category.isBlank() && !VALID_CATEGORIES.contains(category)) {
            throw new IllegalStateException("无效的来源类别: " + category + "，有效值: " + VALID_CATEGORIES);
        }
    }

    private void validateCreditType(String creditType) {
        if (creditType == null || creditType.isBlank()) {
            throw new IllegalStateException("加分类型不能为空");
        }
        validateCreditTypeIfPresent(creditType);
    }

    private void validateCategory(String category) {
        if (category == null || category.isBlank()) {
            throw new IllegalStateException("来源类别不能为空");
        }
        validateCategoryIfPresent(category);
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

    private CollegeCreditRuleDto toDto(CollegeCreditRule rule) {
        if (rule == null) {
            return null;
        }
        CollegeCreditRuleDto dto = new CollegeCreditRuleDto();
        dto.setId(rule.getId());
        dto.setCollege(rule.getCollege());
        dto.setCreditType(rule.getCreditType());
        dto.setCategory(rule.getCategory());
        dto.setCompLevel(rule.getCompLevel());
        dto.setCompName(rule.getCompName());
        dto.setAwardTier(rule.getAwardTier());
        dto.setCredits(rule.getCredits());
        dto.setCategoryCap(rule.getCategoryCap());
        dto.setTeamFormula(rule.getTeamFormula());
        dto.setStudentCohort(rule.getStudentCohort());
        dto.setDocSource(rule.getDocSource());
        dto.setLevelsJson(rule.getLevelsJson());
        dto.setNotes(rule.getNotes());
        dto.setCreatedAt(rule.getCreatedAt() != null ? rule.getCreatedAt().toString() : null);
        return dto;
    }
}
