package org.example.lifecomposer.Repository;

import org.example.lifecomposer.Entity.PlanningHistory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class PlanningHistoryRepository {

    private final JdbcTemplate jdbcTemplate;

    public PlanningHistoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<PlanningHistory> ROW_MAPPER = new RowMapper<>() {
        @Override
        public PlanningHistory mapRow(ResultSet rs, int rowNum) throws SQLException {
            PlanningHistory record = new PlanningHistory();
            record.setId(rs.getLong("id"));
            record.setUserId(rs.getLong("user_id"));
            record.setType(rs.getString("type"));
            record.setRequestJson(rs.getString("request_json"));
            record.setResponseJson(rs.getString("response_json"));
            record.setProvider(rs.getString("provider"));
            record.setModel(rs.getString("model"));
            record.setStatus(rs.getString("status"));
            record.setErrorMessage(rs.getString("error_message"));
            record.setCreatedAt(rs.getString("created_at"));
            return record;
        }
    };

    public void createTableIfNeeded() {
        String sql = """
                CREATE TABLE IF NOT EXISTS planning_history (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id INTEGER NOT NULL,
                    type TEXT NOT NULL,
                    request_json TEXT,
                    response_json TEXT,
                    provider TEXT,
                    model TEXT,
                    status TEXT NOT NULL DEFAULT 'MOCKED',
                    error_message TEXT,
                    created_at TEXT NOT NULL DEFAULT (datetime('now'))
                )
                """;
        jdbcTemplate.execute(sql);
    }

    public Long insert(PlanningHistory record) {
        String sql = """
                INSERT INTO planning_history (user_id, type, request_json, response_json, provider, model, status, error_message)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(conn -> {
            var ps = conn.prepareStatement(sql, new String[]{"id"});
            ps.setLong(1, record.getUserId());
            ps.setString(2, record.getType());
            ps.setString(3, record.getRequestJson());
            ps.setString(4, record.getResponseJson());
            ps.setString(5, record.getProvider());
            ps.setString(6, record.getModel());
            ps.setString(7, record.getStatus() != null ? record.getStatus() : "MOCKED");
            ps.setString(8, record.getErrorMessage());
            return ps;
        }, keyHolder);
        return keyHolder.getKey() != null ? keyHolder.getKey().longValue() : null;
    }

    public PlanningHistory findById(Long id) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT * FROM planning_history WHERE id = ?",
                    ROW_MAPPER, id);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public List<PlanningHistory> findByUserId(Long userId) {
        return jdbcTemplate.query(
                "SELECT * FROM planning_history WHERE user_id = ? ORDER BY created_at DESC",
                ROW_MAPPER, userId);
    }
}
