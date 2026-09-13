package org.example.lifecomposer.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.lifecomposer.Repository.CapabilityReferenceRepository;
import org.example.lifecomposer.Repository.CapabilityTagRepository;
import org.example.lifecomposer.Repository.ChatMessageRepository;
import org.example.lifecomposer.Repository.CollegeCreditRuleRepository;
import org.example.lifecomposer.Repository.CreditActivityRepository;
import org.example.lifecomposer.Repository.FeedbackRepository;
import org.example.lifecomposer.Repository.PlanningHistoryRepository;
import org.example.lifecomposer.Repository.RagChunkRepository;
import org.example.lifecomposer.Repository.ResourceRepository;
import org.example.lifecomposer.Repository.UserProfileRepository;
import org.example.lifecomposer.Repository.UserRepository;
import org.example.lifecomposer.Entity.User;
import org.example.lifecomposer.Entity.UserType;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Component
public class DatabaseInitializer {

    private static final Logger LOG = LogManager.getLogger(DatabaseInitializer.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JdbcTemplate jdbcTemplate;
    private final UserRepository userRepository;
    private final FeedbackRepository feedbackRepository;
    private final CollegeCreditRuleRepository collegeCreditRuleRepository;
    private final UserProfileRepository userProfileRepository;
    private final CreditActivityRepository creditActivityRepository;
    private final PlanningHistoryRepository planningHistoryRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ResourceRepository resourceRepository;
    private final RagChunkRepository ragChunkRepository;
    private final CapabilityTagRepository capabilityTagRepository;
    private final CapabilityReferenceRepository capabilityReferenceRepository;
    private final PasswordEncoder passwordEncoder;
    private final boolean importMode;

    public DatabaseInitializer(JdbcTemplate jdbcTemplate,
                               UserRepository userRepository,
                               FeedbackRepository feedbackRepository,
                               ChatMessageRepository chatMessageRepository,
                               UserProfileRepository userProfileRepository,
                               CollegeCreditRuleRepository collegeCreditRuleRepository,
                               CreditActivityRepository creditActivityRepository,
                               PlanningHistoryRepository planningHistoryRepository,
                               ResourceRepository resourceRepository,
                               RagChunkRepository ragChunkRepository,
                               CapabilityTagRepository capabilityTagRepository,
                               CapabilityReferenceRepository capabilityReferenceRepository,
                               PasswordEncoder passwordEncoder,
                               Environment environment) {
        this.jdbcTemplate = jdbcTemplate;
        this.userRepository = userRepository;
        this.feedbackRepository = feedbackRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.userProfileRepository = userProfileRepository;
        this.collegeCreditRuleRepository = collegeCreditRuleRepository;
        this.creditActivityRepository = creditActivityRepository;
        this.planningHistoryRepository = planningHistoryRepository;
        this.resourceRepository = resourceRepository;
        this.ragChunkRepository = ragChunkRepository;
        this.capabilityTagRepository = capabilityTagRepository;
        this.capabilityReferenceRepository = capabilityReferenceRepository;
        this.passwordEncoder = passwordEncoder;
        this.importMode = environment.getProperty("lifecomposer.import.mode", Boolean.class, false);
    }

    @PostConstruct
    public void init() {
        // Table creation follows SCHEMA.md 建表顺序 (dependency order).
        userRepository.createUserTableIfNeeded();
        userRepository.migrateUserSchema();
        feedbackRepository.createFeedbackTableIfNeeded();
        collegeCreditRuleRepository.createTableIfNeeded();
        userProfileRepository.createTableIfNeeded();
        userProfileRepository.migrateUserSchema();
        creditActivityRepository.createTableIfNeeded();
        planningHistoryRepository.createTableIfNeeded();
        chatMessageRepository.createChatMessageTableIfNeeded();
        resourceRepository.createTableIfNeeded();
        ragChunkRepository.createTableIfNeeded();
        capabilityTagRepository.createTableIfNeeded();
        capabilityReferenceRepository.createTableIfNeeded();

        migrateLegacyGoals();

        createDefaultAdminIfMissing();
    }

    /**
     * Guarded, idempotent one-time migration: merge rows still sitting in the
     * legacy `goals` table into `user_profiles.goals` (a JSON array string of
     * goal titles, per SCHEMA.md), then drop the legacy table. Soft-deleted
     * (ARCHIVED) rows are not migrated because they were already hidden from
     * the user via the old goals API. Guarding on table existence makes this a
     * no-op on fresh databases and on every start after the first successful
     * run; if it ever fails part-way, the next start retries without
     * duplicating titles (titles are de-duplicated against the profile's
     * existing JSON).
     */
    private void migrateLegacyGoals() {
        if (!tableExists("goals")) {
            return;
        }
        try {
            Map<Long, List<String>> titlesByUser = new LinkedHashMap<>();
            jdbcTemplate.query("""
                            SELECT user_id, title
                            FROM goals
                            WHERE status != 'ARCHIVED'
                              AND EXISTS (SELECT 1 FROM users WHERE users.id = goals.user_id)
                            ORDER BY user_id, id
                            """,
                    rs -> {
                        String title = rs.getString("title");
                        if (title != null && !title.isBlank()) {
                            titlesByUser
                                    .computeIfAbsent(rs.getLong("user_id"), k -> new ArrayList<>())
                                    .add(title.trim());
                        }
                    });
            for (Map.Entry<Long, List<String>> entry : titlesByUser.entrySet()) {
                mergeLegacyGoalsIntoProfile(entry.getKey(), entry.getValue());
            }
            jdbcTemplate.execute("DROP TABLE goals");
            LOG.info("Legacy goals table migrated into user_profiles.goals and dropped");
        } catch (RuntimeException e) {
            LOG.warn("Legacy goals migration failed and will be retried on next startup", e);
        }
    }

    private void mergeLegacyGoalsIntoProfile(Long userId, List<String> legacyTitles) {
        List<String> existingColumn = jdbcTemplate.query(
                "SELECT goals FROM user_profiles WHERE user_id = ?",
                (rs, rowNum) -> rs.getString("goals"), userId);
        boolean profileExists = !existingColumn.isEmpty();
        String existingJson = profileExists ? existingColumn.get(0) : null;

        LinkedHashSet<String> merged = new LinkedHashSet<>();
        merged.addAll(parseGoalsJson(existingJson));
        merged.addAll(legacyTitles);

        String goalsJson = toGoalsJson(merged);
        if (profileExists) {
            jdbcTemplate.update(
                    "UPDATE user_profiles SET goals = ?, updated_at = datetime('now') WHERE user_id = ?",
                    goalsJson, userId);
        } else {
            jdbcTemplate.update("""
                            INSERT INTO user_profiles (user_id, goals, created_at, updated_at)
                            VALUES (?, ?, datetime('now'), datetime('now'))
                            """,
                    userId, goalsJson);
        }
    }

    private List<String> parseGoalsJson(String goalsJson) {
        if (goalsJson == null || goalsJson.isBlank()) {
            return List.of();
        }
        try {
            JsonNode node = MAPPER.readTree(goalsJson);
            if (node != null && node.isArray()) {
                List<String> titles = new ArrayList<>();
                for (JsonNode item : node) {
                    if (item != null && item.isTextual() && !item.asText().isBlank()) {
                        titles.add(item.asText().trim());
                    }
                }
                return titles;
            }
            // Not a JSON array (e.g. legacy free text): keep the raw value as a
            // single goal title so nothing is lost during the merge.
            return List.of(goalsJson.trim());
        } catch (JsonProcessingException e) {
            return List.of(goalsJson.trim());
        }
    }

    private String toGoalsJson(Collection<String> titles) {
        try {
            return MAPPER.writeValueAsString(titles);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize merged goals for user_profiles", e);
        }
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = ?",
                Integer.class, tableName);
        return count != null && count > 0;
    }

    private void createDefaultAdminIfMissing() {
        if (importMode) {
            LOG.info("Import mode active: skipping default admin bootstrap");
            return;
        }
        if (userRepository.countAdminUsers() > 0) {
            return;
        }

        User admin = new User();
        admin.setUsername("admin");
        admin.setPasswordHash(passwordEncoder.encode("admin"));
        admin.setType(UserType.ADMIN);
        admin.setBanned(false);

        userRepository.insertUser(admin);
    }
}
