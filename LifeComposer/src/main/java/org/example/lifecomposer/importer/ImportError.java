package org.example.lifecomposer.importer;

/** One per-record import validation or write failure. */
public record ImportError(String file, int record, String businessKey, String reason) {
}
