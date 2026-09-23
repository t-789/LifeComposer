package org.example.lifecomposer.Service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Loads versioned prompts from {@code src/main/resources/prompts/} and exposes
 * their text plus version. Invalid/missing prompts fail fast at startup so a
 * deployment can never silently run with an empty system prompt.
 */
@Service
public class PromptCatalog {

    private static final String BASE_PATH = "prompts/";

    private final Map<PromptId, Prompt> prompts = new EnumMap<>(PromptId.class);

    public PromptCatalog() {
        load();
    }

    public String text(PromptId id) {
        return require(id).content();
    }

    public String version(PromptId id) {
        return require(id).version();
    }

    public Map<String, String> versions() {
        Map<String, String> versions = new LinkedHashMap<>();
        prompts.forEach((id, prompt) -> versions.put(id.name(), prompt.version()));
        return versions;
    }

    private Prompt require(PromptId id) {
        Prompt prompt = prompts.get(id);
        if (prompt == null) {
            throw new IllegalStateException("Prompt not loaded: " + id);
        }
        return prompt;
    }

    private void load() {
        for (PromptId id : PromptId.values()) {
            String path = BASE_PATH + id.fileName();
            ClassPathResource resource = new ClassPathResource(path);
            if (!resource.exists()) {
                throw new IllegalStateException("Missing prompt resource: " + path);
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                String raw = reader.lines().collect(Collectors.joining("\n"));
                prompts.put(id, parse(id, raw));
            } catch (IOException e) {
                throw new IllegalStateException("Unable to read prompt resource: " + path, e);
            }
        }
    }

    private Prompt parse(PromptId id, String raw) {
        String version = "unknown";
        String content = raw;
        if (raw.startsWith("---")) {
            int headerEnd = raw.indexOf("\n---", 3);
            if (headerEnd > 0) {
                String header = raw.substring(3, headerEnd);
                for (String line : header.split("\n")) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("version:")) {
                        version = trimmed.substring("version:".length()).trim();
                    }
                }
                content = raw.substring(headerEnd + 4).trim();
            }
        }
        if (content.isBlank()) {
            throw new IllegalStateException("Prompt is empty: " + id);
        }
        return new Prompt(id, version, content);
    }

    private record Prompt(PromptId id, String version, String content) {
    }
}
