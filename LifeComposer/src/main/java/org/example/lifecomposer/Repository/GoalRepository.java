package org.example.lifecomposer.Repository;

import org.example.lifecomposer.Entity.Goal;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class GoalRepository {

    private final JdbcTemplate jdbcTemplate;

    public GoalRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<Goal> GOAL_ROW_MAPPER = new RowMapper<>() {
        @Override
        public Goal mapRow(ResultSet rs, int rowNum) throws SQLException {
            Goal goal = new Goal();
            goal.setId(rs.getLong("id"));
            goal.setUserId(rs.getLong("user_id"));
            goal.setTitle(rs.getString("title"));
            goal.setDescription(rs.getString("description"));
            goal.setCategory(rs.getString("category"));
            goal.setPriority(rs.getString("priority"));
            goal.setStatus(rs.getString("status"));
            goal.setTargetDate(rs.getString("target_date"));
            goal.setProgress(rs.getInt("progress"));
            goal.setCreatedAt(rs.getString("created_at"));
            goal.setUpdatedAt(rs.getString("updated_at"));
            return goal;
        }
    };

    public void createTableIfNeeded() {
        String sql = """
                CREATE TABLE IF NOT EXISTS goals (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id INTEGER NOT NULL,
                    title TEXT NOT NULL,
                    description TEXT,
                    category TEXT,
                    priority TEXT,
                    status TEXT NOT NULL DEFAULT 'ACTIVE',
                    target_date TEXT,
                    progress INTEGER DEFAULT 0,
                    created_at TEXT NOT NULL DEFAULT (datetime('now')),
                    updated_at TEXT NOT NULL DEFAULT (datetime('now'))
                )
                """;
        jdbcTemplate.execute(sql);
    }

    public Goal findById(Long id) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT * FROM goals WHERE id = ?",
                    GOAL_ROW_MAPPER, id);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public List<Goal> findByUserId(Long userId) {
        return jdbcTemplate.query(
                "SELECT * FROM goals WHERE user_id = ? AND status != 'ARCHIVED' ORDER BY created_at DESC",
                GOAL_ROW_MAPPER, userId);
    }

    public Long insert(Goal goal) {
        String sql = """
                INSERT INTO goals (user_id, title, description, category, priority, status, target_date, progress)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(conn -> {
            var ps = conn.prepareStatement(sql, new String[]{"id"});
            ps.setLong(1, goal.getUserId());
            ps.setString(2, goal.getTitle());
            ps.setString(3, goal.getDescription());
            ps.setString(4, goal.getCategory());
            ps.setString(5, goal.getPriority());
            ps.setString(6, goal.getStatus() != null ? goal.getStatus() : "ACTIVE");
            ps.setString(7, goal.getTargetDate());
            ps.setObject(8, goal.getProgress() != null ? goal.getProgress() : 0);
            return ps;
        }, keyHolder);
        return keyHolder.getKey() != null ? keyHolder.getKey().longValue() : null;
    }

    public void update(Goal goal) {
        jdbcTemplate.update("""
                        UPDATE goals SET
                            title = ?,
                            description = ?,
                            category = ?,
                            priority = ?,
                            status = ?,
                            target_date = ?,
                            progress = ?,
                            updated_at = datetime('now')
                        WHERE id = ?
                        """,
                goal.getTitle(),
                goal.getDescription(),
                goal.getCategory(),
                goal.getPriority(),
                goal.getStatus(),
                goal.getTargetDate(),
                goal.getProgress(),
                goal.getId());
    }

    public void archive(Long id) {
        jdbcTemplate.update(
                "UPDATE goals SET status = 'ARCHIVED', updated_at = datetime('now') WHERE id = ?",
                id);
    }
}
