package org.example.lifecomposer.Repository;

import org.example.lifecomposer.Entity.CreditActivity;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class CreditActivityRepository {

    private final JdbcTemplate jdbcTemplate;

    public CreditActivityRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<CreditActivity> CREDIT_ACTIVITY_ROW_MAPPER = new RowMapper<>() {
        @Override
        public CreditActivity mapRow(ResultSet rs, int rowNum) throws SQLException {
            CreditActivity activity = new CreditActivity();
            activity.setId(rs.getLong("id"));
            activity.setUserId(rs.getLong("user_id"));
            long ruleId = rs.getLong("rule_id");
            activity.setRuleId(rs.wasNull() ? null : ruleId);
            activity.setCreditType(rs.getString("credit_type"));
            activity.setCategory(rs.getString("category"));
            activity.setCompName(rs.getString("comp_name"));
            activity.setCompLevel(rs.getString("comp_level"));
            activity.setAwardTier(rs.getString("award_tier"));
            double credits = rs.getDouble("credits");
            activity.setCredits(rs.wasNull() ? null : credits);
            activity.setObtainedDate(rs.getString("obtained_date"));
            activity.setCertificateRef(rs.getString("certificate_ref"));
            int verified = rs.getInt("verified");
            activity.setVerified(rs.wasNull() ? null : verified);
            activity.setNotes(rs.getString("notes"));
            activity.setCreatedAt(rs.getTimestamp("created_at"));
            return activity;
        }
    };

    public void createTableIfNeeded() {
        String sql = """
                CREATE TABLE IF NOT EXISTS credit_activities (
                  id                INTEGER PRIMARY KEY AUTOINCREMENT,
                  user_id           INTEGER NOT NULL,
                  rule_id           INTEGER,
                  credit_type       TEXT NOT NULL CHECK(credit_type IN ('graduation', 'recommendation')),
                  category          TEXT NOT NULL,
                  comp_name         TEXT,
                  comp_level        TEXT,
                  award_tier        TEXT,
                  credits           REAL NOT NULL,
                  obtained_date     TEXT,
                  certificate_ref   TEXT,
                  verified          INTEGER DEFAULT 0,
                  notes             TEXT,
                  created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """;
        jdbcTemplate.execute(sql);

        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_ca_user ON credit_activities(user_id)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_ca_type ON credit_activities(credit_type)");
    }

    public CreditActivity findById(Long id) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT * FROM credit_activities WHERE id = ?",
                    CREDIT_ACTIVITY_ROW_MAPPER, id);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public List<CreditActivity> findByUserId(Long userId) {
        return jdbcTemplate.query(
                "SELECT * FROM credit_activities WHERE user_id = ? ORDER BY created_at DESC, id DESC",
                CREDIT_ACTIVITY_ROW_MAPPER, userId);
    }

    public Long insert(CreditActivity activity) {
        String sql = """
                INSERT INTO credit_activities
                    (user_id, rule_id, credit_type, category, comp_name, comp_level,
                     award_tier, credits, obtained_date, certificate_ref, verified, notes)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"id"});
            ps.setLong(1, activity.getUserId());
            ps.setObject(2, activity.getRuleId());
            ps.setString(3, activity.getCreditType());
            ps.setString(4, activity.getCategory());
            ps.setString(5, activity.getCompName());
            ps.setString(6, activity.getCompLevel());
            ps.setString(7, activity.getAwardTier());
            ps.setObject(8, activity.getCredits());
            ps.setString(9, activity.getObtainedDate());
            ps.setString(10, activity.getCertificateRef());
            ps.setObject(11, activity.getVerified() != null ? activity.getVerified() : 0);
            ps.setString(12, activity.getNotes());
            return ps;
        }, keyHolder);
        return keyHolder.getKey() != null ? keyHolder.getKey().longValue() : null;
    }

    public void update(CreditActivity activity) {
        jdbcTemplate.update("""
                        UPDATE credit_activities SET
                            rule_id = ?,
                            credit_type = ?,
                            category = ?,
                            comp_name = ?,
                            comp_level = ?,
                            award_tier = ?,
                            credits = ?,
                            obtained_date = ?,
                            certificate_ref = ?,
                            verified = ?,
                            notes = ?
                        WHERE id = ?
                        """,
                activity.getRuleId(),
                activity.getCreditType(),
                activity.getCategory(),
                activity.getCompName(),
                activity.getCompLevel(),
                activity.getAwardTier(),
                activity.getCredits(),
                activity.getObtainedDate(),
                activity.getCertificateRef(),
                activity.getVerified() != null ? activity.getVerified() : 0,
                activity.getNotes(),
                activity.getId());
    }

    public boolean deleteByIdAndUserId(Long id, Long userId) {
        int rows = jdbcTemplate.update(
                "DELETE FROM credit_activities WHERE id = ? AND user_id = ?",
                id, userId);
        return rows > 0;
    }
}
