package org.example.lifecomposer.Repository;

import org.example.lifecomposer.Entity.UserProfile;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class UserProfileRepository {

    private final JdbcTemplate jdbcTemplate;

    public UserProfileRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<UserProfile> USER_PROFILE_ROW_MAPPER = new RowMapper<>() {
        @Override
        public UserProfile mapRow(ResultSet rs, int rowNum) throws SQLException {
            UserProfile profile = new UserProfile();
            profile.setId(rs.getLong("id"));
            profile.setUserId(rs.getLong("user_id"));
            profile.setCollege(rs.getString("college"));
            profile.setMajor(rs.getString("major"));
            profile.setGrade(rs.getString("grade"));
            profile.setStudentId(rs.getString("student_id"));
            profile.setSkillsJson(rs.getString("skills_json"));
            profile.setInterestsJson(rs.getString("interests_json"));
            profile.setExperiencesJson(rs.getString("experiences_json"));
            profile.setPreferencesJson(rs.getString("preferences_json"));
            profile.setAvailableTime(rs.getString("available_time"));
            profile.setGoals(rs.getString("goals"));
            profile.setCreatedAt(rs.getString("created_at"));
            profile.setUpdatedAt(rs.getString("updated_at"));
            return profile;
        }
    };

    public void createTableIfNeeded() {
        String sql = """
                CREATE TABLE IF NOT EXISTS user_profiles (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id INTEGER NOT NULL UNIQUE,
                    college TEXT,
                    major TEXT,
                    grade TEXT,
                    student_id TEXT,
                    skills_json TEXT,
                    interests_json TEXT,
                    experiences_json TEXT,
                    preferences_json TEXT,
                    available_time TEXT,
                    goals TEXT,
                    created_at TEXT NOT NULL DEFAULT (datetime('now')),
                    updated_at TEXT NOT NULL DEFAULT (datetime('now'))
                )
                """;
        jdbcTemplate.execute(sql);
    }

    public void migrateUserSchema() {
        if (!hasColumn("user_profiles", "available_time")) {
            jdbcTemplate.execute("ALTER TABLE user_profiles ADD COLUMN available_time TEXT");
        }

        if (!hasColumn("user_profiles", "goals")) {
            jdbcTemplate.execute("ALTER TABLE user_profiles ADD COLUMN goals TEXT");
        }
    }

    private boolean hasColumn(String tableName, String columnName) {
        String sql = "PRAGMA table_info(" + tableName + ")";
        List<String> columns = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("name"));
        return columns.stream().anyMatch(col -> col.equalsIgnoreCase(columnName));
    }

    public UserProfile findByUserId(Long userId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT * FROM user_profiles WHERE user_id = ?",
                    USER_PROFILE_ROW_MAPPER, userId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public boolean upsert(UserProfile profile) {
        String sql = """
                INSERT INTO user_profiles
                    (user_id, college, major, grade, student_id,
                     skills_json, interests_json, experiences_json, preferences_json,
                     available_time, goals,
                     created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, COALESCE(
                    (SELECT created_at FROM user_profiles WHERE user_id = ?),
                    datetime('now')
                ), datetime('now'))
                ON CONFLICT(user_id) DO UPDATE SET
                    college = excluded.college,
                    major = excluded.major,
                    grade = excluded.grade,
                    student_id = excluded.student_id,
                    skills_json = excluded.skills_json,
                    interests_json = excluded.interests_json,
                    experiences_json = excluded.experiences_json,
                    preferences_json = excluded.preferences_json,
                    available_time = excluded.available_time,
                    goals = excluded.goals,
                    updated_at = datetime('now')
                """;
        Long userId = profile.getUserId();
        int rows = jdbcTemplate.update(sql,
                userId,
                profile.getCollege(),
                profile.getMajor(),
                profile.getGrade(),
                profile.getStudentId(),
                profile.getSkillsJson(),
                profile.getInterestsJson(),
                profile.getExperiencesJson(),
                profile.getPreferencesJson(),
                profile.getAvailableTime(),
                profile.getGoals(),
                userId
        );
        return rows > 0;
    }
}
