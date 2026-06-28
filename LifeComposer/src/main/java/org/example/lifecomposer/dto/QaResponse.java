package org.example.lifecomposer.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Response DTO for the Q&A ask endpoint.
 */
@Getter
@Setter
public class QaResponse {

    private String answer;
    private boolean mocked;
    private String provider;
    private String model;
    private Long historyId;
    private long timestamp;
    private String error;

    public QaResponse() {
        this.timestamp = System.currentTimeMillis();
    }
}
