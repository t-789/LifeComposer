package org.example.lifecomposer.importer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks the deliberate {@code record} contract of {@link ImportError}: four
 * accessors, value equality, the {@code record == 0} "no usable 1-based array
 * index" convention (file-level, keyed-object member and synthetic-stage
 * errors) and the JSON field names used by the import report.
 */
class ImportErrorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void exposesAllFourComponents() {
        ImportError error = new ImportError("data/student_profiles.json", 7, "stu_007", "缺少必填字段");

        assertEquals("data/student_profiles.json", error.file());
        assertEquals(7, error.record());
        assertEquals("stu_007", error.businessKey());
        assertEquals("缺少必填字段", error.reason());
    }

    @Test
    void recordZeroMarksErrorsWithoutArrayIndex() {
        // file/path level: no array index, no business key
        ImportError fileLevel = new ImportError("missing.json", 0, null, "file not found");
        assertEquals(0, fileLevel.record());
        assertNull(fileLevel.businessKey());

        // keyed-object member (capability tags / reference dictionaries): no array index,
        // the business key carries the object key
        ImportError keyedMember = new ImportError("capability-tags.json", 0, "python", "record is null");
        assertEquals(0, keyedMember.record());
        assertEquals("python", keyedMember.businessKey());

        // synthetic processing stage: the stage name replaces the file
        ImportError stage = new ImportError("embedding", 0, "rag_001", "connection refused");
        assertEquals(0, stage.record());
        assertEquals("embedding", stage.file());
        assertEquals("rag_001", stage.businessKey());
    }

    @Test
    void equalityFollowsComponents() {
        ImportError a = new ImportError("f.json", 1, "k", "bad");
        ImportError b = new ImportError("f.json", 1, "k", "bad");
        ImportError c = new ImportError("f.json", 2, "k", "bad");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }

    @Test
    void reportJsonKeepsStableErrorFieldNames() throws Exception {
        ImportReport report = new ImportReport();
        report.error(new ImportError("f.json", 0, null, "file not found"));

        JsonNode root = MAPPER.readTree(report.toPrettyJson());
        JsonNode error = root.get("errors").get(0);

        assertEquals("f.json", error.get("file").asText());
        assertEquals(0, error.get("record").asInt());
        assertTrue(error.get("businessKey").isNull());
        assertEquals("file not found", error.get("reason").asText());
        // aggregate counters keep their names as well
        assertEquals(1, root.get("failed").asInt());
        assertTrue(root.has("embeddingFailed"));
    }
}
