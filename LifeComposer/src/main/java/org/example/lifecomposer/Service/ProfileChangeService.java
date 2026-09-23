package org.example.lifecomposer.Service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.lifecomposer.Entity.ProfileChangeCandidate;
import org.example.lifecomposer.Entity.UserProfile;
import org.example.lifecomposer.Exception.ProfileApiException;
import org.example.lifecomposer.Repository.ProfileChangeCandidateRepository;
import org.example.lifecomposer.Repository.UserProfileRepository;
import org.example.lifecomposer.config.ProfileChangeProperties;
import org.example.lifecomposer.dto.ProfileChangeCandidateDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Two-phase profile change flow: the chat agent only proposes candidates, and a
 * separate, explicit user decision merges a candidate into the formal profile.
 */
@Service
public class ProfileChangeService {

    private static final Logger LOG = LogManager.getLogger(ProfileChangeService.class);
    private static final DateTimeFormatter DB_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ProfileChangeCandidateRepository candidateRepository;
    private final UserProfileRepository userProfileRepository;
    private final ProfileFieldValidator fieldValidator;
    private final ProfileChangeProperties properties;
    private final Clock clock;
    private final CapabilityStateService capabilityStateService;

    public ProfileChangeService(ProfileChangeCandidateRepository candidateRepository,
                                UserProfileRepository userProfileRepository,
                                ProfileFieldValidator fieldValidator,
                                ProfileChangeProperties properties,
                                Clock clock,
                                CapabilityStateService capabilityStateService) {
        this.candidateRepository = candidateRepository;
        this.userProfileRepository = userProfileRepository;
        this.fieldValidator = fieldValidator;
        this.properties = properties;
        this.clock = clock;
        this.capabilityStateService = capabilityStateService;
    }

    /** A single proposed change coming from the agent tool. */
    public record ProfileChangeProposal(String field, String newValue, String rationale) {
    }

    /** Result of a user decision, including the structured context for the agent. */
    public record DecisionResult(ProfileChangeCandidate candidate, String agentContext, boolean newlyDecided) {
    }

    @Transactional
    public List<ProfileChangeCandidate> propose(Long userId, List<ProfileChangeProposal> proposals, String defaultRationale) {
        if (proposals == null || proposals.isEmpty()) {
            throw new ProfileApiException("NO_PROFILE_CHANGES", "没有可提议的画像变更");
        }
        UserProfile current = userProfileRepository.findByUserId(userId);
        long baseVersion = current == null || current.getVersion() == null ? 0L : current.getVersion();
        String expiresAt = LocalDateTime.now(clock).plusMinutes(properties.getTtlMinutes()).format(DB_TIME);

        List<ProfileChangeCandidate> created = new ArrayList<>();
        for (ProfileChangeProposal proposal : proposals) {
            String field = proposal.field() == null ? "" : proposal.field().trim();
            if (!properties.getAllowedFields().contains(field)) {
                throw new ProfileApiException("FIELD_NOT_ALLOWED", "聊天不能修改该画像字段: " + field);
            }
            String normalized = fieldValidator.validateAndNormalize(field, proposal.newValue(), false);
            if (normalized == null) {
                throw new ProfileApiException("INVALID_FIELD_VALUE", field + " 的新值不能为空");
            }
            String oldValue = readField(current, field);
            if (Objects.equals(oldValue, normalized)) {
                continue;
            }
            ProfileChangeCandidate candidate = new ProfileChangeCandidate();
            candidate.setCandidateId("chg_" + UUID.randomUUID().toString().replace("-", ""));
            candidate.setUserId(userId);
            candidate.setFieldName(field);
            candidate.setOldValue(oldValue);
            candidate.setNewValue(normalized);
            candidate.setRationale(proposal.rationale() == null || proposal.rationale().isBlank()
                    ? defaultRationale : proposal.rationale());
            candidate.setSource("chat");
            candidate.setStatus(ProfileChangeCandidate.PENDING);
            candidate.setBaseVersion(baseVersion);
            candidate.setExpiresAt(expiresAt);
            candidateRepository.insert(candidate);
            created.add(candidate);
        }
        if (created.isEmpty()) {
            throw new ProfileApiException("NO_PROFILE_CHANGES", "提议内容与当前画像一致，无需确认");
        }
        return created;
    }

