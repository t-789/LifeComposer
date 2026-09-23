package org.example.lifecomposer.Controller;

import jakarta.validation.Valid;
import org.example.lifecomposer.Entity.User;
import org.example.lifecomposer.Exception.ProfileApiException;
import org.example.lifecomposer.Repository.UserRepository;
import org.example.lifecomposer.Service.ChatQuotaDecision;
import org.example.lifecomposer.Service.ChatQuotaService;
import org.example.lifecomposer.Service.ChatService;
import org.example.lifecomposer.Service.ProfileChangeService;
import org.example.lifecomposer.agent.LlmUnavailableException;
import org.example.lifecomposer.dto.ChatResponse;
import org.example.lifecomposer.dto.ProfileChangeCandidateDto;
import org.example.lifecomposer.dto.ProfileChangeDecisionDto;
import org.example.lifecomposer.dto.ProfileChangeDecisionRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** v0.1 M2: independent confirmation endpoint for chat-proposed profile changes. */
@RestController
@RequestMapping("/api/profiles/change-candidates")
public class ProfileChangeController {

    private final ProfileChangeService profileChangeService;
    private final ChatService chatService;
    private final ChatQuotaService chatQuotaService;
    private final UserRepository userRepository;

    public ProfileChangeController(ProfileChangeService profileChangeService,
                                   ChatService chatService,
                                   ChatQuotaService chatQuotaService,
                                   UserRepository userRepository) {
        this.profileChangeService = profileChangeService;
        this.chatService = chatService;
        this.chatQuotaService = chatQuotaService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<?> listCandidates(Authentication authentication) {
        User user = currentUser(authentication);
        if (user == null) {
            return unauthorized();
        }
        List<ProfileChangeCandidateDto> candidates = profileChangeService.listPending(user.getId().longValue());
        return ResponseEntity.ok(candidates);
    }

    @GetMapping("/{candidateId}")
    public ResponseEntity<?> getCandidate(@PathVariable String candidateId, Authentication authentication) {
        User user = currentUser(authentication);
        if (user == null) {
            return unauthorized();
        }
        return ResponseEntity.ok(profileChangeService.getOwned(user.getId().longValue(), candidateId));
    }

    @PostMapping("/{candidateId}/decision")
    public ResponseEntity<?> decide(@PathVariable String candidateId,
                                    @Valid @RequestBody ProfileChangeDecisionRequest request,
                                    Authentication authentication) {
        User user = currentUser(authentication);
        if (user == null) {
            return unauthorized();
        }
        Integer userId = user.getId();
        ProfileChangeService.DecisionResult result =
                profileChangeService.decide(userId.longValue(), candidateId, request.getDecision(), request.getReason());

        ProfileChangeDecisionDto response = new ProfileChangeDecisionDto();
        response.setCandidateId(result.candidate().getCandidateId());
        response.setStatus(result.candidate().getStatus());
        response.setField(result.candidate().getFieldName());
        response.setOldValue(result.candidate().getOldValue());
        response.setNewValue(result.candidate().getNewValue());
        response.setMergedVersion(result.candidate().getMergedVersion());
        response.setDecidedAt(result.candidate().getDecidedAt());

        boolean agentAnswered = false;
        boolean quotaExceeded = false;
        long retryAfterSeconds = 0L;
        String agentMessage;

        if (result.newlyDecided()) {
            // The agent continuation is an AI call: it must obey the same
            // per-user minute/daily quota as /api/chat/*. The profile decision
            // itself has already been persisted, so quota failure only skips
            // the continuation instead of failing the business operation.
            ChatQuotaDecision quota = chatQuotaService.tryConsume(userId);
            if (!quota.allowed()) {
                quotaExceeded = true;
                retryAfterSeconds = quota.retryAfterSeconds();
                agentMessage = fallbackMessage(result) + "（AI 续答额度已用完：" + quota.message() + "）";
            } else {
                try {
                    ChatResponse chat = chatService.continueAfterProfileDecision(userId, result.agentContext());
                    agentMessage = chat.getContent();
                    agentAnswered = agentMessage != null && !agentMessage.isBlank();
                    response.setPromptVersions(chat.getPromptVersions());
                } catch (LlmUnavailableException e) {
                    agentMessage = fallbackMessage(result);
                } catch (RuntimeException e) {
                    agentMessage = fallbackMessage(result);
                }
            }
        } else {
            // Idempotent / expired / conflict decisions do not invoke the model.
            agentMessage = fallbackMessage(result);
        }
        response.setAgentMessage(agentMessage);
        response.setAgentAnswered(agentAnswered);
        response.setQuotaExceeded(quotaExceeded);
        response.setRetryAfterSeconds(retryAfterSeconds);
        return ResponseEntity.ok(response);
    }

    private String fallbackMessage(ProfileChangeService.DecisionResult result) {
        String status = result.candidate().getStatus();
        if ("CONFIRMED".equals(status)) {
            return "画像变更已确认并写入正式画像。";
        }
        if ("REJECTED".equals(status)) {
            return "已拒绝该画像变更，信息不会写入正式画像。";
        }
        if ("EXPIRED".equals(status)) {
            return "该画像变更候选已过期，请重新在对话中提出。";
        }
        if ("CONFLICT".equals(status)) {
            return "画像已被其他操作更新，请刷新后重新提出变更。";
        }
        return "画像变更处理完成。";
    }

    private ResponseEntity<?> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(java.util.Map.of("error", "未登录"));
    }

    private User currentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }
        return userRepository.findByUsername(authentication.getName());
    }
}
