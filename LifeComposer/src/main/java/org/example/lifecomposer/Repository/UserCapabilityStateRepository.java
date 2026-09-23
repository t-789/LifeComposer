package org.example.lifecomposer.Repository;

import org.example.lifecomposer.Entity.UserCapabilityState;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Repository
public class UserCapabilityStateRepository {

    private final JdbcTemplate jdbcTemplate;

    public UserCapabilityStateRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<UserCapabilityState> ROW_MAPPER = new RowMapper<>() {
        @Override
        public UserCapabilityState mapRow(ResultSet rs, int rowNum) throws SQLException {
            UserCapabilityState state = new UserCapabilityState();
            state.setId(rs.getLong("id"));
            state.setUserId(rs.getLong("user_id"));
            state.setTagName(rs.getString("tag_name"));
            state.setLevel(rs.getString("level"));
            state.setEvidenceJson(rs.getString("evidence_json"));
            state.setSource(rs.getString("source"));
            double confidence = rs.getDouble("confidence");
            state.setConfidence(rs.wasNull() ? null : confidence);
            state.setCreatedAt(rs.getString("created_at"));
            state.setUpdatedAt(rs.getString("updated_at"));
            return state;
        }
    };

    public void createTableIfNeeded() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS user_capability_states (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id INTEGER NOT NULL,
                    tag_name TEXT NOT NULL,
                    level TEXT,
                    evidence_json TEXT,
                    source TEXT NOT NULL,
                    confidence REAL,
                    created_at TEXT NOT NULL DEFAULT (datetime('now')),
                    updated_at TEXT NOT NULL DEFAULT (datetime('now')),
                    UNIQUE(user_id, tag_name)
                )
                """);
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_user_capability_user "
                + "ON user_capability_states(user_id)");
    }

    public void upsert(UserCapabilityState state) {
        jdbcTemplate.update("""
                INSERT INTO user_capability_states
                    (user_id, tag_name, level, evidence_json, source, confidence, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, datetime('now'), datetime('now'))
                ON CONFLICT(user_id, tag_name) DO UPDATE SET
                    level = excluded.level,
                    evidence_json = excluded.evidence_json,
                    source = excluded.source,
                    confidence = excluded.confidence,
                    updated_at = datetime('now')
                """,
                state.getUserId(), state.getTagName(), state.getLevel(), state.getEvidenceJson(),
                state.getSource(), state.getConfidence());
    }

    /**
     * Removes capability rows that are no longer part of the current profile.
     * An empty {@code keptTags} clears every row for the user.
     */
    public int deleteByUserIdAndTagNotIn(Long userId, Collection<String> keptTags) {
        if (userId == null) {
            return 0;
        }
        if (keptTags == null || keptTags.isEmpty()) {
            return jdbcTemplate.update("DELETE FROM user_capability_states WHERE user_id = ?", userId);
        }
        List<String> tags = new ArrayList<>(keptTags);
        String placeholders = String.join(",", java.util.Collections.nCopies(tags.size(), "?"));
        List<Object> args = new ArrayList<>();
        args.add(userId);
        args.addAll(tags);
        return jdbcTemplate.update(
                "DELETE FROM user_capability_states WHERE user_id = ? AND tag_name NOT IN ("
                        + placeholders + ")",
                args.toArray());
    }

    public List<UserCapabilityState> findByUserId(Long userId) {
        return jdbcTemplate.query(
                "SELECT * FROM user_capability_states WHERE user_id = ? ORDER BY tag_name",
                ROW_MAPPER, userId);
    }
}
