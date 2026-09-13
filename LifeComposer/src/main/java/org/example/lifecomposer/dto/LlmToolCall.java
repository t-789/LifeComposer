package org.example.lifecomposer.dto;

import lombok.Getter;
import lombok.Setter;

/** One tool call requested by the model. */
@Getter
@Setter
public class LlmToolCall {

    private String id;
    private String type = "function";
    private LlmFunctionCall function;

    public LlmToolCall() {
    }

    public LlmToolCall(String id, String name, String arguments) {
        this.id = id;
        this.type = "function";
        this.function = new LlmFunctionCall(name, arguments);
    }

    public String name() {
        return function != null ? function.getName() : null;
    }

    public String arguments() {
        return function != null ? function.getArguments() : null;
    }
}
