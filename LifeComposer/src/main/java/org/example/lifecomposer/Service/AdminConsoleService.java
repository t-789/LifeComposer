package org.example.lifecomposer.Service;

import org.example.lifecomposer.Exception.AdminApiException;
import org.example.lifecomposer.Repository.AdminQueryRepository;
import org.example.lifecomposer.config.AdminSecurityProperties;
import org.example.lifecomposer.config.AppSecurityProperties;
import org.example.lifecomposer.dto.AdminPage;
import org.example.lifecomposer.dto.PageResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Milestone 7 admin console service.
 *
 * <p>Responsibilities: validate every request parameter against a whitelist,
 * resolve sorting from a per-dataset column allow-list (so user input never
 * reaches the SQL text), mask sensitive fields before they leave the server,
 * enforce a per-administrator read rate limit and write the audit trail.</p>
 */
@Service
public class AdminConsoleService {

    private static final int MAX_KEYWORD_LENGTH = 64;
    private static final int MAX_ENUM_LENGTH = 64;
    private static final int MAX_DATE_LENGTH = 10;

    private static final Set<String> PAGE_PARAMS = Set.of("page", "pageSize", "sort", "dir");

    private static final Set<String> USER_PARAMS =
            params(PAGE_PARAMS, "q", "type", "banned", "hasProfile");
    private static final Set<String> PROFILE_PARAMS =
            params(PAGE_PARAMS, "q", "college", "major", "grade", "userId");
    private static final Set<String> PLANNING_PARAMS =
            params(PAGE_PARAMS, "userId", "type", "status", "provider", "model", "dateFrom", "dateTo");
    private static final Set<String> CHAT_MESSAGE_PARAMS =
            params(PAGE_PARAMS, "userId", "role", "dateFrom", "dateTo");
    private static final Set<String> FEEDBACK_PARAMS =
            params(PAGE_PARAMS, "type", "resolved", "userId", "dateFrom", "dateTo");
    private static final Set<String> CREDIT_RULE_PARAMS =
            params(PAGE_PARAMS, "college", "creditType", "category", "compLevel", "awardTier", "q");
    private static final Set<String> CREDIT_ACTIVITY_PARAMS =
            params(PAGE_PARAMS, "userId", "creditType", "verified");
    private static final Set<String> RESOURCE_PARAMS =
            params(PAGE_PARAMS, "type", "difficulty", "dataQuality", "provider", "q");
    private static final Set<String> RAG_PARAMS =
            params(PAGE_PARAMS, "embeddingStatus", "sourceType", "relatedResourceId", "q");
    private static final Set<String> TAG_PARAMS = params(PAGE_PARAMS, "category", "q");
    private static final Set<String> REFERENCE_PARAMS = params(PAGE_PARAMS, "section", "q");
    private static final Set<String> USAGE_PARAMS =
            params(PAGE_PARAMS, "userId", "usageDate", "dateFrom", "dateTo");

    private static final Set<String> CREDIT_TYPES = Set.of("graduation", "recommendation");
    private static final Set<String> CREDIT_CATEGORIES = Set.of(
            "competition", "lecture", "course", "project", "paper", "patent", "sports", "arts", "veteran");
    private static final Set<String> COMP_LEVELS = Set.of(
            "S", "A+", "A", "B+", "B", "national", "provincial", "school");
    private static final Set<String> AWARD_TIERS = Set.of(
            "first", "second", "third", "special", "participation");
    private static final Set<String> RESOURCE_TYPES = Set.of("competition", "course");
    private static final Set<String> DIFFICULTIES = Set.of("easy", "medium", "hard");
    private static final Set<String> DATA_QUALITIES = Set.of("complete", "partial", "needs_review");
    private static final Set<String> SOURCE_TYPES = Set.of("web", "pdf", "json");
    private static final Set<String> EMBEDDING_STATUSES = Set.of("PENDING", "SUCCESS", "FAILED", "SKIPPED");
    private static final Set<String> MESSAGE_ROLES = Set.of("user", "assistant", "tool");
    private static final Set<String> FEEDBACK_TYPES = Set.of("user", "system");
    private static final Set<String> TAG_CATEGORIES = Set.of("技术能力", "通用能力");
    private static final Set<String> REFERENCE_SECTIONS = Set.of(
            "tags_to_merge", "skill_mapping", "skill_profiles", "role_profiles", "major_categories", "_meta");

