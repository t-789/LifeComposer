package org.example.lifecomposer.Repository;

import org.example.lifecomposer.config.AdminSecurityProperties;
import org.example.lifecomposer.dto.AdminPage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Milestone 7 read-only queries backing the admin debug console.
 *
 * <p>Security rules enforced here:</p>
 * <ul>
 *   <li>Every projection is an explicit allow-list of columns. Sensitive columns
 *       ({@code password_hash}, {@code embedding_json}, {@code certificate_ref},
 *       {@code preferences_json} in list views) are never selected.</li>
 *   <li>Sorting is expressed as a fully resolved {@code ORDER BY} expression built
 *       by the service from a whitelist; this class additionally rejects anything
 *       that is not a plain identifier.</li>
 *   <li>Filters are always bound parameters; user text is escaped before being
 *       embedded in a {@code LIKE} pattern.</li>
 *   <li>All queries run on a private {@link JdbcTemplate} with a statement query
 *       timeout so a runaway console query cannot hold a connection forever.</li>
 * </ul>
 *
 * <p>Timestamps are stored inconsistently by the two write paths (JDBC
 * {@code Timestamp} as epoch millis, SQL {@code datetime('now')} as text), so
 * read expressions normalise both forms. Date predicates are intentionally
 * non-indexable for that reason; every list query still filters on an indexed
 * column (user_id / status / type) in the common console use cases.</p>
 */
@Repository
public class AdminQueryRepository {

    /** One page of rows plus the unpaged total. */
    public record Rows(List<Map<String, Object>> items, long total) {
    }

    private final JdbcTemplate jdbc;