    public List<ProfileChangeCandidateDto> listPending(Long userId) {
        candidateRepository.expireStale(now());
        return candidateRepository.findPendingByUserId(userId).stream().map(this::toDto).toList();
    }

    public ProfileChangeCandidateDto getOwned(Long userId, String candidateId) {
        ProfileChangeCandidate candidate = requireOwned(userId, candidateId);
        return toDto(candidate);
    }

    @Transactional
    public DecisionResult decide(Long userId, String candidateId, String rawDecision, String reason) {
        candidateRepository.expireStale(now());
        ProfileChangeCandidate candidate = requireOwned(userId, candidateId);
        String decision = normalizeDecision(rawDecision);

        if (!ProfileChangeCandidate.PENDING.equals(candidate.getStatus())) {
            // Repeated / already-decided requests are idempotent and must not
            // trigger another agent continuation (and therefore no AI quota).
            return new DecisionResult(candidate, contextFor(candidate), false);
        }

        if (LocalDateTime.now(clock).format(DB_TIME).compareTo(candidate.getExpiresAt()) >= 0) {
            candidateRepository.markExpired(candidateId, userId, now());
            candidate.setStatus(ProfileChangeCandidate.EXPIRED);
            candidate.setDecidedAt(now());
            return new DecisionResult(candidate, contextFor(candidate), false);
        }

        if ("REJECT".equals(decision)) {
            String safeReason = reason == null ? null : reason.trim();
            boolean marked = candidateRepository.markRejected(candidateId, userId, safeReason, now());
            if (!marked) {
                // A concurrent request already decided this candidate: return
                // the persisted state and skip the agent continuation.
                ProfileChangeCandidate reloaded = candidateRepository.findByCandidateIdAndUserId(candidateId, userId);
                return new DecisionResult(reloaded == null ? candidate : reloaded,
                        contextFor(reloaded == null ? candidate : reloaded), false);
            }
            candidate.setStatus(ProfileChangeCandidate.REJECTED);
            candidate.setDecisionReason(safeReason);
            candidate.setDecidedAt(now());
            return new DecisionResult(candidate, contextFor(candidate), true);
        }

        return confirm(userId, candidate);
    }

    private DecisionResult confirm(Long userId, ProfileChangeCandidate candidate) {
        String decidedAt = now();

        // Step 1: atomically claim the PENDING candidate. This is the only
        // request allowed to touch the profile; a concurrent REJECT / EXPIRED /
        // CONFLICT makes this UPDATE miss and the profile stays untouched.
        if (!candidateRepository.claimForConfirmation(candidate.getCandidateId(), userId, decidedAt)) {
            ProfileChangeCandidate reloaded = candidateRepository.findByCandidateIdAndUserId(
                    candidate.getCandidateId(), userId);
            return new DecisionResult(reloaded == null ? candidate : reloaded,
                    contextFor(reloaded == null ? candidate : reloaded), false);
        }

        // Step 2 (same transaction): the claim is valid, now write the profile.
        UserProfile current = userProfileRepository.findByUserId(userId);
        long currentVersion = current == null || current.getVersion() == null ? 0L : current.getVersion();
        String currentFieldValue = readField(current, candidate.getFieldName());
        String mergedValue = fieldValidator.mergeConfirmedValue(
                candidate.getFieldName(), currentFieldValue, candidate.getNewValue());

        // Already applied (duplicate candidate, or stale card completed after a
        // continuation applied the same value): finish it without another write.
        if (current != null && Objects.equals(mergedValue, currentFieldValue)) {
            return markAlreadyApplied(userId, candidate, currentVersion, decidedAt);
        }

        // Collection fields are union-merged, so they are safe on a newer
        // profile. Scalar fields require the candidate snapshot to still match
        // the stored value; otherwise another request changed that field.
        boolean snapshotUnchanged = Objects.equals(currentVersion, candidate.getBaseVersion())
                || Objects.equals(currentFieldValue, candidate.getOldValue());
        if (current == null) {
            if (!Objects.equals(candidate.getBaseVersion(), 0L)) {
                return markConflictFromClaim(userId, candidate, decidedAt);
            }
        } else if (!fieldValidator.isUnionMergeField(candidate.getFieldName()) && !snapshotUnchanged) {
            return markConflictFromClaim(userId, candidate, decidedAt);
        }

        UserProfile target = current == null ? new UserProfile() : current;
        target.setUserId(userId);
        applyField(target, candidate.getFieldName(), mergedValue);

        boolean saved;
        if (current == null) {
            saved = userProfileRepository.insertIfAbsent(target);
        } else {
            saved = userProfileRepository.updateWithVersion(target, currentVersion);
        }
        if (!saved) {
            return markConflictFromClaim(userId, candidate, decidedAt);
        }

        UserProfile latest = userProfileRepository.findByUserId(userId);
        long mergedVersion = latest == null || latest.getVersion() == null ? currentVersion + 1 : latest.getVersion();
        if (!candidateRepository.updateMergedVersion(candidate.getCandidateId(), userId, mergedVersion)) {
            // The claimed row disappeared mid-transaction; roll the profile
            // write back rather than leave an unconfirmed mutation behind.
            throw new IllegalStateException("画像候选确认状态与画像写入不一致");
        }

        if ("skillsJson".equals(candidate.getFieldName()) && latest != null) {
            capabilityStateService.recordSkills(userId, latest.getSkillsJson(),
                    latest.getExperiencesJson(), "CHAT_CONFIRMED");
        }
        candidate.setStatus(ProfileChangeCandidate.CONFIRMED);
        candidate.setMergedVersion(mergedVersion);
        candidate.setDecidedAt(decidedAt);
        LOG.info("Profile change confirmed: user={} field={} version={}",
                userId, candidate.getFieldName(), mergedVersion);
        return new DecisionResult(candidate, contextFor(candidate), true);
    }

