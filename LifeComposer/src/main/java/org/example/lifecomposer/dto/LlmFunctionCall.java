package org.example.lifecomposer.dto;

import lombok.Getter;
import lombok.Setter;

/** OpenAI-compatible function call payload. */
@Getter
@Setter
public class LlmFunctionCall {

    private String name;
    private String arguments;

    public LlmFunctionCall() {
    }

    public LlmFunctionCall(String name, String arguments) {
        this.name = name;
        this.arguments = arguments;
    }
}
