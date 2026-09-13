package org.example.lifecomposer.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/** Tool definition sent to an OpenAI-compatible model. */
@Getter
@Setter
public class LlmToolDefinition {

    private String name;
    private String description;
    private Map<String, Object> parameters;

    public LlmToolDefinition() {
    }

    public LlmToolDefinition(String name, String description, Map<String, Object> parameters) {
        this.name = name;
        this.description = description;
        this.parameters = parameters;
    }
}