    /** Candidate value is already present in the current profile (idempotent confirmation). */
    private DecisionResult markAlreadyApplied(Long userId, ProfileChangeCandidate candidate,
                                              long currentVersion, String decidedAt) {
        if (!candidateRepository.updateMergedVersion(candidate.getCandidateId(), userId, currentVersion)) {
            throw new IllegalStateException("画像候选确认状态与画像写入不一致");
        }
        if ("skillsJson".equals(candidate.getFieldName())) {
            UserProfile latest = userProfileRepository.findByUserId(userId);
            if (latest != null) {
                capabilityStateService.recordSkills(userId, latest.getSkillsJson(),
                        latest.getExperiencesJson(), "CHAT_CONFIRMED");
            }
        }
        candidate.setStatus(ProfileChangeCandidate.CONFIRMED);
        candidate.setMergedVersion(currentVersion);
        candidate.setDecidedAt(decidedAt);
        LOG.info("Profile change already applied: user={} field={} version={}",
                userId, candidate.getFieldName(), currentVersion);
        return new DecisionResult(candidate, contextFor(candidate), true);
    }

    private DecisionResult markConflictFromClaim(Long userId, ProfileChangeCandidate candidate, String decidedAt) {
        candidateRepository.markConflictFromClaimed(candidate.getCandidateId(), userId, decidedAt);
        candidate.setStatus(ProfileChangeCandidate.CONFLICT);
        candidate.setDecidedAt(decidedAt);
        return new DecisionResult(candidate, contextFor(candidate), false);
    }

    private ProfileChangeCandidate requireOwned(Long userId, String candidateId) {
        ProfileChangeCandidate candidate = candidateRepository.findByCandidateIdAndUserId(candidateId, userId);
        if (candidate == null) {
            throw new ProfileApiException("CANDIDATE_NOT_FOUND", "画像变更候选不存在");
        }
        return candidate;
    }

    private String normalizeDecision(String rawDecision) {
        String value = rawDecision == null ? "" : rawDecision.trim().toUpperCase();
        if (value.equals("CONFIRM") || value.equals("CONFIRMED") || value.equals("ACCEPT") || value.equals("确认")) {
            return "CONFIRM";
        }
        if (value.equals("REJECT") || value.equals("REJECTED") || value.equals("DENY") || value.equals("拒绝")) {
            return "REJECT";
        }
        throw new ProfileApiException("INVALID_DECISION", "decision 必须是 CONFIRM 或 REJECT");
    }