    private static final Map<String, String> USER_SORTS = sorts(
            "id", "u.id", "username", "u.username", "createdAt", "u.created_at", "type", "u.type");
    private static final Map<String, String> PROFILE_SORTS = sorts(
            "userId", "p.user_id", "updatedAt", "p.updated_at", "college", "p.college", "grade", "p.grade");
    private static final Map<String, String> PLANNING_SORTS = sorts(
            "id", "h.id", "createdAt", "h.created_at", "status", "h.status", "userId", "h.user_id");
    private static final Map<String, String> CHAT_MESSAGE_SORTS = sorts(
            "id", "m.id", "createTime", "m.create_time", "userId", "m.user_id", "role", "m.role");
    private static final Map<String, String> FEEDBACK_SORTS = sorts(
            "id", "f.id", "createTime", "f.create_time", "resolved", "f.resolved", "type", "f.type");
    private static final Map<String, String> CREDIT_RULE_SORTS = sorts(
            "id", "r.id", "college", "r.college", "credits", "r.credits",
            "compName", "r.comp_name", "category", "r.category", "compLevel", "r.comp_level");
    private static final Map<String, String> CREDIT_ACTIVITY_SORTS = sorts(
            "id", "a.id", "credits", "a.credits", "obtainedDate", "a.obtained_date",
            "verified", "a.verified", "userId", "a.user_id");
    private static final Map<String, String> RESOURCE_SORTS = sorts(
            "id", "r.id", "resourceId", "r.resource_id", "name", "r.name", "type", "r.type",
            "difficulty", "r.difficulty", "dataQuality", "r.data_quality", "updatedAt", "r.updated_at");
    private static final Map<String, String> RAG_SORTS = sorts(
            "chunkId", "c.chunk_id", "title", "c.title", "embeddingStatus", "c.embedding_status",
            "embeddingUpdatedAt", "c.embedding_updated_at", "createdAt", "c.created_at");
    private static final Map<String, String> TAG_SORTS = sorts(
            "name", "t.name", "category", "t.category");
    private static final Map<String, String> REFERENCE_SORTS = sorts(
            "id", "r.id", "section", "r.section", "refKey", "r.ref_key");
    private static final Map<String, String> USAGE_SORTS = sorts(
            "id", "d.id", "usageDate", "d.usage_date", "requestCount", "d.request_count", "userId", "d.user_id");

    private final AdminQueryRepository repository;
    private final ChatQuotaService chatQuotaService;
    private final AdminSecurityProperties properties;
    private final AppSecurityProperties appSecurityProperties;
    private final AdminQueryRateLimiter rateLimiter;
    private final AdminAuditLogger audit;

    public AdminConsoleService(AdminQueryRepository repository,
                               ChatQuotaService chatQuotaService,
                               AdminSecurityProperties properties,
                               AppSecurityProperties appSecurityProperties,
                               AdminQueryRateLimiter rateLimiter,
                               AdminAuditLogger audit) {
        this.repository = repository;
        this.chatQuotaService = chatQuotaService;
        this.properties = properties;
        this.appSecurityProperties = appSecurityProperties;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
    }

    // -------------------------------------------------------------- overview

    public Map<String, Object> dashboard(String admin, int adminUserId, Map<String, String> query) {
        long started = guard(admin, adminUserId, "dashboard", query, PAGE_PARAMS);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tables", repository.tableCounts());
        result.put("users", repository.userStats());
        result.put("feedback", repository.feedbackStats());
        result.put("planning", repository.planningStats());
        result.put("embeddingStatus", repository.embeddingStatusDistribution());
        result.put("recentLlmFailures", repository.recentLlmFailures(5, previewLength()));
        result.put("generatedAt", java.time.Instant.now().toString());

        String today = chatQuotaService.today();
        Map<String, Object> usage = new LinkedHashMap<>(repository.chatUsageToday(today));
        usage.put("date", today);
        usage.put("dailyLimitPerUser", appSecurityProperties.getChatPerDay());
        usage.put("minuteLimitPerUser", appSecurityProperties.getChatPerMinute());
        usage.put("topUsersToday", repository.topChatUsageToday(today, 5));
        result.put("chatUsage", usage);

        audit(admin, "dashboard", query, 0, 0, started);
        return result;
    }

