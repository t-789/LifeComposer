package org.example.lifecomposer.importer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/** Aggregate import report shared by every importer. */
@Getter
public class ImportReport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private int inserted;
    private int updated;
    private int skipped;
    private int failed;
    private int embeddingFailed;
    private final List<ImportError> errors = new ArrayList<>();

    public void inserted() {
        inserted++;
    }

    public void updated() {
        updated++;
    }

    public void skipped() {
        skipped++;
    }

    public void failed(ImportError error) {
        failed++;
        errors.add(error);
    }

    public void error(ImportError error) {
        failed++;
        errors.add(error);
    }

    public void embeddingFailed(ImportError error) {
        embeddingFailed++;
        failed++;
        errors.add(error);
    }

    /** True when any record, file-level or embedding error was recorded. */
    public boolean hasErrors() {
        return failed > 0 || embeddingFailed > 0 || !errors.isEmpty();
    }

    public String toPrettyJson() {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return "{\"inserted\":" + inserted + ",\"updated\":" + updated
                    + ",\"skipped\":" + skipped + ",\"failed\":" + failed
                    + ",\"embeddingFailed\":" + embeddingFailed + "}";
        }
    }
}
