package org.example.lifecomposer.Repository;

import org.example.lifecomposer.Entity.CapabilityReference;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class CapabilityReferenceRepository {

    private final JdbcTemplate jdbcTemplate;

    public CapabilityReferenceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<CapabilityReference> CAPABILITY_REFERENCE_ROW_MAPPER = new RowMapper<>() {
        @Override
        public CapabilityReference mapRow(ResultSet rs, int rowNum) throws SQLException {
            CapabilityReference reference = new CapabilityReference();
            reference.setId(rs.getLong("id"));
            reference.setSection(rs.getString("section"));
            reference.setRefKey(rs.getString("ref_key"));
            reference.setRefValue(rs.getString("ref_value"));
            reference.setNote(rs.getString("note"));
            return reference;
        }
    };

    public List<CapabilityReference> findAll() {
        String sql = """
                SELECT * FROM capability_reference
                ORDER BY id
                """;
        return jdbcTemplate.query(sql, CAPABILITY_REFERENCE_ROW_MAPPER);
    }

    /** 按字典节过滤（tags_to_merge / skill_mapping / skill_profiles / role_profiles / major_categories / _meta）。 */
    public List<CapabilityReference> findBySection(String section) {
        String sql = """
                SELECT * FROM capability_reference
                WHERE section = ?
                ORDER BY ref_key
                """;
        return jdbcTemplate.query(sql, CAPABILITY_REFERENCE_ROW_MAPPER, section);
    }

    /** 按 (section, ref_key) 精确查找，找不到返回 null。 */
    public CapabilityReference findBySectionAndKey(String section, String refKey) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT * FROM capability_reference WHERE section = ? AND ref_key = ?",
                    CAPABILITY_REFERENCE_ROW_MAPPER, section, refKey);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    /** 按 (section, ref_key) 幂等写入（重复导入同一交付包不产生重复行）。 */
    public int upsert(CapabilityReference reference) {
        String sql = """
                INSERT INTO capability_reference (section, ref_key, ref_value, note)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(section, ref_key) DO UPDATE SET
                    ref_value = excluded.ref_value,
                    note = excluded.note
                """;
        return jdbcTemplate.update(sql,
                reference.getSection(),
                reference.getRefKey(),
                reference.getRefValue(),
                reference.getNote());
    }

    public void createTableIfNeeded() {
        String sql = """
                CREATE TABLE IF NOT EXISTS capability_reference (
                  id        INTEGER PRIMARY KEY AUTOINCREMENT,
                  section   TEXT NOT NULL CHECK(section IN ('tags_to_merge', 'skill_mapping', 'skill_profiles', 'role_profiles', 'major_categories', '_meta')),
                  ref_key   TEXT NOT NULL,
                  ref_value TEXT NOT NULL,
                  note      TEXT,
                  UNIQUE(section, ref_key)
                )
                """;
        jdbcTemplate.execute(sql);
    }
}