    // ----------------------------------------------------------------- users

    public PageResponse<Map<String, Object>> users(String admin, int adminUserId, Map<String, String> query) {
        long started = guard(admin, adminUserId, "users", query, USER_PARAMS);
        AdminPage page = pageSpec(query, USER_SORTS, "id", "DESC");
        String usageDate = chatQuotaService.today();

        AdminQueryRepository.Rows rows = repository.pageUsers(page,
                keyword(query, "q"), intValue(query, "type", 1, 2),
                boolValue(query, "banned"), boolValue(query, "hasProfile"), usageDate);

        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> row : rows.items()) {
            Map<String, Object> item = new LinkedHashMap<>(row);
            Integer userType = asInt(item.get("userType"));
            item.put("userType", userType == null ? 1 : userType);
            item.put("role", userType != null && userType == 2 ? "ADMIN" : "USER");
            item.put("banned", asBoolean(item.get("banned")));
            item.put("hasProfile", asBoolean(item.get("hasProfile")));
            // Review follow-up: operators need to see that an account is still on
            // an administrator-issued temporary password, and until when.
            item.put("passwordResetRequired", asBoolean(item.get("passwordResetRequired")));
            int used = asIntOrZero(item.get("usedToday"));
            item.put("usedToday", used);
            item.put("dailyLimit", appSecurityProperties.getChatPerDay());
            item.put("remainingToday", Math.max(0, appSecurityProperties.getChatPerDay() - used));
            items.add(item);
        }
        return respond(admin, "users", query, page, items, rows.total(), started);
    }

    // -------------------------------------------------------------- profiles

    public PageResponse<Map<String, Object>> userProfiles(String admin, int adminUserId,
                                                          Map<String, String> query) {
        long started = guard(admin, adminUserId, "user_profiles", query, PROFILE_PARAMS);
        AdminPage page = pageSpec(query, PROFILE_SORTS, "userId", "ASC");

        AdminQueryRepository.Rows rows = repository.pageUserProfiles(page,
                keyword(query, "q"), enumFreeText(query, "college"), enumFreeText(query, "major"),
                enumFreeText(query, "grade"), intValue(query, "userId", 1, Integer.MAX_VALUE));

        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> row : rows.items()) {
            Map<String, Object> item = new LinkedHashMap<>(row);
            item.put("studentId", maskStudentId(asString(item.get("studentId"))));
            item.put("studentIdMasked", true);
            item.put("hasPreferences", asBoolean(item.get("hasPreferences")));
            items.add(item);
        }
        return respond(admin, "user_profiles", query, page, items, rows.total(), started);
    }

    public Map<String, Object> userProfileDetail(String admin, int adminUserId, int userId) {
        long started = guard(admin, adminUserId, "user_profiles", Map.of(), PROFILE_PARAMS);
        Map<String, Object> row = repository.findUserProfile(userId);
        if (row == null) {
            throw AdminApiException.notFound("用户画像不存在");
        }
        Map<String, Object> item = new LinkedHashMap<>(row);
        item.put("userType", asInt(item.get("userType")));
        audit(admin, "user_profiles", Map.of("userId", String.valueOf(userId)), 1, 1, started);
        return item;
    }

    // ------------------------------------------------------ planning history

    public PageResponse<Map<String, Object>> planningHistory(String admin, int adminUserId,
                                                             Map<String, String> query) {
        long started = guard(admin, adminUserId, "planning_history", query, PLANNING_PARAMS);
        AdminPage page = pageSpec(query, PLANNING_SORTS, "id", "DESC");

        AdminQueryRepository.Rows rows = repository.pagePlanningHistory(page,
                intValue(query, "userId", 1, Integer.MAX_VALUE), freeText(query, "type", MAX_ENUM_LENGTH),
                freeText(query, "status", MAX_ENUM_LENGTH), freeText(query, "provider", MAX_ENUM_LENGTH),
                freeText(query, "model", MAX_ENUM_LENGTH), dateValue(query, "dateFrom"),
                dateValue(query, "dateTo"), previewLength());

        return respond(admin, "planning_history", query, page, rows.items(), rows.total(), started);
    }

    public Map<String, Object> planningHistoryDetail(String admin, int adminUserId, long id) {
        long started = guard(admin, adminUserId, "planning_history", Map.of(), PLANNING_PARAMS);
        Map<String, Object> row = repository.findPlanningHistory(id);
        if (row == null) {
            throw AdminApiException.notFound("记录不存在");
        }
        Map<String, Object> item = new LinkedHashMap<>(row);
        item.put("requestJson", truncate(asString(item.get("requestJson")), diagnosticLength()));
        item.put("responseJson", truncate(asString(item.get("responseJson")), diagnosticLength()));
        item.put("errorMessage", truncate(asString(item.get("errorMessage")), diagnosticLength()));
        audit(admin, "planning_history", Map.of("id", String.valueOf(id)), 1, 1, started);
        return item;
    }

    // -------------------------------------------------------- chat messages

    public PageResponse<Map<String, Object>> chatMessages(String admin, int adminUserId,
                                                          Map<String, String> query) {
        long started = guard(admin, adminUserId, "chat_messages", query, CHAT_MESSAGE_PARAMS);
        AdminPage page = pageSpec(query, CHAT_MESSAGE_SORTS, "id", "DESC");

        AdminQueryRepository.Rows rows = repository.pageChatMessages(page,
                intValue(query, "userId", 1, Integer.MAX_VALUE),
                enumValue(query, "role", MESSAGE_ROLES),
                dateValue(query, "dateFrom"), dateValue(query, "dateTo"), previewLength());

        List<Map<String, Object>> items = rows.items().stream().map(row -> {
            Map<String, Object> item = new LinkedHashMap<>(row);
            item.put("structured", asBoolean(item.get("structured")));
            return item;
        }).toList();
        return respond(admin, "chat_messages", query, page, items, rows.total(), started);
    }

    public Map<String, Object> chatMessageDetail(String admin, int adminUserId, long id) {
        long started = guard(admin, adminUserId, "chat_messages", Map.of(), CHAT_MESSAGE_PARAMS);
        Map<String, Object> row = repository.findChatMessage(id);
        if (row == null) {
            throw AdminApiException.notFound("消息不存在");
        }
        Map<String, Object> item = new LinkedHashMap<>(row);
        String content = asString(item.get("content"));
        item.put("contentLength", content == null ? 0 : content.length());
        item.put("contentTruncated", content != null && content.length() > diagnosticLength());
        item.put("content", truncate(content, diagnosticLength()));
        audit(admin, "chat_messages", Map.of("id", String.valueOf(id)), 1, 1, started);
        return item;
    }

    // ------------------------------------------------------------- feedback

    public PageResponse<Map<String, Object>> feedback(String admin, int adminUserId,
                                                      Map<String, String> query) {
        long started = guard(admin, adminUserId, "feedback", query, FEEDBACK_PARAMS);
        AdminPage page = pageSpec(query, FEEDBACK_SORTS, "id", "DESC");

        AdminQueryRepository.Rows rows = repository.pageFeedback(page,
                enumValue(query, "type", FEEDBACK_TYPES), boolValue(query, "resolved"),
                intValue(query, "userId", 1, Integer.MAX_VALUE),
                dateValue(query, "dateFrom"), dateValue(query, "dateTo"), previewLength());

        List<Map<String, Object>> items = rows.items().stream().map(row -> {
            Map<String, Object> item = new LinkedHashMap<>(row);
            item.put("resolved", asBoolean(item.get("resolved")));
            item.put("hasStackTrace", asBoolean(item.get("hasStackTrace")));
            return item;
        }).toList();
        return respond(admin, "feedback", query, page, items, rows.total(), started);
    }

    public Map<String, Object> feedbackDetail(String admin, int adminUserId, long id) {
        long started = guard(admin, adminUserId, "feedback", Map.of(), FEEDBACK_PARAMS);
        Map<String, Object> row = repository.findFeedback(id, diagnosticLength());
        if (row == null) {
            throw AdminApiException.notFound("反馈不存在");
        }
        Map<String, Object> item = new LinkedHashMap<>(row);
        item.put("resolved", asBoolean(item.get("resolved")));
        audit(admin, "feedback", Map.of("id", String.valueOf(id)), 1, 1, started);
        return item;
    }

    // ------------------------------------------------------- credit rules

    public PageResponse<Map<String, Object>> collegeCreditRules(String admin, int adminUserId,
                                                                Map<String, String> query) {
        long started = guard(admin, adminUserId, "college_credit_rules", query, CREDIT_RULE_PARAMS);
        AdminPage page = pageSpec(query, CREDIT_RULE_SORTS, "id", "ASC");

        AdminQueryRepository.Rows rows = repository.pageCollegeCreditRules(page,
                freeText(query, "college", MAX_ENUM_LENGTH),
                enumValue(query, "creditType", CREDIT_TYPES),
                enumValue(query, "category", CREDIT_CATEGORIES),
                enumValue(query, "compLevel", COMP_LEVELS),
                enumValue(query, "awardTier", AWARD_TIERS),
                keyword(query, "q"), previewLength());

        return respond(admin, "college_credit_rules", query, page, rows.items(), rows.total(), started);
    }

    public Map<String, Object> collegeCreditRuleDetail(String admin, int adminUserId, long id) {
        long started = guard(admin, adminUserId, "college_credit_rules", Map.of(), CREDIT_RULE_PARAMS);
        Map<String, Object> row = repository.findCollegeCreditRule(id);
        if (row == null) {
            throw AdminApiException.notFound("加分规则不存在");
        }
        audit(admin, "college_credit_rules", Map.of("id", String.valueOf(id)), 1, 1, started);
        return row;
    }

    // --------------------------------------------------- credit activities

    public PageResponse<Map<String, Object>> creditActivities(String admin, int adminUserId,
                                                              Map<String, String> query) {
        long started = guard(admin, adminUserId, "credit_activities", query, CREDIT_ACTIVITY_PARAMS);
        AdminPage page = pageSpec(query, CREDIT_ACTIVITY_SORTS, "id", "DESC");

        AdminQueryRepository.Rows rows = repository.pageCreditActivities(page,
                intValue(query, "userId", 1, Integer.MAX_VALUE),
                enumValue(query, "creditType", CREDIT_TYPES),
                boolValue(query, "verified"), previewLength());

        List<Map<String, Object>> items = rows.items().stream().map(row -> {
            Map<String, Object> item = new LinkedHashMap<>(row);
            item.put("verified", asBoolean(item.get("verified")));
            item.put("hasCertificate", asBoolean(item.get("hasCertificate")));
            return item;
        }).toList();
        return respond(admin, "credit_activities", query, page, items, rows.total(), started);
    }

    public Map<String, Object> creditActivityDetail(String admin, int adminUserId, long id) {
        long started = guard(admin, adminUserId, "credit_activities", Map.of(), CREDIT_ACTIVITY_PARAMS);
        Map<String, Object> row = repository.findCreditActivity(id);
        if (row == null) {
            throw AdminApiException.notFound("加分记录不存在");
        }
        Map<String, Object> item = new LinkedHashMap<>(row);
        item.put("verified", asBoolean(item.get("verified")));
        item.put("hasCertificate", asBoolean(item.get("hasCertificate")));
        audit(admin, "credit_activities", Map.of("id", String.valueOf(id)), 1, 1, started);
        return item;
    }

    // ------------------------------------------------------------ resources

    public PageResponse<Map<String, Object>> resources(String admin, int adminUserId,
                                                       Map<String, String> query) {
        long started = guard(admin, adminUserId, "resources", query, RESOURCE_PARAMS);
        AdminPage page = pageSpec(query, RESOURCE_SORTS, "id", "ASC");

        AdminQueryRepository.Rows rows = repository.pageResources(page,
                enumValue(query, "type", RESOURCE_TYPES),
                enumValue(query, "difficulty", DIFFICULTIES),
                enumValue(query, "dataQuality", DATA_QUALITIES),
                freeText(query, "provider", MAX_ENUM_LENGTH),
                keyword(query, "q"), previewLength());

        return respond(admin, "resources", query, page, rows.items(), rows.total(), started);
    }

    public Map<String, Object> resourceDetail(String admin, int adminUserId, long id) {
        long started = guard(admin, adminUserId, "resources", Map.of(), RESOURCE_PARAMS);
        Map<String, Object> row = repository.findResource(id);
        if (row == null) {
            throw AdminApiException.notFound("资源不存在");
        }
        audit(admin, "resources", Map.of("id", String.valueOf(id)), 1, 1, started);
        return row;
    }

    // ----------------------------------------------------------- rag chunks

    public PageResponse<Map<String, Object>> ragChunks(String admin, int adminUserId,
                                                       Map<String, String> query) {
        long started = guard(admin, adminUserId, "rag_chunks", query, RAG_PARAMS);
        AdminPage page = pageSpec(query, RAG_SORTS, "chunkId", "ASC");

        AdminQueryRepository.Rows rows = repository.pageRagChunks(page,
                enumValue(query, "embeddingStatus", EMBEDDING_STATUSES),
                enumValue(query, "sourceType", SOURCE_TYPES),
                freeText(query, "relatedResourceId", MAX_ENUM_LENGTH),
                keyword(query, "q"), previewLength());

        return respond(admin, "rag_chunks", query, page, rows.items(), rows.total(), started);
    }

    public Map<String, Object> ragChunkDetail(String admin, int adminUserId, String chunkId) {
        long started = guard(admin, adminUserId, "rag_chunks", Map.of(), RAG_PARAMS);
        String safeChunkId = requireText(chunkId, "chunkId", 120);
        Map<String, Object> row = repository.findRagChunk(safeChunkId, diagnosticLength());
        if (row == null) {
            throw AdminApiException.notFound("切片不存在");
        }
        Map<String, Object> item = new LinkedHashMap<>(row);
        String text = asString(item.get("text"));
        item.put("textTruncated", text != null && text.length() > diagnosticLength());
        audit(admin, "rag_chunks", Map.of("chunkId", safeChunkId), 1, 1, started);
        return item;
    }

    // -------------------------------------------------- capability dictionary

    public PageResponse<Map<String, Object>> capabilityTags(String admin, int adminUserId,
                                                            Map<String, String> query) {
        long started = guard(admin, adminUserId, "capability_tags", query, TAG_PARAMS);
        AdminPage page = pageSpec(query, TAG_SORTS, "name", "ASC");

        AdminQueryRepository.Rows rows = repository.pageCapabilityTags(page,
                enumValue(query, "category", TAG_CATEGORIES), keyword(query, "q"), previewLength());

        return respond(admin, "capability_tags", query, page, rows.items(), rows.total(), started);
    }

    public Map<String, Object> capabilityTagDetail(String admin, int adminUserId, String name) {
        long started = guard(admin, adminUserId, "capability_tags", Map.of(), TAG_PARAMS);
        String safeName = requireText(name, "name", 120);
        Map<String, Object> row = repository.findCapabilityTag(safeName);
        if (row == null) {
            throw AdminApiException.notFound("能力标签不存在");
        }
        audit(admin, "capability_tags", Map.of("name", safeName), 1, 1, started);
        return row;
    }

    public PageResponse<Map<String, Object>> capabilityReference(String admin, int adminUserId,
                                                                 Map<String, String> query) {
        long started = guard(admin, adminUserId, "capability_reference", query, REFERENCE_PARAMS);
        AdminPage page = pageSpec(query, REFERENCE_SORTS, "id", "ASC");

        AdminQueryRepository.Rows rows = repository.pageCapabilityReference(page,
                enumValue(query, "section", REFERENCE_SECTIONS), keyword(query, "q"), previewLength());

        return respond(admin, "capability_reference", query, page, rows.items(), rows.total(), started);
    }

    public Map<String, Object> capabilityReferenceDetail(String admin, int adminUserId, long id) {
        long started = guard(admin, adminUserId, "capability_reference", Map.of(), REFERENCE_PARAMS);
        Map<String, Object> row = repository.findCapabilityReference(id);
        if (row == null) {
            throw AdminApiException.notFound("字典条目不存在");
        }
        audit(admin, "capability_reference", Map.of("id", String.valueOf(id)), 1, 1, started);
        return row;
    }

    // ----------------------------------------------------------- chat usage

    public PageResponse<Map<String, Object>> chatUsage(String admin, int adminUserId,
                                                       Map<String, String> query) {
        long started = guard(admin, adminUserId, "chat_usage_daily", query, USAGE_PARAMS);
        AdminPage page = pageSpec(query, USAGE_SORTS, "usageDate", "DESC");

        AdminQueryRepository.Rows rows = repository.pageChatUsage(page,
                intValue(query, "userId", 1, Integer.MAX_VALUE),
                dateValue(query, "usageDate"),
                dateValue(query, "dateFrom"), dateValue(query, "dateTo"));

        List<Map<String, Object>> items = rows.items().stream().map(row -> {
            Map<String, Object> item = new LinkedHashMap<>(row);
            item.put("dailyLimit", appSecurityProperties.getChatPerDay());
            return item;
        }).toList();
        return respond(admin, "chat_usage_daily", query, page, items, rows.total(), started);
    }

    // ------------------------------------------------------------- internals

    private long guard(String admin, int adminUserId, String dataset, Map<String, String> query,
                       Set<String> allowedParams) {
        int limit = properties.getConsoleQueriesPerMinute();
        if (!rateLimiter.tryAcquire(adminUserId, limit)) {
            throw AdminApiException.rateLimited(
                    "管理查询过于频繁，请稍后再试（每分钟最多 " + limit + " 次）");
        }
        for (String key : query.keySet()) {
            if (!allowedParams.contains(key)) {
                // Rejections are audited centrally by AdminController's advice handler.
                throw AdminApiException.badRequest("UNKNOWN_PARAMETER", "不支持的查询参数");
            }
        }
        return System.currentTimeMillis();
    }

    private <T> PageResponse<T> respond(String admin, String dataset, Map<String, String> query,
                                        AdminPage page, List<T> items, long total, long started) {
        audit(admin, dataset, query, total, items.size(), started);
        return PageResponse.of(items, page.page(), page.pageSize(), total);
    }

    private void audit(String admin, String dataset, Map<String, String> query, long total,
                       int returned, long started) {
        audit.query(admin, dataset, filterSummary(query), total, returned,
                Math.max(0L, System.currentTimeMillis() - started));
    }

    /** Compact, newline-free filter summary for the audit line. */
    private String filterSummary(Map<String, String> query) {
        if (query.isEmpty()) {
            return "-";
        }
        StringBuilder summary = new StringBuilder();
        query.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    if (summary.length() > 0) {
                        summary.append(',');
                    }
                    summary.append(entry.getKey()).append('=')
                            .append(sanitizeForLog(entry.getValue()));
                });
        return summary.toString();
    }

    private String sanitizeForLog(String value) {
        if (value == null) {
            return "";
        }
        String flat = value.replaceAll("[\\r\\n\\t]", " ");
        return flat.length() > 60 ? flat.substring(0, 60) + "…" : flat;
    }

    private AdminPage pageSpec(Map<String, String> query, Map<String, String> sorts,
                               String defaultSortKey, String defaultDirection) {
        int page = intOrDefault(query, "page", 1, 1, 100_000);
        int pageSize = intOrDefault(query, "pageSize", properties.getConsoleDefaultPageSize(),
                1, properties.getConsoleMaxPageSize());

        String requestedSort = freeText(query, "sort", 40);
        String column;
        if (requestedSort == null) {
            column = sorts.get(defaultSortKey);
        } else {
            column = sorts.get(requestedSort);
            if (column == null) {
                throw AdminApiException.badRequest("INVALID_PARAMETER",
                        "不支持的排序字段，可选值：" + String.join(", ", new LinkedHashSet<>(sorts.keySet())));
            }
        }

        String requestedDir = freeText(query, "dir", 8);
        String direction;
        if (requestedDir == null) {
            direction = defaultDirection;
        } else if ("asc".equalsIgnoreCase(requestedDir)) {
            direction = "ASC";
        } else if ("desc".equalsIgnoreCase(requestedDir)) {
            direction = "DESC";
        } else {
            throw AdminApiException.badRequest("INVALID_PARAMETER", "dir 只能是 asc 或 desc");
        }

        return new AdminPage(page, pageSize, column + " " + direction);
    }

    private int intOrDefault(Map<String, String> query, String key, int defaultValue, int min, int max) {
        Integer value = intValue(query, key, min, max);
        return value == null ? defaultValue : value;
    }

    private Integer intValue(Map<String, String> query, String key, int min, int max) {
        String raw = freeText(query, key, 24);
        if (raw == null) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(raw);
            if (parsed < min || parsed > max) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw AdminApiException.badRequest("INVALID_PARAMETER",
                    "参数 " + key + " 必须是 " + min + " 到 " + max + " 之间的整数");
        }
    }

    private Boolean boolValue(Map<String, String> query, String key) {
        String raw = freeText(query, key, 8);
        if (raw == null) {
            return null;
        }
        if ("1".equals(raw) || "true".equalsIgnoreCase(raw)) {
            return Boolean.TRUE;
        }
        if ("0".equals(raw) || "false".equalsIgnoreCase(raw)) {
            return Boolean.FALSE;
        }
        throw AdminApiException.badRequest("INVALID_PARAMETER", "参数 " + key + " 只能是 0/1 或 true/false");
    }

    private String enumValue(Map<String, String> query, String key, Set<String> allowed) {
        String raw = freeText(query, key, MAX_ENUM_LENGTH);
        if (raw == null) {
            return null;
        }
        if (!allowed.contains(raw)) {
            throw AdminApiException.badRequest("INVALID_PARAMETER",
                    "参数 " + key + " 取值不在允许范围内：" + String.join(", ", new LinkedHashSet<>(allowed)));
        }
        return raw;
    }

    private String dateValue(Map<String, String> query, String key) {
        String raw = freeText(query, key, MAX_DATE_LENGTH);
        if (raw == null) {
            return null;
        }
        try {
            LocalDate.parse(raw);
        } catch (DateTimeParseException e) {
            throw AdminApiException.badRequest("INVALID_PARAMETER", "参数 " + key + " 必须是 YYYY-MM-DD 日期");
        }
        return raw;
    }

    /** Free-text filter with a length cap; enums are not applied. */
    private String freeText(Map<String, String> query, String key, int maxLength) {
        String value = query.get(key);
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > maxLength) {
            throw AdminApiException.badRequest("INVALID_PARAMETER", "参数 " + key + " 过长");
        }
        return trimmed;
    }

    private String keyword(Map<String, String> query, String key) {
        return freeText(query, key, MAX_KEYWORD_LENGTH);
    }

    /** Exact-match text filter (college / major / grade / provider names). */
    private String enumFreeText(Map<String, String> query, String key) {
        return freeText(query, key, MAX_ENUM_LENGTH);
    }

    private String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw AdminApiException.badRequest("INVALID_PARAMETER", field + " 非法");
        }
        return value;
    }

    private int previewLength() {
        return Math.max(40, properties.getConsolePreviewLength());
    }

    private int diagnosticLength() {
        return Math.max(200, properties.getConsoleDiagnosticLength());
    }

    /** Mask a student id: keep a short prefix/suffix so it is still recognisable. */
    private String maskStudentId(String studentId) {
        if (studentId == null || studentId.isBlank()) {
            return null;
        }
        if (studentId.length() <= 6) {
            return "*".repeat(studentId.length());
        }
        return studentId.substring(0, 3) + "****" + studentId.substring(studentId.length() - 2);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    /** Null-safe integer projection for aggregate expressions such as COALESCE(...). */
    private int asIntOrZero(Object value) {
        Integer parsed = asInt(value);
        return parsed == null ? 0 : parsed;
    }

    private Integer asInt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.valueOf(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean asBoolean(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        String text = value.toString().trim().toLowerCase(Locale.ROOT);
        return "1".equals(text) || "true".equals(text);
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private static Set<String> params(Set<String> base, String... extra) {
        Set<String> all = new LinkedHashSet<>(base);
        all.addAll(List.of(extra));
        return Set.copyOf(all);
    }

    private static Map<String, String> sorts(String... keyColumnPairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < keyColumnPairs.length; i += 2) {
            map.put(keyColumnPairs[i], keyColumnPairs[i + 1]);
        }
        return Map.copyOf(map);
    }
}
