package org.example.lifecomposer.Repository;

import org.example.lifecomposer.Entity.CapabilityTag;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class CapabilityTagRepository {

    private final JdbcTemplate jdbcTemplate;

    public CapabilityTagRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<CapabilityTag> CAPABILITY_TAG_ROW_MAPPER = new RowMapper<>() {
        @Override
        public CapabilityTag mapRow(ResultSet rs, int rowNum) throws SQLException {
            CapabilityTag tag = new CapabilityTag();
            tag.setName(rs.getString("name"));
            tag.setCategory(rs.getString("category"));
            tag.setLevel1Desc(rs.getString("level1_desc"));
            tag.setLevel2Desc(rs.getString("level2_desc"));
            tag.setLevel3Desc(rs.getString("level3_desc"));
            tag.setSkillAliasesJson(rs.getString("skill_aliases_json"));
            tag.setTypicalEvidenceJson(rs.getString("typical_evidence_json"));
            tag.setCreatedAt(rs.getTimestamp("created_at"));
            tag.setUpdatedAt(rs.getTimestamp("updated_at"));
            return tag;
        }
    };

    public List<CapabilityTag> findAll() {
        String sql = """
                SELECT * FROM capability_tags
                ORDER BY name
                """;
        return jdbcTemplate.query(sql, CAPABILITY_TAG_ROW_MAPPER);
    }

    /** 按标签主键 name 查找（如 编程基础），找不到返回 null。 */
    public CapabilityTag findByName(String name) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT * FROM capability_tags WHERE name = ?",
                    CAPABILITY_TAG_ROW_MAPPER, name);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    /** 按标签 name 幂等写入（冲突时更新描述字段、刷新 updated_at）。 */
    public int upsert(CapabilityTag tag) {
        String sql = """
                INSERT INTO capability_tags
                    (name, category, level1_desc, level2_desc, level3_desc,
                     skill_aliases_json, typical_evidence_json)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(name) DO UPDATE SET
                    category = excluded.category,
                    level1_desc = excluded.level1_desc,
                    level2_desc = excluded.level2_desc,
                    level3_desc = excluded.level3_desc,
                    skill_aliases_json = excluded.skill_aliases_json,
                    typical_evidence_json = excluded.typical_evidence_json,
                    updated_at = CURRENT_TIMESTAMP
                """;
        return jdbcTemplate.update(sql,
                tag.getName(),
                tag.getCategory(),
                tag.getLevel1Desc(),
                tag.getLevel2Desc(),
                tag.getLevel3Desc(),
                tag.getSkillAliasesJson(),
                tag.getTypicalEvidenceJson());
    }

    public void createTableIfNeeded() {
        String sql = """
                CREATE TABLE IF NOT EXISTS capability_tags (
                  name                  TEXT PRIMARY KEY,
                  category              TEXT NOT NULL CHECK(category IN ('技术能力', '通用能力')),
                  level1_desc           TEXT,
                  level2_desc           TEXT,
                  level3_desc           TEXT,
                  skill_aliases_json    TEXT,
                  typical_evidence_json TEXT,
                  created_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  updated_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """;
        jdbcTemplate.execute(sql);
    }
}
