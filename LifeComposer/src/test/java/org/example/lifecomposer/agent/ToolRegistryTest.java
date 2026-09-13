package org.example.lifecomposer.agent;

import com.google.gson.JsonObject;
import org.example.lifecomposer.dto.LlmToolDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ToolRegistryTest {

    @Test
    void exposesDefinitionsAndExecutesKnownTool() {
        ToolRegistry registry = new ToolRegistry(List.of(new EchoTool()));

        List<LlmToolDefinition> definitions = registry.definitions();
        assertEquals(1, definitions.size());
        assertEquals("echo_tool", definitions.get(0).getName());

        ToolResult result = registry.execute("echo_tool", new AgentToolContext(7), "{\"value\":\"hi\"}");
        assertTrue(result.ok());
        assertEquals("hi", ((Map<?, ?>) result.data()).get("echo"));
    }

    @Test
    void rejectsUnknownTool() {
        ToolRegistry registry = new ToolRegistry(List.of());
        ToolResult result = registry.execute("missing", new AgentToolContext(1), "{}");
        assertFalse(result.ok());
        assertEquals("UNKNOWN_TOOL", result.errorCode());
    }

    @Test
    void rejectsInvalidJsonArguments() {
        ToolRegistry registry = new ToolRegistry(List.of(new EchoTool()));
        ToolResult result = registry.execute("echo_tool", new AgentToolContext(1), "not-json");
        assertFalse(result.ok());
        assertEquals("INVALID_ARGUMENTS", result.errorCode());
    }

    private static final class EchoTool implements AgentTool {
        @Override
        public String name() {
            return "echo_tool";
        }

        @Override
        public String description() {
            return "echo";
        }

        @Override
        public String displayDescription() {
            return "回显工具";
        }

        @Override
        public Map<String, Object> parameterSchema() {
            return Map.of("type", "object", "properties", Map.of("value", Map.of("type", "string")));
        }

        @Override
        public ToolResult execute(AgentToolContext context, JsonObject arguments) {
            String value = arguments.has("value") ? arguments.get("value").getAsString() : null;
            return ToolResult.ok(Map.of("echo", value == null ? "" : value));
        }
    }
}
