package org.example.lifecomposer.Repository;

import org.example.lifecomposer.Entity.CollegeCreditRule;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

@Repository
public class CollegeCreditRuleRepository {

    private final JdbcTemplate jdbcTemplate;

    public CollegeCreditRuleRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<CollegeCreditRule> COLLEGE_CREDIT_RULE_ROW_MAPPER = new RowMapper<>() {
        @Override
        public CollegeCreditRule mapRow(ResultSet rs, int rowNum) throws SQLException {
            CollegeCreditRule rule = new CollegeCreditRule();
            rule.setId(rs.getLong("id"));
            rule.setCollege(rs.getString("college"));
            rule.setCreditType(rs.getString("credit_type"));
            rule.setCategory(rs.getString("category"));
            rule.setCompLevel(rs.getString("comp_level"));
            rule.setCompName(rs.getString("comp_name"));
            rule.setAwardTier(rs.getString("award_tier"));
            double credits = rs.getDouble("credits");
            rule.setCredits(rs.wasNull() ? null : credits);
            rule.setCategoryCap(nullableDouble(rs, "category_cap"));
            rule.setTeamFormula(rs.getString("team_formula"));
            rule.setStudentCohort(rs.getString("student_cohort"));
            rule.setDocSource(rs.getString("doc_source"));
            rule.setLevelsJson(rs.getString("levels_json"));
            rule.setNotes(rs.getString("notes"));
            Timestamp createdAt = rs.getTimestamp("created_at");
            rule.setCreatedAt(createdAt);
            return rule;
        }

        private Double nullableDouble(ResultSet rs, String column) throws SQLException {
            double value = rs.getDouble(column);
            return rs.wasNull() ? null : value;
        }
    };

    public void createTableIfNeeded() {
        String sql = """
                CREATE TABLE IF NOT EXISTS college_credit_rules (
                  id                INTEGER PRIMARY KEY AUTOINCREMENT,
                  college           TEXT NOT NULL,
                  credit_type       TEXT NOT NULL CHECK(credit_type IN ('graduation', 'recommendation')),
                  category          TEXT NOT NULL CHECK(category IN ('competition', 'lecture', 'course', 'project', 'paper', 'patent', 'sports', 'arts', 'veteran')),
                  comp_level        TEXT CHECK(comp_level IN ('S', 'A+', 'A', 'B+', 'B', 'national', 'provincial', 'school')),
                  comp_name         TEXT,
                  award_tier        TEXT CHECK(award_tier IN ('first', 'second', 'third', 'special', 'participation')),
                  credits           REAL NOT NULL,
                  category_cap      REAL,
                  team_formula      TEXT,
                  student_cohort    TEXT,
                  doc_source        TEXT,
                  levels_json       TEXT,
                  notes             TEXT,
                  created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """;
        jdbcTemplate.execute(sql);
    }

    /** 按学院/加分类型/类别/级别/奖项组合检索；空参数忽略。 */
    public List<CollegeCreditRule> search(String college,
                                          String creditType,
                                          String category,
                                          String compLevel,
                                          String awardTier,
                                          Integer limit) {
        StringBuilder sql = new StringBuilder("SELECT * FROM college_credit_rules WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (college != null && !college.isBlank()) {
            sql.append(" AND college LIKE ?");
            params.add("%" + college.trim() + "%");
        }
        if (creditType != null && !creditType.isBlank()) {
            sql.append(" AND credit_type = ?");
            params.add(creditType.trim());
        }
        if (category != null && !category.isBlank()) {
            sql.append(" AND category = ?");
            params.add(category.trim());
        }
        if (compLevel != null && !compLevel.isBlank()) {
            sql.append(" AND comp_level = ?");
            params.add(compLevel.trim());
        }
        if (awardTier != null && !awardTier.isBlank()) {
            sql.append(" AND award_tier = ?");
            params.add(awardTier.trim());
        }
        sql.append(" ORDER BY id ASC");
        if (limit != null && limit > 0) {
            sql.append(" LIMIT ?");
            params.add(limit);
        }
        return jdbcTemplate.query(sql.toString(), COLLEGE_CREDIT_RULE_ROW_MAPPER, params.toArray());
    }

