package org.example.lifecomposer.Repository;

import org.example.lifecomposer.entity.User;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

@Repository
public class UserRepository {

    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<User> USER_ROW_MAPPER = new RowMapper<>() {
        @Override
        public User mapRow(ResultSet rs, int rowNum) throws SQLException {
            User user = new User();
            user.setId(rs.getInt("id"));
            user.setUsername(rs.getString("username"));
            user.setPasswordHash(rs.getString("password_hash"));
            user.setType(rs.getInt("type"));
            user.setBanned(rs.getBoolean("is_banned"));
            user.setBanEndTime(rs.getTimestamp("ban_end_time"));
            user.setAvatar(rs.getString("avatar"));
            user.setCreatedAt(rs.getTimestamp("created_at"));
            user.setUpdatedAt(rs.getTimestamp("updated_at"));
            return user;
        }
    };

    public void createUserTableIfNeeded() {
        String sql = """
                CREATE TABLE IF NOT EXISTS users (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  username TEXT UNIQUE NOT NULL,
                  password_hash TEXT NOT NULL,
                  type INTEGER NOT NULL DEFAULT 1 CHECK(type IN (1,2)),
                  is_banned BOOLEAN NOT NULL DEFAULT 0,
                  ban_end_time TIMESTAMP NULL,
                  avatar TEXT NULL,
                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """;
        jdbcTemplate.execute(sql);
    }

    public void migrateUserSchema() {
        // Remove legacy column from old project structure.
        // SQLite (3.35+) supports DROP COLUMN. If runtime SQLite is older, this will be ignored safely.
        if (hasColumn("users", "credit")) {
            try {
                jdbcTemplate.execute("ALTER TABLE users DROP COLUMN credit");
            } catch (Exception ignored) {
                // Keep startup resilient on older SQLite engines.
            }
        }

        // Remove legacy blind type and old type=0 users. Keep only:
        // 1 = USER, 2 = ADMIN
        jdbcTemplate.update("UPDATE users SET type = 1 WHERE type IS NULL OR type IN (0, 1)");

        if (!hasColumn("users", "password_hash") && hasColumn("users", "password")) {
            jdbcTemplate.execute("ALTER TABLE users ADD COLUMN password_hash TEXT");
            jdbcTemplate.update("UPDATE users SET password_hash = password WHERE password_hash IS NULL");
        }

        if (!hasColumn("users", "is_banned")) {
            jdbcTemplate.execute("ALTER TABLE users ADD COLUMN is_banned BOOLEAN NOT NULL DEFAULT 0");
        }

        if (!hasColumn("users", "ban_end_time")) {
            jdbcTemplate.execute("ALTER TABLE users ADD COLUMN ban_end_time TIMESTAMP NULL");
        }

        if (!hasColumn("users", "avatar")) {
            jdbcTemplate.execute("ALTER TABLE users ADD COLUMN avatar TEXT NULL");
        }

        if (!hasColumn("users", "created_at")) {
            jdbcTemplate.execute("ALTER TABLE users ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP");
        }

        if (!hasColumn("users", "updated_at")) {
            jdbcTemplate.execute("ALTER TABLE users ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP");
        }
    }

    private boolean hasColumn(String tableName, String columnName) {
        String sql = "PRAGMA table_info(" + tableName + ")";
        List<String> columns = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("name"));
        return columns.stream().anyMatch(col -> col.equalsIgnoreCase(columnName));
    }

    public int insertUser(User user) {
        String sql = """
                INSERT INTO users(username, password_hash, type, is_banned, ban_end_time, avatar, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        Timestamp now = new Timestamp(System.currentTimeMillis());
        return jdbcTemplate.update(sql,
                user.getUsername(),
                user.getPasswordHash(),
                user.getType(),
                Boolean.TRUE.equals(user.getBanned()),
                user.getBanEndTime(),
                user.getAvatar(),
                now,
                now
        );
    }

    public User findByUsername(String username) {
        try {
            return jdbcTemplate.queryForObject("SELECT * FROM users WHERE username = ?", USER_ROW_MAPPER, username);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public User findById(int id) {
        try {
            return jdbcTemplate.queryForObject("SELECT * FROM users WHERE id = ?", USER_ROW_MAPPER, id);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public List<User> findAll() {
        return jdbcTemplate.query("SELECT * FROM users ORDER BY id", USER_ROW_MAPPER);
    }

    public boolean updateUserType(int userId, int type) {
        int rows = jdbcTemplate.update("UPDATE users SET type = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", type, userId);
        return rows > 0;
    }

    public boolean updateUserAvatar(int userId, String avatarPath) {
        int rows = jdbcTemplate.update("UPDATE users SET avatar = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", avatarPath, userId);
        return rows > 0;
    }

    public boolean updateUserPassword(int userId, String passwordHash) {
        int rows = jdbcTemplate.update("UPDATE users SET password_hash = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", passwordHash, userId);
        return rows > 0;
    }

    public boolean updateBanStatus(int userId, boolean banned, Timestamp banEndTime) {
        int rows = jdbcTemplate.update(
                "UPDATE users SET is_banned = ?, ban_end_time = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                banned,
                banEndTime,
                userId
        );
        return rows > 0;
    }

    public long countAdminUsers() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE type = 2", Long.class);
        return count == null ? 0 : count;
    }
}
