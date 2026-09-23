package org.example.lifecomposer.Repository;

import org.example.lifecomposer.Entity.ProfileChangeCandidate;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class ProfileChangeCandidateRepository {

    private final JdbcTemplate jdbcTemplate;

    public ProfileChangeCandidateRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<ProfileChangeCandidate> ROW_MAPPER = new RowMapper<>() {
        @Override
        public ProfileChangeCandidate mapRow(ResultSet rs, int rowNum) throws SQLException {
            ProfileChangeCandidate candidate = new ProfileChangeCandidate();
            candidate.setId(rs.getLong("id"));
            candidate.setCandidateId(rs.getString("candidate_id"));
            candidate.setUserId(rs.getLong("user_id"));
            candidate.setFieldName(rs.getString("field_name"));
            candidate.setOldValue(rs.getString("old_value"));
            candidate.setNewValue(rs.getString("new_value"));
            candidate.setRationale(rs.getString("rationale"));
            candidate.setSource(rs.getString("source"));
            candidate.setStatus(rs.getString("status"));
            candidate.setBaseVersion(rs.getLong("base_version"));
            candidate.setCreatedAt(rs.getString("created_at"));
            candidate.setExpiresAt(rs.getString("expires_at"));
            candidate.setDecidedAt(rs.getString("decided_at"));
            candidate.setDecisionReason(rs.getString("decision_reason"));
            long merged = rs.getLong("merged_version");
            candidate.setMergedVersion(rs.wasNull() ? null : merged);
            return candidate;
        }
    };

    public void createTableIfNeeded() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS profile_change_candidates (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    candidate_id TEXT NOT NULL UNIQUE,
                    user_id INTEGER NOT NULL,
                    field_name TEXT NOT NULL,
                    old_value TEXT,
                    new_value TEXT NOT NULL,
                    rationale TEXT,
                    source TEXT NOT NULL DEFAULT 'chat',
                    status TEXT NOT NULL DEFAULT 'PENDING_CONFIRMATION'
                        CHECK(status IN ('PENDING_CONFIRMATION', 'CONFIRMED', 'REJECTED', 'EXPIRED', 'CONFLICT')),
                    base_version INTEGER NOT NULL DEFAULT 0,
                    created_at TEXT NOT NULL DEFAULT (datetime('now')),
                    expires_at TEXT NOT NULL,
                    decided_at TEXT,
                    decision_reason TEXT,
                    merged_version INTEGER
                )
                """);
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_profile_change_user_status "
                + "ON profile_change_candidates(user_id, status)");
    }

    public void insert(ProfileChangeCandidate candidate) {
        jdbcTemplate.update("""
                        INSERT INTO profile_change_candidates
                            (candidate_id, user_id, field_name, old_value, new_value, rationale, source,
                             status, base_version, created_at, expires_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, datetime('now'), ?)
                        """,
                candidate.getCandidateId(),
                candidate.getUserId(),
                candidate.getFieldName(),
                candidate.getOldValue(),
                candidate.getNewValue(),
                candidate.getRationale(),
                candidate.getSource(),
                candidate.getStatus(),
                candidate.getBaseVersion(),
                candidate.getExpiresAt());
    }

    public ProfileChangeCandidate findByCandidateIdAndUserId(String candidateId, Long userId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT * FROM profile_change_candidates WHERE candidate_id = ? AND user_id = ?",
                    ROW_MAPPER, candidateId, userId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public List<ProfileChangeCandidate> findByUserIdAndStatus(Long userId, String status) {
        return jdbcTemplate.query(
                "SELECT * FROM profile_change_candidates WHERE user_id = ? AND status = ? "
                        + "ORDER BY id ASC",
                ROW_MAPPER, userId, status);
    }

    public List<ProfileChangeCandidate> findPendingByUserId(Long userId) {
        return findByUserIdAndStatus(userId, ProfileChangeCandidate.PENDING);
    }

    public int countPendingByUserId(Long userId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM profile_change_candidates WHERE user_id = ? AND status = ?",
                Integer.class, userId, ProfileChangeCandidate.PENDING);
        return count == null ? 0 : count;
    }

    /** Expires every stale pending candidate and returns how many rows changed. */
    public int expireStale(String now) {
        return jdbcTemplate.update("""
                UPDATE profile_change_candidates
                SET status = 'EXPIRED', decided_at = ?
                WHERE status = 'PENDING_CONFIRMATION' AND expires_at <= ?
                """, now, now);
    }

    /**
     * Atomic claim step: only one request can flip a PENDING candidate into the
     * claimed CONFIRMED state. The profile is written only after a successful
     * claim, so a concurrent REJECT/EXPIRED can never leave an unconfirmed
     * profile mutation behind.
     */
    public boolean claimForConfirmation(String candidateId, Long userId, String decidedAt) {
        return jdbcTemplate.update("""
                UPDATE profile_change_candidates
                SET status = 'CONFIRMED', decided_at = ?, merged_version = NULL
                WHERE candidate_id = ? AND user_id = ? AND status = 'PENDING_CONFIRMATION'
                  AND expires_at > ?
                """, decidedAt, candidateId, userId, decidedAt) > 0;
    }

    /** Records the merged profile version on a candidate this transaction claimed. */
    public boolean updateMergedVersion(String candidateId, Long userId, long mergedVersion) {
        return jdbcTemplate.update("""
                UPDATE profile_change_candidates
                SET merged_version = ?
                WHERE candidate_id = ? AND user_id = ? AND status = 'CONFIRMED'
                """, mergedVersion, candidateId, userId) > 0;
    }

    /** Turns a claimed-but-not-merged candidate into CONFLICT (no profile write happened). */
    public boolean markConflictFromClaimed(String candidateId, Long userId, String decidedAt) {
        return jdbcTemplate.update("""
                UPDATE profile_change_candidates
                SET status = 'CONFLICT', decided_at = ?, merged_version = NULL
                WHERE candidate_id = ? AND user_id = ? AND status = 'CONFIRMED' AND merged_version IS NULL
                """, decidedAt, candidateId, userId) > 0;
    }

    public boolean markConfirmed(String candidateId, Long userId, long mergedVersion, String decidedAt) {
        return jdbcTemplate.update("""
                UPDATE profile_change_candidates
                SET status = 'CONFIRMED', decided_at = ?, merged_version = ?
                WHERE candidate_id = ? AND user_id = ? AND status = 'PENDING_CONFIRMATION'
                """, decidedAt, mergedVersion, candidateId, userId) > 0;
    }

    public boolean markRejected(String candidateId, Long userId, String reason, String decidedAt) {
        return jdbcTemplate.update("""
                UPDATE profile_change_candidates
                SET status = 'REJECTED', decided_at = ?, decision_reason = ?
                WHERE candidate_id = ? AND user_id = ? AND status = 'PENDING_CONFIRMATION'
                """, decidedAt, reason, candidateId, userId) > 0;
    }

    public boolean markExpired(String candidateId, Long userId, String decidedAt) {
        return jdbcTemplate.update("""
                UPDATE profile_change_candidates
                SET status = 'EXPIRED', decided_at = ?
                WHERE candidate_id = ? AND user_id = ? AND status = 'PENDING_CONFIRMATION'
                """, decidedAt, candidateId, userId) > 0;
    }

    public boolean markConflict(String candidateId, Long userId, String decidedAt) {
        return jdbcTemplate.update("""
                UPDATE profile_change_candidates
                SET status = 'CONFLICT', decided_at = ?
                WHERE candidate_id = ? AND user_id = ? AND status = 'PENDING_CONFIRMATION'
                """, decidedAt, candidateId, userId) > 0;
    }
}
