package org.example.lifecomposer.Repository;

import org.example.lifecomposer.Entity.Resource;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class ResourceRepository {

    private final JdbcTemplate jdbcTemplate;

    public ResourceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<Resource> RESOURCE_ROW_MAPPER = new RowMapper<>() {
        @Override
        public Resource mapRow(ResultSet rs, int rowNum) throws SQLException {
            Resource resource = new Resource();
            resource.setId(rs.getLong("id"));
            resource.setResourceId(rs.getString("resource_id"));
            resource.setName(rs.getString("name"));
            resource.setType(rs.getString("type"));
            resource.setLevelsJson(rs.getString("levels_json"));
            resource.setStagesJson(rs.getString("stages_json"));
            resource.setTargetMajorsJson(rs.getString("target_majors_json"));
            resource.setRegistrationStart(rs.getString("registration_start"));
            resource.setRegistrationDeadline(rs.getString("registration_deadline"));
            resource.setRequiredSkillsJson(rs.getString("required_skills_json"));
            resource.setDifficulty(rs.getString("difficulty"));
            resource.setPreparationPeriod(rs.getString("preparation_period"));
            resource.setTeamRolesJson(rs.getString("team_roles_json"));
            resource.setBonusPointJson(rs.getString("bonus_point_json"));
            resource.setProvider(rs.getString("provider"));
            resource.setCourseLink(rs.getString("course_link"));
            resource.setDescription(rs.getString("description"));
            resource.setTeachesSkillsJson(rs.getString("teaches_skills_json"));
            resource.setSourceUrl(rs.getString("source_url"));
            resource.setSourceUrlsJson(rs.getString("source_urls_json"));
            resource.setSourceFile(rs.getString("source_file"));
            resource.setNotesJson(rs.getString("notes_json"));
            resource.setDataQuality(rs.getString("data_quality"));
            resource.setUpdatedAt(rs.getString("updated_at"));
            resource.setCreatedAt(rs.getTimestamp("created_at"));
            return resource;
        }
    };

    public List<Resource> findAll() {
        String sql = """
                SELECT * FROM resources
                ORDER BY id
                """;
        return jdbcTemplate.query(sql, RESOURCE_ROW_MAPPER);
    }

    public List<Resource> findByType(String type) {
        String sql = """
                SELECT * FROM resources
                WHERE type = ?
                ORDER BY id
                """;
        return jdbcTemplate.query(sql, RESOURCE_ROW_MAPPER, type);
    }

    /** 按物理主键 id 查找（AUTOINCREMENT），找不到返回 null。 */
    public Resource findById(Long id) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT * FROM resources WHERE id = ?",
                    RESOURCE_ROW_MAPPER, id);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    /** 按业务主键 resource_id 查找（如 competition_001），找不到返回 null。 */
    public Resource findByResourceId(String resourceId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT * FROM resources WHERE resource_id = ?",
                    RESOURCE_ROW_MAPPER, resourceId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    /**
     * 按 resource_id 幂等写入（重复导入同一交付包不产生重复行），
     * 冲突时更新业务字段、保留入库 created_at。
     */
    public int upsert(Resource resource) {
        String sql = """
                INSERT INTO resources
                    (resource_id, name, type, levels_json, stages_json,
                     target_majors_json, registration_start, registration_deadline,
                     required_skills_json, difficulty, preparation_period,
                     team_roles_json, bonus_point_json, provider, course_link,
                     description, teaches_skills_json, source_url, source_urls_json,
                     source_file, notes_json, data_quality, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, COALESCE(?, 'needs_review'), ?)
                ON CONFLICT(resource_id) DO UPDATE SET
                    name = excluded.name,
                    type = excluded.type,
                    levels_json = excluded.levels_json,
                    stages_json = excluded.stages_json,
                    target_majors_json = excluded.target_majors_json,
                    registration_start = excluded.registration_start,
                    registration_deadline = excluded.registration_deadline,
                    required_skills_json = excluded.required_skills_json,
                    difficulty = excluded.difficulty,
                    preparation_period = excluded.preparation_period,
                    team_roles_json = excluded.team_roles_json,
                    bonus_point_json = excluded.bonus_point_json,
                    provider = excluded.provider,
                    course_link = excluded.course_link,
                    description = excluded.description,
                    teaches_skills_json = excluded.teaches_skills_json,
                    source_url = excluded.source_url,
                    source_urls_json = excluded.source_urls_json,
                    source_file = excluded.source_file,
                    notes_json = excluded.notes_json,
                    data_quality = excluded.data_quality,
                    updated_at = excluded.updated_at
                """;
        return jdbcTemplate.update(sql,
                resource.getResourceId(),
                resource.getName(),
                resource.getType(),
                resource.getLevelsJson(),
                resource.getStagesJson(),
                resource.getTargetMajorsJson(),
                resource.getRegistrationStart(),
                resource.getRegistrationDeadline(),
                resource.getRequiredSkillsJson(),
                resource.getDifficulty(),
                resource.getPreparationPeriod(),
                resource.getTeamRolesJson(),
                resource.getBonusPointJson(),
                resource.getProvider(),
                resource.getCourseLink(),
                resource.getDescription(),
                resource.getTeachesSkillsJson(),
                resource.getSourceUrl(),
                resource.getSourceUrlsJson(),
                resource.getSourceFile(),
                resource.getNotesJson(),
                resource.getDataQuality(),
                resource.getUpdatedAt());
    }

    public void createTableIfNeeded() {
        String sql = """
                CREATE TABLE IF NOT EXISTS resources (
                  id                    INTEGER PRIMARY KEY AUTOINCREMENT,
                  resource_id           TEXT NOT NULL UNIQUE,
                  name                  TEXT NOT NULL,
                  type                  TEXT NOT NULL CHECK(type IN ('competition', 'course')),
                  levels_json           TEXT,
                  stages_json           TEXT,
                  target_majors_json    TEXT,
                  registration_start    TEXT,
                  registration_deadline TEXT,
                  required_skills_json  TEXT,
                  difficulty            TEXT CHECK(difficulty IN ('easy', 'medium', 'hard')),
                  preparation_period    TEXT,
                  team_roles_json       TEXT,
                  bonus_point_json      TEXT,
                  provider              TEXT,
                  course_link           TEXT,
                  description           TEXT,
                  teaches_skills_json   TEXT,
                  source_url            TEXT,
                  source_urls_json      TEXT,
                  source_file           TEXT,
                  notes_json            TEXT,
                  data_quality          TEXT NOT NULL DEFAULT 'needs_review' CHECK(data_quality IN ('complete', 'partial', 'needs_review')),
                  updated_at            TEXT,
                  created_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """;
        jdbcTemplate.execute(sql);
    }
}
