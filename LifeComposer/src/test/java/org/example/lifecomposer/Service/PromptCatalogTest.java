package org.example.lifecomposer.Service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Prompt contract tests: the catalog must load every versioned prompt, keep the
 * tool/confirmation boundary in the chat prompt, and require parseable JSON for
 * profile extraction.
 */
class PromptCatalogTest {

    private final PromptCatalog catalog = new PromptCatalog();

    @Test
    void loadsEveryPromptWithVersionAndContent() {
        Map<String, String> versions = catalog.versions();
        for (PromptId id : PromptId.values()) {
            assertTrue(versions.containsKey(id.name()), "missing prompt: " + id);
            assertNotNull(catalog.version(id));
            assertFalse(catalog.text(id).isBlank(), "empty prompt: " + id);
        }
        assertEquals("2", catalog.version(PromptId.CHAT_SYSTEM));
    }

    @Test
    void chatPromptKeepsToolAndConfirmationBoundary() {
        String prompt = catalog.text(PromptId.CHAT_SYSTEM);
        assertTrue(prompt.contains("propose_profile_update"));
        assertTrue(prompt.contains("不能在未确认时声称"));
        assertTrue(prompt.contains("只使用提供的工具") || prompt.contains("只能使用提供的工具"));
        assertTrue(prompt.contains("中文"));
        assertTrue(prompt.contains("不要泄露"));
    }

    @Test
    void profileExtractionPromptRequiresParseableJsonAndWhitelist() {
        String prompt = catalog.text(PromptId.PROFILE_EXTRACTION);
        assertTrue(prompt.contains("\"changes\""));
        assertTrue(prompt.contains("JSON"));
        assertTrue(prompt.contains("studentId") || prompt.contains("学号"));
        assertTrue(prompt.contains("availableTime"));
    }

    @Test
    void composerSelectsTaskPromptsAndExposesVersions() {
        PromptComposer composer = new PromptComposer(catalog);

        PromptComposer.ComposedPrompt chat = composer.forUserMessage("帮我推荐一个适合的方向");
        assertTrue(chat.versions().containsKey("PROFILE_EXTRACTION"));
        assertTrue(chat.versions().containsKey("DIRECTION_EXPLANATION"));
        assertTrue(chat.systemPrompt().contains("profile_extraction"));
        assertTrue(chat.systemPrompt().contains("direction_explanation"));

        PromptComposer.ComposedPrompt decision = composer.forDecisionContinuation();
        assertEquals(4, decision.versions().size());
        assertTrue(decision.systemPrompt().contains("path_suggestion"));
        String fingerprint = composer.fingerprint(decision.versions());
        assertTrue(fingerprint.contains("CHAT_SYSTEM=2"));
        assertTrue(fingerprint.contains("PROFILE_EXTRACTION=2"));
    }

    @Test
    void composerRecognizesCompetitionPreparationQuestions() {
        PromptComposer composer = new PromptComposer(catalog);

        PromptComposer.ComposedPrompt competition = composer.forUserMessage("我想参加蓝桥杯，应该怎么准备？");
        assertTrue(competition.versions().containsKey("DIRECTION_EXPLANATION"));
        assertTrue(competition.versions().containsKey("PATH_SUGGESTION"));

        PromptComposer.ComposedPrompt unrelated = composer.forUserMessage("你好");
        assertFalse(unrelated.versions().containsKey("DIRECTION_EXPLANATION"));
        assertTrue(unrelated.versions().containsKey("PROFILE_EXTRACTION"));
    }

    @Test
    void explanationPromptsDoNotInventScores() {
        String direction = catalog.text(PromptId.DIRECTION_EXPLANATION);
        assertTrue(direction.contains("scoreBreakdown"));
        assertTrue(direction.contains("不得重新发明评分规则") || direction.contains("不得新增"));
        String path = catalog.text(PromptId.PATH_SUGGESTION);
        assertTrue(path.contains("pathPlan"));
        assertTrue(path.contains("needs_review"));
    }
}
