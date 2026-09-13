package org.example.lifecomposer.Repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Daily chat usage counter backing Milestone 5 quota enforcement. */
@Repository
public class ChatUsageRepository {

    private final JdbcTemplate jdbcTemplate;

    public ChatUsageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void createTableIfNeeded() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS chat_usage_daily (
                  id            INTEGER PRIMARY KEY AUTOINCREMENT,
                  user_id       INTEGER NOT NULL,
                  usage_date    TEXT NOT NULL,
                  request_count INTEGER NOT NULL DEFAULT 0,
                  updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  UNIQUE(user_id, usage_date)
                )
                """);
        jdbcTemplate.execute(
                "CREATE INDEX IF NOT EXISTS idx_chat_usage_date ON chat_usage_daily(usage_date)");
    }

    /**
     * Atomically inserts or increments today's counter only while it is below the
     * limit. Returns 1 when admitted, 0 when the daily limit was already reached.
     */
    public int tryIncrement(Integer userId, String usageDate, int dailyLimit) {
        return jdbcTemplate.update("""
                        INSERT INTO chat_usage_daily(user_id, usage_date, request_count, updated_at)
                        VALUES (?, ?, 1, CURRENT_TIMESTAMP)
                        ON CONFLICT(user_id, usage_date) DO UPDATE SET
                            request_count = request_count + 1,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE request_count < ?
                        """,
                userId, usageDate, dailyLimit);
    }

    /** Refund one unit after a later admission failure (e.g. minute limiter race). */
    public int decrement(Integer userId, String usageDate) {
        return jdbcTemplate.update("""
                        UPDATE chat_usage_daily
                        SET request_count = CASE WHEN request_count > 0 THEN request_count - 1 ELSE 0 END,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE user_id = ? AND usage_date = ?
                        """,
                userId, usageDate);
    }

    public int getCount(Integer userId, String usageDate) {
        java.util.List<Integer> counts = jdbcTemplate.queryForList(
                "SELECT request_count FROM chat_usage_daily WHERE user_id = ? AND usage_date = ?",
                Integer.class, userId, usageDate);
        return counts.isEmpty() || counts.get(0) == null ? 0 : counts.get(0);
    }

    public int reset(Integer userId, String usageDate) {
        return jdbcTemplate.update("""
                        UPDATE chat_usage_daily
                        SET request_count = 0, updated_at = CURRENT_TIMESTAMP
                        WHERE user_id = ? AND usage_date = ?
                        """,
                userId, usageDate);
    }

    public int deleteOlderThan(String cutoffDate) {
        return jdbcTemplate.update(
                "DELETE FROM chat_usage_daily WHERE usage_date < ?", cutoffDate);
    }
}