    private String contextFor(ProfileChangeCandidate candidate) {
        String field = candidate.getFieldName();
        String oldValue = candidate.getOldValue() == null ? "（空）" : candidate.getOldValue();
        String newValue = candidate.getNewValue();
        return switch (candidate.getStatus()) {
            case ProfileChangeCandidate.CONFIRMED -> "用户已确认画像变更：" + field + " 从 " + oldValue
                    + " 更新为 " + newValue + "，已写入正式画像（版本 v" + candidate.getMergedVersion()
                    + "）。请基于更新后的画像继续回答；需要时重新读取画像。";
            case ProfileChangeCandidate.REJECTED -> "用户拒绝画像变更：" + field + " 原拟更新为 " + newValue
                    + "，拒绝理由：" + (candidate.getDecisionReason() == null || candidate.getDecisionReason().isBlank()
                    ? "未填写" : candidate.getDecisionReason())
                    + "。该信息没有写入正式画像，不得当作事实使用；可以解释原因或询问补充信息。";
            case ProfileChangeCandidate.EXPIRED -> "画像变更候选已过期：" + field + " 的待确认变更未在有效期内处理，未写入画像。";
            case ProfileChangeCandidate.CONFLICT -> "画像变更发生冲突：" + field + " 的候选基于旧版本画像（v"
                    + candidate.getBaseVersion() + "），未写入正式画像。请提示用户刷新画像后重新提议。";
            default -> "画像变更候选状态：" + candidate.getStatus() + "（" + field + "）。";
        };
    }

    private String readField(UserProfile profile, String field) {
        if (profile == null) {
            return null;
        }
        return switch (field) {
            case "availableTime" -> profile.getAvailableTime();
            case "skillsJson" -> profile.getSkillsJson();
            case "interestsJson" -> profile.getInterestsJson();
            case "experiencesJson" -> profile.getExperiencesJson();
            case "goals" -> profile.getGoals();
            case "preferencesJson" -> profile.getPreferencesJson();
            case "college" -> profile.getCollege();
            case "major" -> profile.getMajor();
            case "grade" -> profile.getGrade();
            case "studentId" -> profile.getStudentId();
            default -> null;
        };
    }

    private void applyField(UserProfile profile, String field, String value) {
        switch (field) {
            case "availableTime" -> profile.setAvailableTime(value);
            case "skillsJson" -> profile.setSkillsJson(value);
            case "interestsJson" -> profile.setInterestsJson(value);
            case "experiencesJson" -> profile.setExperiencesJson(value);
            case "goals" -> profile.setGoals(value);
            case "preferencesJson" -> profile.setPreferencesJson(value);
            case "college" -> profile.setCollege(value);
            case "major" -> profile.setMajor(value);
            case "grade" -> profile.setGrade(value);
            case "studentId" -> profile.setStudentId(value);
            default -> throw new ProfileApiException("FIELD_NOT_ALLOWED", "未知画像字段: " + field);
        }
    }

    private ProfileChangeCandidateDto toDto(ProfileChangeCandidate candidate) {
        ProfileChangeCandidateDto dto = new ProfileChangeCandidateDto();
        dto.setCandidateId(candidate.getCandidateId());
        dto.setField(candidate.getFieldName());
        dto.setOldValue(candidate.getOldValue());
        dto.setNewValue(candidate.getNewValue());
        dto.setRationale(candidate.getRationale());
        dto.setStatus(candidate.getStatus());
        dto.setSource(candidate.getSource());
        dto.setCreatedAt(candidate.getCreatedAt());
        dto.setExpiresAt(candidate.getExpiresAt());
        dto.setDecidedAt(candidate.getDecidedAt());
        dto.setDecisionReason(candidate.getDecisionReason());
        dto.setMergedVersion(candidate.getMergedVersion());
        return dto;
    }

    private String now() {
        return LocalDateTime.now(clock).format(DB_TIME);
    }

    /** Map form used by the agent tool result. */
    public Map<String, Object> toToolPayload(List<ProfileChangeCandidate> candidates) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", ProfileChangeCandidate.PENDING);
        payload.put("awaitingConfirmation", true);
        payload.put("created", candidates.size());
        payload.put("message", "已生成 " + candidates.size() + " 条画像变更候选，等待用户确认；不要声称已经写入画像。");
        payload.put("candidates", candidates.stream().map(this::toDto).toList());
        return payload;
    }
}