    public AdminQueryRepository(DataSource dataSource, AdminSecurityProperties properties) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.jdbc.setQueryTimeout(Math.max(1, properties.getConsoleQueryTimeoutSeconds()));
    }

    // ------------------------------------------------------------------ users

    public Rows pageUsers(AdminPage page, String keyword, Integer type, Boolean banned,
                          Boolean hasProfile, String usageDate) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        // The usage date parameter belongs to the JOIN and therefore comes first.
        params.add(usageDate);
        if (keyword != null) {
            where.append(" AND u.username LIKE ? ESCAPE '\\'");
            params.add(like(keyword));
        }
        if (type != null) {
            where.append(" AND u.type = ?");
            params.add(type);
        }
        if (banned != null) {
            where.append(" AND u.is_banned = ?");
            params.add(banned ? 1 : 0);
        }
        if (hasProfile != null) {
            where.append(hasProfile ? " AND p.user_id IS NOT NULL" : " AND p.user_id IS NULL");
        }

        String from = """
                FROM users u
                LEFT JOIN user_profiles p ON p.user_id = u.id
                LEFT JOIN chat_usage_daily cud ON cud.user_id = u.id AND cud.usage_date = ?
                """ + where;
        String select = """
                SELECT u.id AS id, u.username AS username, u.type AS userType,
                       u.is_banned AS banned, u.ban_end_time AS banEndTime, u.avatar AS avatar,
                       %s AS createdAt, %s AS updatedAt,
                       u.password_reset_required AS passwordResetRequired,
                       %s AS tempPasswordExpiresAt,
                       CASE WHEN p.user_id IS NULL THEN 0 ELSE 1 END AS hasProfile,
                       COALESCE(cud.request_count, 0) AS usedToday
                """.formatted(ts("u.created_at"), ts("u.updated_at"),
                ts("u.temp_password_expires_at")) + from;
        return page(page, select, "SELECT COUNT(*) " + from, params);
    }

    public Map<String, Object> findUser(int userId) {
        String sql = """
                SELECT u.id AS id, u.username AS username, u.type AS userType,
                       u.is_banned AS banned, u.ban_end_time AS banEndTime, u.avatar AS avatar,
                       %s AS createdAt, %s AS updatedAt,
                       u.password_reset_required AS passwordResetRequired,
                       %s AS tempPasswordExpiresAt,
                       CASE WHEN p.user_id IS NULL THEN 0 ELSE 1 END AS hasProfile
                FROM users u
                LEFT JOIN user_profiles p ON p.user_id = u.id
                WHERE u.id = ?
                """.formatted(ts("u.created_at"), ts("u.updated_at"),
                ts("u.temp_password_expires_at"));
        return one(sql, userId);
    }

    // --------------------------------------------------------------- profiles

    public Rows pageUserProfiles(AdminPage page, String keyword, String college, String major,
                                 String grade, Integer userId) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (userId != null) {
            where.append(" AND p.user_id = ?");
            params.add(userId);
        }
        if (keyword != null) {
            where.append(" AND (u.username LIKE ? ESCAPE '\\' OR p.major LIKE ? ESCAPE '\\'"
                    + " OR p.college LIKE ? ESCAPE '\\' OR p.student_id LIKE ? ESCAPE '\\')");
            String pattern = like(keyword);
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
        }
        if (college != null) {
            where.append(" AND p.college = ?");
            params.add(college);
        }
        if (major != null) {
            where.append(" AND p.major = ?");
            params.add(major);
        }
        if (grade != null) {
            where.append(" AND p.grade = ?");
            params.add(grade);
        }

        String from = "FROM user_profiles p LEFT JOIN users u ON u.id = p.user_id" + where;
        String select = """
                SELECT p.user_id AS userId, u.username AS username, p.college AS college,
                       p.major AS major, p.grade AS grade, p.student_id AS studentId,
                       p.available_time AS availableTime,
                       p.skills_json AS skillsJson, p.interests_json AS interestsJson,
                       p.experiences_json AS experiencesJson, p.goals AS goalsJson,
                       CASE WHEN p.preferences_json IS NULL OR p.preferences_json IN ('', '{}', 'null')
                            THEN 0 ELSE 1 END AS hasPreferences,
                       %s AS createdAt, %s AS updatedAt
                """.formatted(ts("p.created_at"), ts("p.updated_at")) + from;
        return page(page, select, "SELECT COUNT(*) " + from, params);
    }

    /** Detail view: the only place where the sensitive preferences JSON is returned. */
    public Map<String, Object> findUserProfile(int userId) {
        String sql = """
                SELECT p.user_id AS userId, u.username AS username, u.type AS userType,
                       p.college AS college, p.major AS major, p.grade AS grade,
                       p.student_id AS studentId, p.available_time AS availableTime,
                       p.skills_json AS skillsJson, p.interests_json AS interestsJson,
                       p.experiences_json AS experiencesJson, p.goals AS goalsJson,
                       p.preferences_json AS preferencesJson,
                       %s AS createdAt, %s AS updatedAt
                FROM user_profiles p
                LEFT JOIN users u ON u.id = p.user_id
                WHERE p.user_id = ?
                """.formatted(ts("p.created_at"), ts("p.updated_at"));
        return one(sql, userId);
    }

    // ------------------------------------------------------- planning history

    public Rows pagePlanningHistory(AdminPage page, Integer userId, String type, String status,
                                    String provider, String model, String dateFrom, String dateTo,
                                    int previewLength) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (userId != null) {
            where.append(" AND h.user_id = ?");
            params.add(userId);
        }
        if (type != null) {
            where.append(" AND h.type = ?");
            params.add(type);
        }
        if (status != null) {
            where.append(" AND h.status = ?");
            params.add(status);
        }
        if (provider != null) {
            where.append(" AND h.provider = ?");
            params.add(provider);
        }
        if (model != null) {
            where.append(" AND h.model = ?");
            params.add(model);
        }
        appendDateRange(where, params, "h.created_at", dateFrom, dateTo);

        String from = "FROM planning_history h LEFT JOIN users u ON u.id = h.user_id" + where;
        String select = """
                SELECT h.id AS id, h.user_id AS userId, u.username AS username, h.type AS type,
                       h.provider AS provider, h.model AS model, h.status AS status,
                       substr(h.error_message, 1, %d) AS errorMessage,
                       length(h.error_message) AS errorMessageLength,
                       length(h.request_json) AS requestLength,
                       substr(h.request_json, 1, %d) AS requestPreview,
                       length(h.response_json) AS responseLength,
                       substr(h.response_json, 1, %d) AS responsePreview,
                       %s AS createdAt
                """.formatted(previewLength, previewLength, previewLength, ts("h.created_at")) + from;
        return page(page, select, "SELECT COUNT(*) " + from, params);
    }

    public Map<String, Object> findPlanningHistory(long id) {
        String sql = """
                SELECT h.id AS id, h.user_id AS userId, u.username AS username, h.type AS type,
                       h.provider AS provider, h.model AS model, h.status AS status,
                       h.error_message AS errorMessage,
                       h.request_json AS requestJson, h.response_json AS responseJson,
                       length(h.request_json) AS requestLength,
                       length(h.response_json) AS responseLength,
                       %s AS createdAt
                FROM planning_history h
                LEFT JOIN users u ON u.id = h.user_id
                WHERE h.id = ?
                """.formatted(ts("h.created_at"));
        return one(sql, id);
    }

    // ----------------------------------------------------------- chat messages

    public Rows pageChatMessages(AdminPage page, Integer userId, String role, String dateFrom,
                                 String dateTo, int previewLength) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (userId != null) {
            where.append(" AND m.user_id = ?");
            params.add(userId);
        }
        if (role != null) {
            where.append(" AND m.role = ?");
            params.add(role);
        }
        appendDateRange(where, params, "m.create_time", dateFrom, dateTo);

        String from = "FROM chat_messages m LEFT JOIN users u ON u.id = m.user_id" + where;
        String select = """
                SELECT m.id AS id, m.user_id AS userId, u.username AS username, m.role AS role,
                       length(m.content) AS contentLength,
                       substr(m.content, 1, %d) AS contentPreview,
                       CASE WHEN m.role = 'tool'
                                  OR (m.role = 'assistant' AND substr(trim(m.content), 1, 1) = '{')
                            THEN 1 ELSE 0 END AS structured,
                       %s AS createTime
                """.formatted(previewLength, ts("m.create_time")) + from;
        return page(page, select, "SELECT COUNT(*) " + from, params);
    }

    /** Detail view: full message content (tool-call JSON included) for one message. */
    public Map<String, Object> findChatMessage(long id) {
        String sql = """
                SELECT m.id AS id, m.user_id AS userId, u.username AS username, m.role AS role,
                       m.content AS content, %s AS createTime
                FROM chat_messages m
                LEFT JOIN users u ON u.id = m.user_id
                WHERE m.id = ?
                """.formatted(ts("m.create_time"));
        return one(sql, id);
    }

    // ---------------------------------------------------------------- feedback

    public Rows pageFeedback(AdminPage page, String type, Boolean resolved, Integer userId,
                             String dateFrom, String dateTo, int previewLength) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (type != null) {
            where.append(" AND f.type = ?");
            params.add(type);
        }
        if (resolved != null) {
            where.append(" AND f.resolved = ?");
            params.add(resolved ? 1 : 0);
        }
        if (userId != null) {
            where.append(" AND f.user_id = ?");
            params.add(userId);
        }
        appendDateRange(where, params, "f.create_time", dateFrom, dateTo);

        String from = "FROM feedback f" + where;
        String select = """
                SELECT f.id AS id, f.user_id AS userId, f.username AS username, f.type AS type,
                       f.resolved AS resolved, f.resolved_by AS resolvedBy, f.url AS url,
                       length(f.content) AS contentLength,
                       substr(f.content, 1, %d) AS contentPreview,
                       CASE WHEN f.stack_trace IS NULL OR f.stack_trace = '' THEN 0 ELSE 1 END AS hasStackTrace,
                       length(f.stack_trace) AS stackTraceLength,
                       substr(f.user_agent, 1, 120) AS userAgentPreview,
                       %s AS createTime, %s AS resolvedTime
                """.formatted(previewLength, ts("f.create_time"), ts("f.resolved_time")) + from;
        return page(page, select, "SELECT COUNT(*) " + from, params);
    }

    /** Detail view: diagnostic fields are truncated before leaving the server. */
    public Map<String, Object> findFeedback(long id, int maxDiagnosticLength) {
        String sql = """
                SELECT f.id AS id, f.user_id AS userId, f.username AS username, f.type AS type,
                       f.content AS content, f.url AS url, f.user_agent AS userAgent,
                       substr(f.stack_trace, 1, %d) AS stackTrace,
                       length(f.stack_trace) AS stackTraceLength,
                       f.resolved AS resolved, f.resolved_by AS resolvedBy,
                       %s AS createTime, %s AS resolvedTime
                FROM feedback f
                WHERE f.id = ?
                """.formatted(maxDiagnosticLength, ts("f.create_time"), ts("f.resolved_time"));
        return one(sql, id);
    }

    // -------------------------------------------------------- credit rules

    public Rows pageCollegeCreditRules(AdminPage page, String college, String creditType,
                                       String category, String compLevel, String awardTier,
                                       String keyword, int previewLength) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (college != null) {
            where.append(" AND r.college = ?");
            params.add(college);
        }
        if (creditType != null) {
            where.append(" AND r.credit_type = ?");
            params.add(creditType);
        }
        if (category != null) {
            where.append(" AND r.category = ?");
            params.add(category);
        }
        if (compLevel != null) {
            where.append(" AND r.comp_level = ?");
            params.add(compLevel);
        }
        if (awardTier != null) {
            where.append(" AND r.award_tier = ?");
            params.add(awardTier);
        }
        if (keyword != null) {
            where.append(" AND (r.comp_name LIKE ? ESCAPE '\\' OR r.doc_source LIKE ? ESCAPE '\\')");
            params.add(like(keyword));
            params.add(like(keyword));
        }

        String from = "FROM college_credit_rules r" + where;
        String select = """
                SELECT r.id AS id, r.college AS college, r.credit_type AS creditType,
                       r.category AS category, r.comp_level AS compLevel, r.comp_name AS compName,
                       r.award_tier AS awardTier, r.credits AS credits,
                       r.category_cap AS categoryCap, r.team_formula AS teamFormula,
                       r.student_cohort AS studentCohort, r.doc_source AS docSource,
                       r.levels_json AS levelsJson,
                       substr(r.notes, 1, %d) AS notesPreview, length(r.notes) AS notesLength,
                       %s AS createdAt
                """.formatted(previewLength, ts("r.created_at")) + from;
        return page(page, select, "SELECT COUNT(*) " + from, params);
    }

    public Map<String, Object> findCollegeCreditRule(long id) {
        String sql = """
                SELECT r.id AS id, r.college AS college, r.credit_type AS creditType,
                       r.category AS category, r.comp_level AS compLevel, r.comp_name AS compName,
                       r.award_tier AS awardTier, r.credits AS credits,
                       r.category_cap AS categoryCap, r.team_formula AS teamFormula,
                       r.student_cohort AS studentCohort, r.doc_source AS docSource,
                       r.levels_json AS levelsJson, r.notes AS notes, %s AS createdAt
                FROM college_credit_rules r
                WHERE r.id = ?
                """.formatted(ts("r.created_at"));
        return one(sql, id);
    }

    // --------------------------------------------------- credit activities

    public Rows pageCreditActivities(AdminPage page, Integer userId, String creditType,
                                     Boolean verified, int previewLength) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (userId != null) {
            where.append(" AND a.user_id = ?");
            params.add(userId);
        }
        if (creditType != null) {
            where.append(" AND a.credit_type = ?");
            params.add(creditType);
        }
        if (verified != null) {
            where.append(" AND a.verified = ?");
            params.add(verified ? 1 : 0);
        }

        String from = "FROM credit_activities a LEFT JOIN users u ON u.id = a.user_id" + where;
        // certificate_ref is never returned: only whether a certificate exists.
        String select = """
                SELECT a.id AS id, a.user_id AS userId, u.username AS username,
                       a.rule_id AS ruleId, a.credit_type AS creditType, a.category AS category,
                       a.comp_name AS compName, a.comp_level AS compLevel, a.award_tier AS awardTier,
                       a.credits AS credits, a.obtained_date AS obtainedDate, a.verified AS verified,
                       CASE WHEN a.certificate_ref IS NULL OR a.certificate_ref = '' THEN 0 ELSE 1 END
                           AS hasCertificate,
                       substr(a.notes, 1, %d) AS notesPreview, length(a.notes) AS notesLength,
                       %s AS createdAt
                """.formatted(previewLength, ts("a.created_at")) + from;
        return page(page, select, "SELECT COUNT(*) " + from, params);
    }

    public Map<String, Object> findCreditActivity(long id) {
        String sql = """
                SELECT a.id AS id, a.user_id AS userId, u.username AS username,
                       a.rule_id AS ruleId, a.credit_type AS creditType, a.category AS category,
                       a.comp_name AS compName, a.comp_level AS compLevel, a.award_tier AS awardTier,
                       a.credits AS credits, a.obtained_date AS obtainedDate, a.verified AS verified,
                       CASE WHEN a.certificate_ref IS NULL OR a.certificate_ref = '' THEN 0 ELSE 1 END
                           AS hasCertificate,
                       a.notes AS notes, %s AS createdAt
                FROM credit_activities a
                LEFT JOIN users u ON u.id = a.user_id
                WHERE a.id = ?
                """.formatted(ts("a.created_at"));
        return one(sql, id);
    }

    // --------------------------------------------------------------- resources

    public Rows pageResources(AdminPage page, String type, String difficulty, String dataQuality,
                              String provider, String keyword, int previewLength) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (type != null) {
            where.append(" AND r.type = ?");
            params.add(type);
        }
        if (difficulty != null) {
            where.append(" AND r.difficulty = ?");
            params.add(difficulty);
        }
        if (dataQuality != null) {
            where.append(" AND r.data_quality = ?");
            params.add(dataQuality);
        }
        if (provider != null) {
            where.append(" AND r.provider = ?");
            params.add(provider);
        }
        if (keyword != null) {
            where.append(" AND (r.name LIKE ? ESCAPE '\\' OR r.resource_id LIKE ? ESCAPE '\\')");
            params.add(like(keyword));
            params.add(like(keyword));
        }

        String from = "FROM resources r" + where;
        String select = """
                SELECT r.id AS id, r.resource_id AS resourceId, r.name AS name, r.type AS type,
                       r.difficulty AS difficulty, r.data_quality AS dataQuality,
                       r.provider AS provider, r.source_url AS sourceUrl,
                       r.registration_start AS registrationStart,
                       r.registration_deadline AS registrationDeadline,
                       r.preparation_period AS preparationPeriod, r.levels_json AS levelsJson,
                       r.target_majors_json AS targetMajorsJson, r.updated_at AS updatedAt,
                       length(r.description) AS descriptionLength,
                       substr(r.description, 1, %d) AS descriptionPreview,
                       %s AS createdAt
                """.formatted(previewLength, ts("r.created_at")) + from;
        return page(page, select, "SELECT COUNT(*) " + from, params);
    }

    public Map<String, Object> findResource(long id) {
        String sql = """
                SELECT r.id AS id, r.resource_id AS resourceId, r.name AS name, r.type AS type,
                       r.difficulty AS difficulty, r.data_quality AS dataQuality,
                       r.provider AS provider, r.course_link AS courseLink,
                       r.description AS description, r.source_url AS sourceUrl,
                       r.source_urls_json AS sourceUrlsJson, r.source_file AS sourceFile,
                       r.registration_start AS registrationStart,
                       r.registration_deadline AS registrationDeadline,
                       r.preparation_period AS preparationPeriod,
                       r.levels_json AS levelsJson, r.stages_json AS stagesJson,
                       r.target_majors_json AS targetMajorsJson,
                       r.required_skills_json AS requiredSkillsJson,
                       r.team_roles_json AS teamRolesJson, r.bonus_point_json AS bonusPointJson,
                       r.teaches_skills_json AS teachesSkillsJson, r.notes_json AS notesJson,
                       r.updated_at AS updatedAt, %s AS createdAt
                FROM resources r
                WHERE r.id = ?
                """.formatted(ts("r.created_at"));
        return one(sql, id);
    }

    // -------------------------------------------------------------- rag chunks

    public Rows pageRagChunks(AdminPage page, String embeddingStatus, String sourceType,
                              String relatedResourceId, String keyword, int previewLength) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (embeddingStatus != null) {
            where.append(" AND c.embedding_status = ?");
            params.add(embeddingStatus);
        }
        if (sourceType != null) {
            where.append(" AND c.source_type = ?");
            params.add(sourceType);
        }
        if (relatedResourceId != null) {
            where.append(" AND c.related_resource_id = ?");
            params.add(relatedResourceId);
        }
        if (keyword != null) {
            where.append(" AND (c.title LIKE ? ESCAPE '\\' OR c.chunk_id LIKE ? ESCAPE '\\')");
            params.add(like(keyword));
            params.add(like(keyword));
        }

        String from = "FROM rag_chunks c" + where;
        // embedding_json itself is never returned — only its byte length.
        String select = """
                SELECT c.chunk_id AS chunkId, c.title AS title, c.source_type AS sourceType,
                       c.source_url AS sourceUrl, c.source_file AS sourceFile,
                       c.page_or_section AS pageOrSection,
                       c.related_resource_id AS relatedResourceId, c.created_at AS createdAt,
                       c.embedding_model AS embeddingModel,
                       c.embedding_dimensions AS embeddingDimensions,
                       c.embedding_status AS embeddingStatus,
                       substr(c.embedding_error, 1, %d) AS embeddingError,
                       length(c.embedding_error) AS embeddingErrorLength,
                       c.embedding_updated_at AS embeddingUpdatedAt, c.content_hash AS contentHash,
                       CASE WHEN c.embedding_json IS NULL OR c.embedding_json = ''
                            THEN 0 ELSE length(c.embedding_json) END AS embeddingJsonLength,
                       length(c.text) AS textLength,
                       substr(c.text, 1, %d) AS textPreview
                """.formatted(previewLength, previewLength) + from;
        return page(page, select, "SELECT COUNT(*) " + from, params);
    }

    public Map<String, Object> findRagChunk(String chunkId, int maxTextLength) {
        String sql = """
                SELECT c.chunk_id AS chunkId, c.title AS title, c.text AS text,
                       c.source_type AS sourceType, c.source_url AS sourceUrl,
                       c.source_file AS sourceFile, c.page_or_section AS pageOrSection,
                       c.related_resource_id AS relatedResourceId, c.created_at AS createdAt,
                       c.embedding_model AS embeddingModel,
                       c.embedding_dimensions AS embeddingDimensions,
                       c.embedding_status AS embeddingStatus,
                       substr(c.embedding_error, 1, %d) AS embeddingError,
                       c.embedding_updated_at AS embeddingUpdatedAt, c.content_hash AS contentHash,
                       CASE WHEN c.embedding_json IS NULL OR c.embedding_json = ''
                            THEN 0 ELSE length(c.embedding_json) END AS embeddingJsonLength
                FROM rag_chunks c
                WHERE c.chunk_id = ?
                """.formatted(maxTextLength);
        return one(sql, chunkId);
    }

    // -------------------------------------------------------- capability dictionary

    public Rows pageCapabilityTags(AdminPage page, String category, String keyword,
                                   int previewLength) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (category != null) {
            where.append(" AND t.category = ?");
            params.add(category);
        }
        if (keyword != null) {
            where.append(" AND (t.name LIKE ? ESCAPE '\\' OR t.skill_aliases_json LIKE ? ESCAPE '\\')");
            params.add(like(keyword));
            params.add(like(keyword));
        }

        String from = "FROM capability_tags t" + where;
        String select = """
                SELECT t.name AS name, t.category AS category,
                       t.level1_desc AS level1Desc, t.level2_desc AS level2Desc,
                       t.level3_desc AS level3Desc,
                       length(t.skill_aliases_json) AS skillAliasesLength,
                       substr(t.skill_aliases_json, 1, %d) AS skillAliasesPreview,
                       length(t.typical_evidence_json) AS typicalEvidenceLength,
                       substr(t.typical_evidence_json, 1, %d) AS typicalEvidencePreview,
                       %s AS createdAt, %s AS updatedAt
                """.formatted(previewLength, previewLength,
                ts("t.created_at"), ts("t.updated_at")) + from;
        return page(page, select, "SELECT COUNT(*) " + from, params);
    }

    public Map<String, Object> findCapabilityTag(String name) {
        String sql = """
                SELECT t.name AS name, t.category AS category,
                       t.level1_desc AS level1Desc, t.level2_desc AS level2Desc,
                       t.level3_desc AS level3Desc,
                       t.skill_aliases_json AS skillAliasesJson,
                       t.typical_evidence_json AS typicalEvidenceJson,
                       %s AS createdAt, %s AS updatedAt
                FROM capability_tags t
                WHERE t.name = ?
                """.formatted(ts("t.created_at"), ts("t.updated_at"));
        return one(sql, name);
    }

    public Rows pageCapabilityReference(AdminPage page, String section, String keyword,
                                        int previewLength) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (section != null) {
            where.append(" AND r.section = ?");
            params.add(section);
        }
        if (keyword != null) {
            where.append(" AND r.ref_key LIKE ? ESCAPE '\\'");
            params.add(like(keyword));
        }

        String from = "FROM capability_reference r" + where;
        String select = """
                SELECT r.id AS id, r.section AS section, r.ref_key AS refKey,
                       length(r.ref_value) AS refValueLength,
                       substr(r.ref_value, 1, %d) AS refValuePreview, r.note AS note
                """.formatted(previewLength) + from;
        return page(page, select, "SELECT COUNT(*) " + from, params);
    }

    public Map<String, Object> findCapabilityReference(long id) {
        String sql = """
                SELECT r.id AS id, r.section AS section, r.ref_key AS refKey,
                       r.ref_value AS refValue, r.note AS note
                FROM capability_reference r
                WHERE r.id = ?
                """;
        return one(sql, id);
    }

    // ------------------------------------------------------------ chat usage

    public Rows pageChatUsage(AdminPage page, Integer userId, String usageDate,
                             String dateFrom, String dateTo) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (userId != null) {
            where.append(" AND d.user_id = ?");
            params.add(userId);
        }
        if (usageDate != null) {
            where.append(" AND d.usage_date = ?");
            params.add(usageDate);
        }
        if (dateFrom != null) {
            where.append(" AND d.usage_date >= ?");
            params.add(dateFrom);
        }
        if (dateTo != null) {
            where.append(" AND d.usage_date <= ?");
            params.add(dateTo);
        }

        String from = "FROM chat_usage_daily d LEFT JOIN users u ON u.id = d.user_id" + where;
        String select = """
                SELECT d.id AS id, d.user_id AS userId, u.username AS username,
                       d.usage_date AS usageDate, d.request_count AS requestCount,
                       %s AS updatedAt
                """.formatted(ts("d.updated_at")) + from;
        return page(page, select, "SELECT COUNT(*) " + from, params);
    }

    // -------------------------------------------------------------- dashboard

    /** Row counts for the 12 business tables shown on the console overview. */
    public Map<String, Long> tableCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String table : List.of(
                "users", "feedback", "user_profiles", "planning_history", "chat_messages",
                "college_credit_rules", "credit_activities", "resources", "rag_chunks",
                "capability_tags", "capability_reference", "chat_usage_daily")) {
            Long count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
            counts.put(table, count == null ? 0L : count);
        }
        return counts;
    }

    public Map<String, Long> userStats() {
        Map<String, Long> stats = new LinkedHashMap<>();
        stats.put("total", count("SELECT COUNT(*) FROM users"));
        stats.put("admins", count("SELECT COUNT(*) FROM users WHERE type = 2"));
        stats.put("banned", count("SELECT COUNT(*) FROM users WHERE is_banned = 1"));
        stats.put("withProfile", count("""
                SELECT COUNT(*) FROM users u
                WHERE EXISTS (SELECT 1 FROM user_profiles p WHERE p.user_id = u.id)
                """));
        return stats;
    }

    public Map<String, Long> chatUsageToday(String usageDate) {
        Map<String, Long> stats = new LinkedHashMap<>();
        stats.put("requests", count(
                "SELECT COALESCE(SUM(request_count), 0) FROM chat_usage_daily WHERE usage_date = ?",
                usageDate));
        stats.put("activeUsers", count(
                "SELECT COUNT(*) FROM chat_usage_daily WHERE usage_date = ? AND request_count > 0",
                usageDate));
        return stats;
    }

    public Map<String, Long> feedbackStats() {
        Map<String, Long> stats = new LinkedHashMap<>();
        stats.put("total", count("SELECT COUNT(*) FROM feedback"));
        stats.put("unresolved", count("SELECT COUNT(*) FROM feedback WHERE resolved = 0"));
        stats.put("unresolvedSystemErrors", count(
                "SELECT COUNT(*) FROM feedback WHERE resolved = 0 AND type = 'system'"));
        return stats;
    }

    public Map<String, Long> planningStats() {
        Map<String, Long> stats = new LinkedHashMap<>();
        stats.put("total", count("SELECT COUNT(*) FROM planning_history"));
        stats.put("failed", count("SELECT COUNT(*) FROM planning_history WHERE status = 'FAILED'"));
        return stats;
    }

    /** Most recent failed LLM interactions, without request/response payloads. */
    public List<Map<String, Object>> recentLlmFailures(int limit, int previewLength) {
        return jdbc.queryForList("""
                SELECT h.id AS id, h.user_id AS userId, h.type AS type, h.provider AS provider,
                       h.model AS model, h.status AS status,
                       substr(h.error_message, 1, %d) AS errorMessage,
                       %s AS createdAt
                FROM planning_history h
                WHERE h.status = 'FAILED'
                ORDER BY h.id DESC
                LIMIT ?
                """.formatted(previewLength, ts("h.created_at")), limit);
    }

    public List<Map<String, Object>> embeddingStatusDistribution() {
        return jdbc.queryForList("""
                SELECT COALESCE(c.embedding_status, 'NONE') AS status, COUNT(*) AS count
                FROM rag_chunks c
                GROUP BY COALESCE(c.embedding_status, 'NONE')
                ORDER BY count DESC
                """);
    }

    public List<Map<String, Object>> topChatUsageToday(String usageDate, int limit) {
        return jdbc.queryForList("""
                SELECT d.user_id AS userId, u.username AS username, d.request_count AS requestCount
                FROM chat_usage_daily d
                LEFT JOIN users u ON u.id = d.user_id
                WHERE d.usage_date = ?
                ORDER BY d.request_count DESC
                LIMIT ?
                """, usageDate, limit);
    }

    // ------------------------------------------------------------- primitives

    private Rows page(AdminPage page, String selectSql, String countSql, List<Object> params) {
        Long total = jdbc.queryForObject(countSql, Long.class, params.toArray());
        List<Object> selectParams = new ArrayList<>(params);
        selectParams.add(page.limit());
        selectParams.add(page.offset());
        List<Map<String, Object>> items = jdbc.queryForList(
                selectSql + " ORDER BY " + safeOrderBy(page.orderBy())
                        + " LIMIT ? OFFSET ?", selectParams.toArray());
        return new Rows(items, total == null ? 0L : total);
    }

    private Map<String, Object> one(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }

    /**
     * Defence in depth: the service already resolved the ORDER BY from a
     * whitelist, this rejects anything that is not "identifier [ASC|DESC]".
     */
    static String safeOrderBy(String orderBy) {
        if (orderBy == null || orderBy.isBlank()) {
            return "id ASC";
        }
        String trimmed = orderBy.trim();
        if (!trimmed.matches("[A-Za-z_][A-Za-z0-9_.]{0,60}(?i:\\s+(ASC|DESC))?")) {
            return "id ASC";
        }
        return trimmed;
    }

    private void appendDateRange(StringBuilder where, List<Object> params, String column,
                                 String dateFrom, String dateTo) {
        if (dateFrom != null) {
            where.append(" AND ").append(day(column)).append(" >= ?");
            params.add(dateFrom);
        }
        if (dateTo != null) {
            where.append(" AND ").append(day(column)).append(" <= ?");
            params.add(dateTo);
        }
    }

    /** Epoch-millis and text timestamps both normalise to ISO-8601 UTC text. */
    private static String ts(String column) {
        return "CASE WHEN typeof(" + column + ") IN ('integer', 'real') "
                + "THEN strftime('%Y-%m-%dT%H:%M:%SZ', " + column + " / 1000, 'unixepoch') "
                + "ELSE " + column + " END";
    }

    /** Epoch-millis and text timestamps both normalise to a YYYY-MM-DD day. */
    private static String day(String column) {
        return "CASE WHEN typeof(" + column + ") IN ('integer', 'real') "
                + "THEN date(" + column + " / 1000, 'unixepoch') "
                + "ELSE date(" + column + ") END";
    }

    private static String like(String keyword) {
        return "%" + keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