    /**
     * 按业务字段组合查找已存在规则。用于导入工具在缺少唯一约束的情况下实现幂等 upsert。
     * 可空字段使用 SQLite 的 IS 比较，保证 NULL 也能匹配。
     */
    public CollegeCreditRule findMatching(CollegeCreditRule rule) {
        try {
            return jdbcTemplate.queryForObject("""
                            SELECT * FROM college_credit_rules
                            WHERE college = ?
                              AND credit_type = ?
                              AND category = ?
                              AND comp_level IS ?
                              AND comp_name IS ?
                              AND award_tier IS ?
                              AND credits = ?
                              AND category_cap IS ?
                              AND team_formula IS ?
                              AND student_cohort IS ?
                              AND doc_source IS ?
                            LIMIT 1
                            """,
                    COLLEGE_CREDIT_RULE_ROW_MAPPER,
                    rule.getCollege(),
                    rule.getCreditType(),
                    rule.getCategory(),
                    rule.getCompLevel(),
                    rule.getCompName(),
                    rule.getAwardTier(),
                    rule.getCredits(),
                    rule.getCategoryCap(),
                    rule.getTeamFormula(),
                    rule.getStudentCohort(),
                    rule.getDocSource());
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public void update(CollegeCreditRule rule) {
        jdbcTemplate.update("""
                        UPDATE college_credit_rules SET
                            college = ?,
                            credit_type = ?,
                            category = ?,
                            comp_level = ?,
                            comp_name = ?,
                            award_tier = ?,
                            credits = ?,
                            category_cap = ?,
                            team_formula = ?,
                            student_cohort = ?,
                            doc_source = ?,
                            levels_json = ?,
                            notes = ?
                        WHERE id = ?
                        """,
                rule.getCollege(),
                rule.getCreditType(),
                rule.getCategory(),
                rule.getCompLevel(),
                rule.getCompName(),
                rule.getAwardTier(),
                rule.getCredits(),
                rule.getCategoryCap(),
                rule.getTeamFormula(),
                rule.getStudentCohort(),
                rule.getDocSource(),
                rule.getLevelsJson(),
                rule.getNotes(),
                rule.getId());
    }

    public CollegeCreditRule findById(Long id) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT * FROM college_credit_rules WHERE id = ?",
                    COLLEGE_CREDIT_RULE_ROW_MAPPER, id);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    /**
     * List rules with optional filters. college is matched as a partial
     * (contains) match, creditType/category are exact enum matches.
     * Blank filter values are ignored.
     */
    public List<CollegeCreditRule> findAll(String college, String creditType, String category) {
        StringBuilder sql = new StringBuilder("SELECT * FROM college_credit_rules WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (college != null && !college.isBlank()) {
            sql.append(" AND college LIKE ?");
            params.add("%" + college + "%");
        }
        if (creditType != null && !creditType.isBlank()) {
            sql.append(" AND credit_type = ?");
            params.add(creditType);
        }
        if (category != null && !category.isBlank()) {
            sql.append(" AND category = ?");
            params.add(category);
        }
        sql.append(" ORDER BY id ASC");
        return jdbcTemplate.query(sql.toString(), COLLEGE_CREDIT_RULE_ROW_MAPPER, params.toArray());
    }

    /** Repository-level insert; used by admin create and by future rule seeding. */
    public Long insert(CollegeCreditRule rule) {
        String sql = """
                INSERT INTO college_credit_rules
                    (college, credit_type, category, comp_level, comp_name, award_tier,
                     credits, category_cap, team_formula, student_cohort, doc_source,
                     levels_json, notes)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"id"});
            ps.setString(1, rule.getCollege());
            ps.setString(2, rule.getCreditType());
            ps.setString(3, rule.getCategory());
            ps.setString(4, rule.getCompLevel());
            ps.setString(5, rule.getCompName());
            ps.setString(6, rule.getAwardTier());
            ps.setObject(7, rule.getCredits());
            ps.setObject(8, rule.getCategoryCap());
            ps.setString(9, rule.getTeamFormula());
            ps.setString(10, rule.getStudentCohort());
            ps.setString(11, rule.getDocSource());
            ps.setString(12, rule.getLevelsJson());
            ps.setString(13, rule.getNotes());
            return ps;
        }, keyHolder);
        return keyHolder.getKey() != null ? keyHolder.getKey().longValue() : null;
    }
}
