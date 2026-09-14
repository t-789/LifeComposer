package org.example.lifecomposer.Controller;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.lifecomposer.Entity.User;
import org.example.lifecomposer.Entity.UserType;
import org.example.lifecomposer.Exception.AdminApiException;
import org.example.lifecomposer.Repository.UserRepository;
import org.example.lifecomposer.Service.AdminAuditLogger;
import org.example.lifecomposer.Service.AdminConsoleService;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Milestone 7 admin debug console API.
 *
 * <p>All endpoints are read-only (GET), are restricted to {@code ROLE_ADMIN} by
 * both the security filter chain and the checks below, never implement arbitrary
 * SQL/table/column access, and answer with a fixed envelope
 * ({@code items/page/pageSize/total/totalPages}) or a stable error code.</p>
 *
 * <h2>Trust model (review decision, v0.0.6)</h2>
 * <p>The administrator is a <b>fully trusted debug/operations role</b> on this
 * single-instance deployment. Hard exclusions are absolute: credentials
 * ({@code password_hash}), raw embedding vectors ({@code embedding_json}),
 * certificate file paths ({@code certificate_ref}) and server-side environment
 * values never leave the API. Personal profile data, however, is visible to an
 * operator on purpose — the profile <em>list</em> masks the student id as a
 * display minimisation, while {@code GET /api/admin/users/{id}/profile} returns
 * the full student id and the preferences JSON because those are exactly what an
 * operator needs when debugging a user's planning output. Any change to this
 * model must be reflected in {@code admin.js}, {@code API.md} and this javadoc.</p>
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final Logger LOG = LogManager.getLogger(AdminController.class);

    private final AdminConsoleService consoleService;
    private final UserRepository userRepository;
    private final AdminAuditLogger auditLogger;

    public AdminController(AdminConsoleService consoleService,
                           UserRepository userRepository,
                           AdminAuditLogger auditLogger) {
        this.consoleService = consoleService;
        this.userRepository = userRepository;
        this.auditLogger = auditLogger;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<?> dashboard(Authentication authentication,
                                       @RequestParam Map<String, String> query) {
        User admin = requireAdmin(authentication);
        return ResponseEntity.ok(consoleService.dashboard(admin.getUsername(), admin.getId(), query));
    }

    @GetMapping("/users")
    public ResponseEntity<?> users(Authentication authentication,
                                   @RequestParam Map<String, String> query) {
        User admin = requireAdmin(authentication);
        return ResponseEntity.ok(consoleService.users(admin.getUsername(), admin.getId(), query));
    }

    @GetMapping("/users/{userId}/profile")
    public ResponseEntity<?> userProfile(Authentication authentication,
                                         @PathVariable int userId) {
        User admin = requireAdmin(authentication);
        return ResponseEntity.ok(
                consoleService.userProfileDetail(admin.getUsername(), admin.getId(), userId));
    }

    @GetMapping("/user-profiles")
    public ResponseEntity<?> userProfiles(Authentication authentication,
                                          @RequestParam Map<String, String> query) {
        User admin = requireAdmin(authentication);
        return ResponseEntity.ok(
                consoleService.userProfiles(admin.getUsername(), admin.getId(), query));
    }

    @GetMapping("/planning-history")
    public ResponseEntity<?> planningHistory(Authentication authentication,
                                             @RequestParam Map<String, String> query) {
        User admin = requireAdmin(authentication);
        return ResponseEntity.ok(
                consoleService.planningHistory(admin.getUsername(), admin.getId(), query));
    }

    @GetMapping("/planning-history/{id}")
    public ResponseEntity<?> planningHistoryDetail(Authentication authentication,
                                                   @PathVariable long id) {
        User admin = requireAdmin(authentication);
        return ResponseEntity.ok(
                consoleService.planningHistoryDetail(admin.getUsername(), admin.getId(), id));
    }

    @GetMapping("/chat-messages")
    public ResponseEntity<?> chatMessages(Authentication authentication,
                                          @RequestParam Map<String, String> query) {
        User admin = requireAdmin(authentication);
        return ResponseEntity.ok(
                consoleService.chatMessages(admin.getUsername(), admin.getId(), query));
    }

    @GetMapping("/chat-messages/{id}")
    public ResponseEntity<?> chatMessageDetail(Authentication authentication, @PathVariable long id) {
        User admin = requireAdmin(authentication);
        return ResponseEntity.ok(
                consoleService.chatMessageDetail(admin.getUsername(), admin.getId(), id));
    }

    @GetMapping("/feedback")
    public ResponseEntity<?> feedback(Authentication authentication,
                                      @RequestParam Map<String, String> query) {
        User admin = requireAdmin(authentication);
        return ResponseEntity.ok(consoleService.feedback(admin.getUsername(), admin.getId(), query));
    }

    @GetMapping("/feedback/{id}")
    public ResponseEntity<?> feedbackDetail(Authentication authentication, @PathVariable long id) {
        User admin = requireAdmin(authentication);
        return ResponseEntity.ok(
                consoleService.feedbackDetail(admin.getUsername(), admin.getId(), id));
    }

    @GetMapping("/college-credit-rules")
    public ResponseEntity<?> collegeCreditRules(Authentication authentication,
                                                @RequestParam Map<String, String> query) {
        User admin = requireAdmin(authentication);
        return ResponseEntity.ok(
                consoleService.collegeCreditRules(admin.getUsername(), admin.getId(), query));
    }

    @GetMapping("/college-credit-rules/{id}")
    public ResponseEntity<?> collegeCreditRuleDetail(Authentication authentication,
                                                     @PathVariable long id) {
        User admin = requireAdmin(authentication);
        return ResponseEntity.ok(
                consoleService.collegeCreditRuleDetail(admin.getUsername(), admin.getId(), id));
    }

    @GetMapping("/credit-activities")
    public ResponseEntity<?> creditActivities(Authentication authentication,
                                              @RequestParam Map<String, String> query) {
        User admin = requireAdmin(authentication);
        return ResponseEntity.ok(
                consoleService.creditActivities(admin.getUsername(), admin.getId(), query));
    }

    @GetMapping("/credit-activities/{id}")
    public ResponseEntity<?> creditActivityDetail(Authentication authentication,
                                                  @PathVariable long id) {
        User admin = requireAdmin(authentication);
        return ResponseEntity.ok(
                consoleService.creditActivityDetail(admin.getUsername(), admin.getId(), id));
    }

    @GetMapping("/resources")
    public ResponseEntity<?> resources(Authentication authentication,
                                       @RequestParam Map<String, String> query) {
        User admin = requireAdmin(authentication);
        return ResponseEntity.ok(consoleService.resources(admin.getUsername(), admin.getId(), query));
    }

    @GetMapping("/resources/{id}")
    public ResponseEntity<?> resourceDetail(Authentication authentication, @PathVariable long id) {
        User admin = requireAdmin(authentication);
        return ResponseEntity.ok(
                consoleService.resourceDetail(admin.getUsername(), admin.getId(), id));
    }

    @GetMapping("/rag-chunks")
    public ResponseEntity<?> ragChunks(Authentication authentication,
                                       @RequestParam Map<String, String> query) {
        User admin = requireAdmin(authentication);
        return ResponseEntity.ok(consoleService.ragChunks(admin.getUsername(), admin.getId(), query));
    }

    @GetMapping("/rag-chunks/{chunkId}")
    public ResponseEntity<?> ragChunkDetail(Authentication authentication,
                                            @PathVariable String chunkId) {
        User admin = requireAdmin(authentication);
        return ResponseEntity.ok(
                consoleService.ragChunkDetail(admin.getUsername(), admin.getId(), chunkId));
    }

    @GetMapping("/capability-tags")
    public ResponseEntity<?> capabilityTags(Authentication authentication,
                                            @RequestParam Map<String, String> query) {
        User admin = requireAdmin(authentication);
        return ResponseEntity.ok(
                consoleService.capabilityTags(admin.getUsername(), admin.getId(), query));
    }

    @GetMapping("/capability-tags/{name}")
    public ResponseEntity<?> capabilityTagDetail(Authentication authentication,
                                                 @PathVariable String name) {
        User admin = requireAdmin(authentication);
        return ResponseEntity.ok(
                consoleService.capabilityTagDetail(admin.getUsername(), admin.getId(), name));
    }

    @GetMapping("/capability-reference")
    public ResponseEntity<?> capabilityReference(Authentication authentication,
                                                 @RequestParam Map<String, String> query) {
        User admin = requireAdmin(authentication);
        return ResponseEntity.ok(
                consoleService.capabilityReference(admin.getUsername(), admin.getId(), query));
    }

    @GetMapping("/capability-reference/{id}")
    public ResponseEntity<?> capabilityReferenceDetail(Authentication authentication,
                                                       @PathVariable long id) {
        User admin = requireAdmin(authentication);
        return ResponseEntity.ok(
                consoleService.capabilityReferenceDetail(admin.getUsername(), admin.getId(), id));
    }

    @GetMapping("/chat-usage")
    public ResponseEntity<?> chatUsage(Authentication authentication,
                                       @RequestParam Map<String, String> query) {
        User admin = requireAdmin(authentication);
        return ResponseEntity.ok(consoleService.chatUsage(admin.getUsername(), admin.getId(), query));
    }

    // ------------------------------------------------------------ error handling

    /**
     * Every rejected console request is audited here — one choke point covering
     * invalid parameters, unknown parameters and rate limiting. Only the URI and
     * the error code are recorded, never the offending value.
     */
    @ExceptionHandler(AdminApiException.class)
    public ResponseEntity<Map<String, Object>> handleAdminApi(AdminApiException e,
                                                              HttpServletRequest request,
                                                              Authentication authentication) {
        if (e.getStatus() == HttpStatus.BAD_REQUEST || e.getStatus() == HttpStatus.TOO_MANY_REQUESTS) {
            auditLogger.queryRejected(adminName(authentication), request.getRequestURI(), e.getCode());
        }
        return ResponseEntity.status(e.getStatus())
                .body(Map.of("error", e.getCode(), "message", e.getMessage()));
    }

    /**
     * Database failures are mapped to a stable code. The response never contains
     * SQL, table metadata or a stack trace.
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, Object>> handleDataAccess(DataAccessException e,
                                                                HttpServletRequest request,
                                                                Authentication authentication) {
        LOG.error("Admin console query failed: {}", e.getClass().getSimpleName());
        auditLogger.queryRejected(adminName(authentication), request.getRequestURI(), "QUERY_FAILED");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "ADMIN_QUERY_FAILED",
                        "message", "查询失败，请缩小筛选范围后重试"));
    }

    private String adminName(Authentication authentication) {
        return authentication == null || authentication.getName() == null
                ? "unknown" : authentication.getName();
    }

    private User requireAdmin(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new AdminApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "权限不足");
        }
        User user = userRepository.findByUsername(authentication.getName());
        if (user == null || user.getType() == null || user.getType() != UserType.ADMIN) {
            throw new AdminApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "权限不足");
        }
        return user;
    }
}
