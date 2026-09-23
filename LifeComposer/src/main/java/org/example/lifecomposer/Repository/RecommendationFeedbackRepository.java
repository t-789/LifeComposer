package org.example.lifecomposer.Repository;

import org.example.lifecomposer.Entity.RecommendationFeedback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class RecommendationFeedbackRepository {

    private final JdbcTemplate jdbcTemplate;

    public RecommendationFeedbackRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<RecommendationFeedback> ROW_MAPPER = new RowMapper<>() {
        @Override
        public RecommendationFeedback mapRow(ResultSet rs, int rowNum) throws SQLException {
            RecommendationFeedback feedback = new RecommendationFeedback();
            feedback.setId(rs.getLong("id"));
            feedback.setUserId(rs.getLong("user_id"));
            feedback.setDirectionId(rs.getString("direction_id"));
            feedback.setFeedbackType(rs.getString("feedback_type"));
            feedback.setNote(rs.getString("note"));
            feedback.setScoringVersion(rs.getString("scoring_version"));
            feedback.setRecommendationSnapshotJson(rs.getString("recommendation_snapshot_json"));
            feedback.setCreatedAt(rs.getString("created_at"));
            return feedback;
        }
    };

    public void createTableIfNeeded() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS recommendation_feedback (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id INTEGER NOT NULL,
                    direction_id TEXT NOT NULL,
                    feedback_type TEXT NOT NULL
                        CHECK(feedback_type IN ('useful', 'irrelevant', 'too_hard', 'time_mismatch', 'goal_changed')),
                    note TEXT,
                    scoring_version TEXT,
                    recommendation_snapshot_json TEXT,
                    created_at TEXT NOT NULL DEFAULT (datetime('now'))
                )
                """);
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_recommendation_feedback_user "
                + "ON recommendation_feedback(user_id, created_at)");
    }

    public void insert(RecommendationFeedback feedback) {
        jdbcTemplate.update("""
                INSERT INTO recommendation_feedback
                    (user_id, direction_id, feedback_type, note, scoring_version,
                     recommendation_snapshot_json, created_at)
                VALUES (?, ?, ?, ?, ?, ?, datetime('now'))
                """,
                feedback.getUserId(), feedback.getDirectionId(), feedback.getFeedbackType(),
                feedback.getNote(), feedback.getScoringVersion(), feedback.getRecommendationSnapshotJson());
    }

    public List<RecommendationFeedback> findByUserId(Long userId) {
        return jdbcTemplate.query(
                "SELECT * FROM recommendation_feedback WHERE user_id = ? ORDER BY id DESC",
                ROW_MAPPER, userId);
    }
}
