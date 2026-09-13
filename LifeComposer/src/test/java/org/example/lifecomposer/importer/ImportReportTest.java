package org.example.lifecomposer.importer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImportReportTest {

    @Test
    void fileLevelErrorMakesReportUnhealthy() {
        ImportReport report = new ImportReport();
        report.error(new ImportError("missing.json", 0, null, "file not found"));

        assertTrue(report.hasErrors());
    }

    @Test
    void embeddingFailureMakesReportUnhealthy() {
        ImportReport report = new ImportReport();
        report.embeddingFailed(new ImportError("embedding", 0, "rag_001", "connection refused"));

        assertTrue(report.hasErrors());
    }

    @Test
    void cleanReportIsHealthy() {
        assertFalse(new ImportReport().hasErrors());
    }
}
