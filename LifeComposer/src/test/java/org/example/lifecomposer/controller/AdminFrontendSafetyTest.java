package org.example.lifecomposer.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Milestone 7 acceptance: the shared admin console frontend cannot inject stored
 * HTML. This is a static guarantee complementing the API tests — every value is
 * written through DOM text nodes, so the sink list below must stay empty.
 */
class AdminFrontendSafetyTest {

    private static final Path STATIC_DIR = Path.of("external", "static");
    private static final Path TEMPLATE_DIR = Path.of("external", "templates");

    private static final List<String> FORBIDDEN_SINKS = List.of(
            "innerHTML", "outerHTML", "insertAdjacentHTML", "document.write",
            "eval(", "new Function(", "javascript:");

    private static final List<String> ADMIN_TEMPLATES = List.of(
            "admin_main.html", "user_management.html", "admin_profile.html", "admin_planning.html",
            "admin_chat.html", "feedback_management.html", "admin_credit_rules.html",
            "admin_credit_activities.html", "admin_resources.html", "admin_rag.html",
            "admin_capability_tags.html", "admin_capability_reference.html", "admin_usage.html");

    @Test
    @DisplayName("admin.js never uses an HTML string sink")
    void adminJsHasNoHtmlSinks() throws IOException {
        String source = Files.readString(STATIC_DIR.resolve("admin.js"), StandardCharsets.UTF_8);

        for (String sink : FORBIDDEN_SINKS) {
            assertThat(source)
                    .as("admin.js must not use %s", sink)
                    .doesNotContain(sink);
        }
        assertThat(source).contains("textContent");
        assertThat(source).contains("createTextNode");
    }

    @Test
    @DisplayName("admin pages share one layout and never inline scripts with data")
    void adminTemplatesShareTheLayout() throws IOException {
        String adminJs = Files.readString(STATIC_DIR.resolve("admin.js"), StandardCharsets.UTF_8);

        for (String template : ADMIN_TEMPLATES) {
            Path path = TEMPLATE_DIR.resolve(template);
            assertThat(path).as("%s must exist", template).exists();
            String html = Files.readString(path, StandardCharsets.UTF_8);

            assertThat(html).as("%s must load the CSRF wrapper", template).contains("/csrf.js");
            assertThat(html).as("%s must load the shared console driver", template).contains("/admin.js");
            assertThat(html).as("%s must load the shared stylesheet", template).contains("/admin.css");

            for (String sink : FORBIDDEN_SINKS) {
                assertThat(html).as("%s must not use %s", template, sink).doesNotContain(sink);
            }

            Matcher matcher = Pattern.compile("data-admin-view=\"([A-Za-z]+)\"").matcher(html);
            assertThat(matcher.find()).as("%s must declare its console view", template).isTrue();
            String viewKey = matcher.group(1);
            assertThat(Pattern.compile("(?m)^\\s*" + viewKey + ": \\{").matcher(adminJs).find())
                    .as("admin.js must define the view '%s' used by %s", viewKey, template)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("every console view points at an /api/admin endpoint")
    void everyViewUsesAdminApi() throws IOException {
        String adminJs = Files.readString(STATIC_DIR.resolve("admin.js"), StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile("endpoint: '(/api/admin/[a-z-]+)'").matcher(adminJs);

        int count = 0;
        while (matcher.find()) {
            count += 1;
        }
        assertThat(count).as("all 13 console datasets must use /api/admin/**").isEqualTo(13);
    }
}
