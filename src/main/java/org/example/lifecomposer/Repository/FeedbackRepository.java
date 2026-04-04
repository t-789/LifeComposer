package org.example.lifecomposer.Repository;

import org.example.lifecomposer.entity.Feedback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

@Repository
public class FeedbackRepository {

    private final JdbcTemplate jdbcTemplate;

    public FeedbackRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<Feedback> FEEDBACK_ROW_MAPPER = new RowMapper<>() {
        @Override
        public Feedback mapRow(ResultSet rs, int rowNum) throws SQLException {
            Feedback feedback = new Feedback();
            feedback.setId(rs.getInt("id"));
            feedback.setUserId(rs.getObject("user_id", Integer.class));
            feedback.setUsername(rs.getString("username"));
            feedback.setContent(rs.getString("content"));
            feedback.setType(rs.getString("type"));
            feedback.setUrl(rs.getString("url"));
            feedback.setUserAgent(rs.getString("user_agent"));
            feedback.setStackTrace(rs.getString("stack_trace"));
            feedback.setCreateTime(rs.getTimestamp("create_time"));
            feedback.setResolved(rs.getBoolean("resolved"));
            feedback.setResolvedBy(rs.getString("resolved_by"));
            feedback.setResolvedTime(rs.getTimestamp("resolved_time"));
            return feedback;
        }
    };

    public void createFeedbackTableIfNeeded() {
        String sql = """
                CREATE TABLE IF NOT EXISTS feedback (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  user_id INTEGER NULL,
                  username TEXT NULL,
                  content TEXT NOT NULL,
                  type TEXT NOT NULL,
                  url TEXT NULL,
                  user_agent TEXT NULL,
                  stack_trace TEXT NULL,
                  create_time TIMESTAMP NOT NULL,
                  resolved BOOLEAN NOT NULL DEFAULT 0,
                  resolved_by TEXT NULL,
                  resolved_time TIMESTAMP NULL
                )
                """;
        jdbcTemplate.execute(sql);
    }

    public boolean saveFeedback(Feedback feedback) {
        String sql = """
                INSERT INTO feedback(user_id, username, content, type, url, user_agent, stack_trace, create_time, resolved)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        int rows = jdbcTemplate.update(sql,
                feedback.getUserId(),
                feedback.getUsername(),
                feedback.getContent(),
                feedback.getType(),
                feedback.getUrl(),
                feedback.getUserAgent(),
                feedback.getStackTrace(),
                feedback.getCreateTime(),
                Boolean.TRUE.equals(feedback.getResolved())
        );
        return rows > 0;
    }

    public List<Feedback> getAllFeedback() {
        return jdbcTemplate.query("SELECT * FROM feedback ORDER BY create_time DESC", FEEDBACK_ROW_MAPPER);
    }

    public List<Feedback> getFeedbackByType(String type) {
        return jdbcTemplate.query("SELECT * FROM feedback WHERE type = ? ORDER BY create_time DESC", FEEDBACK_ROW_MAPPER, type);
    }

    public boolean updateResolvedStatus(int feedbackId, boolean resolved, String resolvedBy) {
        Timestamp resolvedTime = resolved ? new Timestamp(System.currentTimeMillis()) : null;
        int rows = jdbcTemplate.update(
                "UPDATE feedback SET resolved = ?, resolved_by = ?, resolved_time = ? WHERE id = ?",
                resolved,
                resolvedBy,
                resolvedTime,
                feedbackId
        );
        return rows > 0;
    }
}
