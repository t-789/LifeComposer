package org.example.lifecomposer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.lifecomposer.Service.CapabilityPreflightService;
import org.example.lifecomposer.importer.DataImportService;
import org.example.lifecomposer.importer.ImportOptions;
import org.example.lifecomposer.importer.ImportReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * v0.1 M4 preflight, now driven by the real {@link DataImportService} path
 * (the same code used by {@code import.sh}) instead of hand-built fixtures.
 *
 * <p>The report records real unmatched skills/tags; the test also asserts the
 * unmatched list is non-empty for a deliberately unknown skill so a future
 * regression cannot silently hardcode an empty list.
 */
@SpringBootTest
@ActiveProfiles("test")
class CapabilityNormalizationPreflightTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private DataImportService dataImportService;

    @Autowired
    private CapabilityPreflightService capabilityPreflightService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanup() {
        jdbcTemplate.execute("DELETE FROM resources");
        jdbcTemplate.execute("DELETE FROM capability_reference");
        jdbcTemplate.execute("DELETE FROM capability_tags");
    }

    @Test
    @DisplayName("preflight: import.sh path imports dictionaries, then reports 100% skill coverage")
    void importPathAndCoverageReport() throws Exception {
        Path sampleRoot = sampleRoot();
        ImportOptions options = ImportOptions.parse(new String[]{
                "--import-dir", sampleRoot.toString(), "--resources", "--capability-tags"});
        ImportReport importReport = dataImportService.run(options);

        assertEquals(0, importReport.getFailed(), "样例资源/能力字典导入不应有失败记录");
        assertTrue(importReport.getInserted() > 0, "首次导入应产生插入计数");
        assertEquals(10, count("capability_tags"));
        assertEquals(68, count("capability_reference"));
        assertEquals(18, count("resources"));

        Map<String, Object> preflight = capabilityPreflightService.preflight(
                sampleRoot.resolve("data/student_profiles.json"));

        assertEquals(1.0, (double) preflight.get("skillCoverage"), 0.0001,
                "样例技能归一覆盖率应为 100%");
        assertTrue(((List<?>) preflight.get("unmatchedSkills")).isEmpty());
        assertFalse(((List<?>) preflight.get("templateExpansions")).isEmpty(),
                "资源技能模板必须被展开并记录");
        assertTrue(((List<?>) preflight.get("unmatchedTemplateTags")).isEmpty(),
                "模板展开结果必须全部是标准标签");

        Map<String, Object> output = new LinkedHashMap<>(preflight);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("inserted", importReport.getInserted());
        summary.put("updated", importReport.getUpdated());
        summary.put("skipped", importReport.getSkipped());
        summary.put("failed", importReport.getFailed());
        output.put("importSummary", summary);
        writeReport(output);
    }

    @Test
    @DisplayName("dry-run import reports counts without writing rows")
    void dryRunImportDoesNotWrite() {
        Path sampleRoot = sampleRoot();
        ImportOptions options = ImportOptions.parse(new String[]{
                "--import-dir", sampleRoot.toString(), "--capability-tags", "--dry-run"});
        ImportReport report = dataImportService.run(options);

        assertTrue(report.getInserted() > 0, "dry-run 仍应报告将要插入的行数");
        assertEquals(0, count("capability_tags"));
        assertEquals(0, count("capability_reference"));
    }

    @Test
    @DisplayName("preflight report records concrete unmatched skills instead of an empty placeholder")
    void unmatchedSkillsAreRecorded() throws Exception {
        Path sampleRoot = sampleRoot();
        dataImportService.run(ImportOptions.parse(new String[]{
                "--import-dir", sampleRoot.toString(), "--capability-tags"}));

        Path fixture = Path.of("target", "unmatched-profiles.json");
        Files.createDirectories(fixture.getParent());
        Files.writeString(fixture, "[{\"major\":\"测试专业\",\"skills\":[\"完全未知技能XYZ\",\"Python基础\"]}]",
                StandardCharsets.UTF_8);

        Map<String, Object> report = capabilityPreflightService.preflight(fixture);
        @SuppressWarnings("unchecked")
        List<String> unmatched = (List<String>) report.get("unmatchedSkills");
        assertTrue(unmatched.contains("完全未知技能XYZ"), "未匹配技能必须逐项记录");
        assertEquals(0.5, (double) report.get("skillCoverage"), 0.0001);
    }

    private int count(String table) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return count == null ? 0 : count;
    }

    private void writeReport(Map<String, Object> report) throws Exception {
        Path out = Path.of("target", "capability-preflight-report.json");
        Files.createDirectories(out.getParent());
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(out.toFile(), report);
    }

    private Path sampleRoot() {
        String cwd = System.getProperty("user.dir");
        for (Path candidate : List.of(Path.of(cwd, "样例"), Path.of(cwd, "..", "样例"))) {
            if (Files.isDirectory(candidate)) {
                return candidate.toAbsolutePath().normalize();
            }
        }
        throw new IllegalStateException("无法定位样例目录");
    }
}
