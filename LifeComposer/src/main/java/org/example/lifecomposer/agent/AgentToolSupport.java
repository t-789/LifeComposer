package org.example.lifecomposer.agent;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Small helpers shared by the v0.0.4 read-only tools. */
public final class AgentToolSupport {

    private AgentToolSupport() {
    }

    public static String stringArg(JsonObject arguments, String name) {
        if (arguments == null || !arguments.has(name) || arguments.get(name).isJsonNull()) {
            return null;
        }
        String value = arguments.get(name).getAsString();
        return value == null || value.isBlank() ? null : value;
    }

    public static Integer intArg(JsonObject arguments, String name) {
        if (arguments == null || !arguments.has(name) || arguments.get(name).isJsonNull()) {
            return null;
        }
        JsonElement value = arguments.get(name);
        if (!value.isJsonPrimitive()) {
            return null;
        }
        try {
            return value.getAsInt();
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static Double doubleArg(JsonObject arguments, String name) {
        if (arguments == null || !arguments.has(name) || arguments.get(name).isJsonNull()) {
            return null;
        }
        try {
            return arguments.get(name).getAsDouble();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
