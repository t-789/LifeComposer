package org.example.lifecomposer.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * v0.1 M3: composes the system prompt from versioned catalog entries and keeps
 * the version map that is attached to agent responses and persisted messages.
 *
 * <p>Normal chat turns always carry the profile-extraction instructions because
 * the agent may need to propose profile candidates. Recommendation/path
 * questions additionally carry the direction-explanation and path-suggestion
 * contracts; decision continuations carry all four.
 */
public class PromptComposer {

    private static final List<String> RECOMMENDATION_KEYWORDS = List.of(
            "推荐", "方向", "路径", "适合", "为什么", "差哪", "缺少", "提升", "规划", "资源", "竞赛", "课程",
            "比赛", "赛事", "参赛", "报名", "准备", "怎么准备", "如何准备", "学习路线", "入门", "刷题",
            "拿奖", "获奖", "技能", "能力", "缺什么", "差什么", "蓝桥杯", "ACM", "ICPC", "数模", "数学建模",
            "电赛", "挑战杯", "互联网+", "大创");

    private final PromptCatalog catalog;

    public PromptComposer(PromptCatalog catalog) {
        this.catalog = catalog;
    }

    public record ComposedPrompt(String systemPrompt, Map<String, String> versions) {
    }

    public ComposedPrompt forUserMessage(String userMessage) {
        LinkedHashMap<PromptId, Boolean> selected = new LinkedHashMap<>();
        selected.put(PromptId.CHAT_SYSTEM, true);
        selected.put(PromptId.PROFILE_EXTRACTION, true);
        if (looksLikeRecommendationQuestion(userMessage)) {
            selected.put(PromptId.DIRECTION_EXPLANATION, true);
            selected.put(PromptId.PATH_SUGGESTION, true);
        }
        return compose(selected.keySet().stream().toList());
    }

    public ComposedPrompt forDecisionContinuation() {
        return compose(List.of(
                PromptId.CHAT_SYSTEM,
                PromptId.PROFILE_EXTRACTION,
                PromptId.DIRECTION_EXPLANATION,
                PromptId.PATH_SUGGESTION));
    }

    /**
     * Contract appended when a recommendation/path tool was actually called,
     * even if the user's original wording did not match the keyword heuristic.
     */
    public ComposedPrompt recommendationContract() {
        return compose(List.of(PromptId.DIRECTION_EXPLANATION, PromptId.PATH_SUGGESTION));
    }

    public ComposedPrompt forProfileExtractionOnly() {
        return compose(List.of(PromptId.CHAT_SYSTEM, PromptId.PROFILE_EXTRACTION));
    }

    /** Stable fingerprint stored on assistant messages for traceability. */
    public String fingerprint(Map<String, String> versions) {
        StringBuilder builder = new StringBuilder();
        for (PromptId id : PromptId.values()) {
            String version = versions.get(id.name());
            if (version == null) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(';');
            }
            builder.append(id.name()).append('=').append(version);
        }
        return builder.toString();
    }

    private boolean looksLikeRecommendationQuestion(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return false;
        }
        String lower = userMessage.toLowerCase(Locale.ROOT);
        for (String keyword : RECOMMENDATION_KEYWORDS) {
            if (lower.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private ComposedPrompt compose(List<PromptId> ids) {
        StringBuilder builder = new StringBuilder();
        Map<String, String> versions = new LinkedHashMap<>();
        for (PromptId id : ids) {
            String version = catalog.version(id);
            versions.put(id.name(), version);
            builder.append("## ").append(id.name()).append(" [").append(id.fileName())
                    .append("] (v").append(version).append(")\n");
            builder.append(catalog.text(id)).append("\n\n");
        }
        return new ComposedPrompt(builder.toString().trim(), versions);
    }
}
