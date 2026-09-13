package org.example.lifecomposer.importer;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ImportOptionsTest {

    @Test
    void parsesImportDirAndFlags() {
        ImportOptions options = ImportOptions.parse(new String[]{
                "--import-dir=../样例",
                "--dry-run",
                "--report=target/report.json",
                "--demo-password=secret"
        });

        assertEquals(Path.of("../样例"), options.getImportDir());
        assertTrue(options.isDryRun());
        assertEquals(Path.of("target/report.json"), options.getReportPath());
        assertEquals("secret", options.getDemoPassword());
        assertTrue(options.shouldImportResources());
        assertTrue(options.shouldImportProfiles());
        assertTrue(options.shouldImportRag());
        assertTrue(options.shouldImportCapability());
        assertTrue(options.shouldImportCreditRules());
    }

    @Test
    void explicitTypeFlagSelectsOnlyThatImporter() {
        ImportOptions options = ImportOptions.parse(new String[]{
                "--import-dir=/tmp/sample",
                "--rag-chunks"
        });

        assertFalse(options.shouldImportResources());
        assertFalse(options.shouldImportProfiles());
        assertTrue(options.shouldImportRag());
        assertFalse(options.shouldImportCapability());
        assertFalse(options.shouldImportCreditRules());
    }

    @Test
    void fillsDefaultPathsUnderImportDir() {
        ImportOptions options = ImportOptions.parse(new String[]{"--import-dir=/tmp/sample"});

        assertEquals(Path.of("/tmp/sample/data/resources.json"), options.getResourcesPath());
        assertEquals(Path.of("/tmp/sample/data/student_profiles.json"), options.getProfilesPath());
        assertEquals(Path.of("/tmp/sample/rag/chunks.json"), options.getRagChunksPath());
        assertEquals(Path.of("/tmp/sample/draft/capability-tags.json"), options.getCapabilityTagsPath());
        assertEquals(Path.of("/tmp/sample/extracted"), options.getCreditRulesDir());
    }

    @Test
    void defaultsDemoPasswordToTestuser() {
        ImportOptions options = ImportOptions.parse(new String[]{"--import-dir=/tmp/sample"});
        assertEquals("testuser", options.getDemoPassword());
    }
}
