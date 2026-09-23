package org.example.lifecomposer.agent;

import com.google.gson.JsonObject;
import org.example.lifecomposer.Entity.ChatMessage;
import org.example.lifecomposer.Repository.ChatMessageRepository;
import org.example.lifecomposer.Service.FallbackLlmClient;
import org.example.lifecomposer.Service.LlmClient;
import org.example.lifecomposer.Service.LlmClientFactory;
import org.example.lifecomposer.Service.LlmStreamListener;
import org.example.lifecomposer.config.AppSecurityProperties;
import org.example.lifecomposer.dto.ChatResponse;
import org.example.lifecomposer.dto.LlmChatMessage;
import org.example.lifecomposer.dto.LlmRequestDto;
import org.example.lifecomposer.dto.LlmResponseDto;
import org.example.lifecomposer.dto.LlmToolCall;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentOrchestratorTest {

    @Test
    void runsMultiRoundToolUseAndPersistsStructuredMessages() {
        List<ChatMessage> stored = new ArrayList<>();
        ChatMessageRepository repository = mockRepository(stored);

        ScriptedLlmClient client = new ScriptedLlmClient(List.of(
                toolCallResponse(),
                textResponse("最终回答")));
        LlmClientFactory factory = mock(LlmClientFactory.class);
        when(factory.getClient("chat")).thenReturn(client);

        ToolRegistry registry = new ToolRegistry(List.of(new FakeSearchResourcesTool()));
        AgentOrchestrator orchestrator = new AgentOrchestrator(repository, factory, registry, new AppSecurityProperties());

        ChatResponse response = orchestrator.sendMessage(1, "帮我找数学建模");

        assertEquals("最终回答", response.getContent());
        assertNotNull(response.getPromptVersions());
        assertTrue(response.getPromptVersions().containsKey("PROFILE_EXTRACTION"),
                "普通聊天也应启用结构化画像提取 Prompt");
        assertEquals(4, stored.size());
        assertEquals("user", stored.get(0).getRole());
        assertEquals("帮我找数学建模", stored.get(0).getContent());

        assertEquals("assistant", stored.get(1).getRole());
        assertTrue(stored.get(1).getContent().contains("\"type\":\"tool_call\""));
        assertTrue(stored.get(1).getContent().contains("\"callId\":\"call-1\""));

        assertEquals("tool", stored.get(2).getRole());
        assertTrue(stored.get(2).getContent().contains("\"type\":\"tool_result\""));
        assertTrue(stored.get(2).getContent().contains("\"callId\":\"call-1\""));

        assertEquals("assistant", stored.get(3).getRole());
        assertEquals("最终回答", stored.get(3).getContent());
        assertNotNull(stored.get(3).getPromptVersion(), "assistant 消息应持久化 Prompt 版本指纹");
        assertTrue(stored.get(3).getPromptVersion().contains("CHAT_SYSTEM="));

        // Second LLM call must receive the reconstructed tool result message.
        List<LlmRequestDto> requests = client.requests;
        assertEquals(2, requests.size());
        boolean hasToolMessage = requests.get(1).getMessages().stream()
                .anyMatch(message -> "tool".equals(message.getRole()));
        assertTrue(hasToolMessage);
        boolean hasAssistantToolCalls = requests.get(1).getMessages().stream()
                .anyMatch(message -> message.getToolCalls() != null && !message.getToolCalls().isEmpty());
        assertTrue(hasAssistantToolCalls);
    }

    @Test
    void groupsMultipleToolCallsIntoOneAssistantMessage() {
        List<ChatMessage> stored = new ArrayList<>();
        ChatMessageRepository repository = mockRepository(stored);

        ScriptedLlmClient client = new ScriptedLlmClient(List.of(
                multiToolCallResponse(),
                textResponse("done")));
        LlmClientFactory factory = mock(LlmClientFactory.class);
        when(factory.getClient("chat")).thenReturn(client);

        ToolRegistry registry = new ToolRegistry(List.of(new FakeSearchResourcesTool()));
        AgentOrchestrator orchestrator = new AgentOrchestrator(repository, factory, registry, new AppSecurityProperties());

        assertEquals("done", orchestrator.sendMessage(1, "查两个方向").getContent());

        List<LlmChatMessage> secondMessages = client.requests.get(1).getMessages();
        long assistantToolCallMessages = secondMessages.stream()
                .filter(message -> message.getToolCalls() != null && !message.getToolCalls().isEmpty())
                .count();
        long toolMessages = secondMessages.stream()
                .filter(message -> "tool".equals(message.getRole()))
                .count();

        assertEquals(1, assistantToolCallMessages);
        assertEquals(2, toolMessages);
        LlmChatMessage assistantWithTools = secondMessages.stream()
                .filter(message -> message.getToolCalls() != null && !message.getToolCalls().isEmpty())
                .findFirst()
                .orElseThrow();
        assertEquals(2, assistantWithTools.getToolCalls().size());
        assertEquals("call-1", assistantWithTools.getToolCalls().get(0).getId());
        assertEquals("call-2", assistantWithTools.getToolCalls().get(1).getId());
    }

    @Test
    void keepsSeparateToolRoundsSeparate() {
        List<ChatMessage> stored = new ArrayList<>();
        ChatMessageRepository repository = mockRepository(stored);

        ScriptedLlmClient client = new ScriptedLlmClient(List.of(
                singleToolCallResponse("call-1", "数学建模"),
                singleToolCallResponse("call-2", "蓝桥杯"),
                textResponse("done")));
        LlmClientFactory factory = mock(LlmClientFactory.class);
        when(factory.getClient("chat")).thenReturn(client);

        ToolRegistry registry = new ToolRegistry(List.of(new FakeSearchResourcesTool()));
        AgentOrchestrator orchestrator = new AgentOrchestrator(repository, factory, registry, new AppSecurityProperties());

        assertEquals("done", orchestrator.sendMessage(1, "两轮各调用一个工具").getContent());

        List<LlmChatMessage> thirdRequestMessages = client.requests.get(2).getMessages();
        List<LlmChatMessage> toolCallMessages = thirdRequestMessages.stream()
                .filter(message -> message.getToolCalls() != null && !message.getToolCalls().isEmpty())
                .toList();
        assertEquals(2, toolCallMessages.size());
        assertEquals(1, toolCallMessages.get(0).getToolCalls().size());
        assertEquals(1, toolCallMessages.get(1).getToolCalls().size());
        assertEquals("call-1", toolCallMessages.get(0).getToolCalls().get(0).getId());
        assertEquals("call-2", toolCallMessages.get(1).getToolCalls().get(0).getId());
        assertEquals(2, thirdRequestMessages.stream()
                .filter(message -> "tool".equals(message.getRole()))
                .count());
    }

    @Test
    void forwardsThinkingEndFromProviderBeforeTokens() {
        List<ChatMessage> stored = new ArrayList<>();
        ChatMessageRepository repository = mockRepository(stored);

        StreamingScriptedLlmClient client = new StreamingScriptedLlmClient();
        LlmClientFactory factory = mock(LlmClientFactory.class);
        when(factory.getClient("chat")).thenReturn(client);

        ToolRegistry registry = new ToolRegistry(List.of());
        AgentOrchestrator orchestrator = new AgentOrchestrator(repository, factory, registry, new AppSecurityProperties());
        RecordingAgentEventListener listener = new RecordingAgentEventListener();

        orchestrator.streamMessage(1, "hi", listener);

        assertEquals(1, listener.thinkingStarts);
        assertEquals(1, listener.thinkingEnds);
        assertTrue(listener.tokens.contains("你好"));
        assertTrue(listener.order.indexOf("thinking_end") < listener.order.indexOf("token"),
                "thinking_end must be sent before the first visible token");
    }

    @Test
    void doesNotPersistAssistantMessageWhenSseSendFails() {
        List<ChatMessage> stored = new ArrayList<>();
        ChatMessageRepository repository = mockRepository(stored);

        LlmClientFactory factory = mock(LlmClientFactory.class);
        when(factory.getClient("chat")).thenReturn(new CompletingStreamingLlmClient(textResponse("answer")));

        AgentOrchestrator orchestrator = new AgentOrchestrator(
                repository, factory, new ToolRegistry(List.of()), new AppSecurityProperties());
        AgentEventListener failingListener = new AgentEventListener() {
            @Override
            public void onAssistantMessage(String content, String createTime) {
                throw new RuntimeException("client disconnected");
            }
        };

        assertThrows(RuntimeException.class,
                () -> orchestrator.streamMessage(1, "hi", failingListener));

        assertEquals(1, stored.size());
        assertEquals("user", stored.get(0).getRole());
        assertTrue(stored.stream().noneMatch(message -> "assistant".equals(message.getRole())));
    }

    @Test
    void doesNotPersistToolCallWhenSseSendFails() {
        List<ChatMessage> stored = new ArrayList<>();
        ChatMessageRepository repository = mockRepository(stored);

        LlmClientFactory factory = mock(LlmClientFactory.class);
        when(factory.getClient("chat")).thenReturn(new CompletingStreamingLlmClient(toolCallResponse()));

        AgentOrchestrator orchestrator = new AgentOrchestrator(
                repository, factory, new ToolRegistry(List.of(new FakeSearchResourcesTool())),
                new AppSecurityProperties());
        AgentEventListener failingListener = new AgentEventListener() {
            @Override
            public void onToolCall(String callId, String name, String description, String argumentsJson) {
                throw new RuntimeException("client disconnected");
            }
        };

        assertThrows(RuntimeException.class,
                () -> orchestrator.streamMessage(1, "hi", failingListener));

        assertEquals(1, stored.size());
        assertEquals("user", stored.get(0).getRole());
        assertTrue(stored.stream().noneMatch(message -> "assistant".equals(message.getRole())));
        assertTrue(stored.stream().noneMatch(message -> "tool".equals(message.getRole())));
    }

    @Test
    void throwsLlmUnavailableForFallbackClient() {
        ChatMessageRepository repository = mock(ChatMessageRepository.class);
        when(repository.findByUserId(1)).thenAnswer(invocation -> new ArrayList<>());
        when(repository.saveMessage(any(ChatMessage.class))).thenReturn(1);

        LlmClientFactory factory = mock(LlmClientFactory.class);
        when(factory.getClient("chat")).thenReturn(new FallbackLlmClient("deepseek", "deepseek-flash"));

        AgentOrchestrator orchestrator = new AgentOrchestrator(
                repository, factory, new ToolRegistry(List.of()), new AppSecurityProperties());

        assertThrows(LlmUnavailableException.class,
                () -> orchestrator.sendMessage(1, "你好"));
    }

    @Test
    void capsRequestedMaxTokensToConfiguredLimit() {
        ChatMessageRepository repository = mockRepository(new ArrayList<>());
        LlmClientFactory factory = mock(LlmClientFactory.class);
        ScriptedLlmClient client = new ScriptedLlmClient(List.of(textResponse("ok")));
        when(factory.getClient("chat")).thenReturn(client);

        AgentOrchestrator orchestrator = new AgentOrchestrator(
                repository, factory, new ToolRegistry(List.of()), new AppSecurityProperties());

        orchestrator.sendMessage(1, "hi", 9999);

        assertEquals(1024, client.requests.get(0).getMaxTokens());
    }

    @Test
    void injectsRecommendationContractWhenRecommendationToolWasCalled() {
        List<ChatMessage> stored = new ArrayList<>();
        ChatMessageRepository repository = mockRepository(stored);

        ScriptedLlmClient client = new ScriptedLlmClient(List.of(
                recommendationToolCallResponse(),
                textResponse("done")));
        LlmClientFactory factory = mock(LlmClientFactory.class);
        when(factory.getClient("chat")).thenReturn(client);

        ToolRegistry registry = new ToolRegistry(List.of(new FakeListGrowthDirectionsTool()));
        AgentOrchestrator orchestrator = new AgentOrchestrator(
                repository, factory, registry, new AppSecurityProperties());

        ChatResponse response = orchestrator.sendMessage(1, "帮我看看");
        assertEquals("done", response.getContent());

        List<LlmChatMessage> secondRequest = client.requests.get(1).getMessages();
        boolean hasContract = secondRequest.stream()
                .filter(message -> "system".equals(message.getRole()))
                .anyMatch(message -> message.getContent() != null
                        && message.getContent().contains("direction_explanation")
                        && message.getContent().contains("path_suggestion"));
        assertTrue(hasContract, "实际调用推荐工具后，后续轮次应补上方向解释/路径建议契约");
        assertTrue(response.getPromptVersions().containsKey("DIRECTION_EXPLANATION"));
        assertTrue(response.getPromptVersions().containsKey("PATH_SUGGESTION"));
    }

    @Test
    void keepsSmallerClientMaxTokens() {
        ChatMessageRepository repository = mockRepository(new ArrayList<>());
        LlmClientFactory factory = mock(LlmClientFactory.class);
        ScriptedLlmClient client = new ScriptedLlmClient(List.of(textResponse("ok")));
        when(factory.getClient("chat")).thenReturn(client);

        AgentOrchestrator orchestrator = new AgentOrchestrator(
                repository, factory, new ToolRegistry(List.of()), new AppSecurityProperties());

        orchestrator.sendMessage(1, "hi", 100);

        assertEquals(100, client.requests.get(0).getMaxTokens());
    }

    private ChatMessageRepository mockRepository(List<ChatMessage> stored) {
        ChatMessageRepository repository = mock(ChatMessageRepository.class);
        when(repository.findByUserId(1)).thenAnswer(invocation -> new ArrayList<>(stored));
        when(repository.saveMessage(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage message = invocation.getArgument(0);
            message.setId(stored.size() + 1);
            stored.add(message);
            return message.getId();
        });
        doAnswer(invocation -> {
            List<ChatMessage> messages = invocation.getArgument(0);
            for (ChatMessage message : messages) {
                message.setId(stored.size() + 1);
                stored.add(message);
            }
            return null;
        }).when(repository).saveMessages(anyList());
        return repository;
    }

    private LlmResponseDto recommendationToolCallResponse() {
        LlmResponseDto response = new LlmResponseDto();
        response.setToolCalls(List.of(new LlmToolCall(
                "call-1", "list_growth_directions", "{}")));
        return response;
    }

    private LlmResponseDto toolCallResponse() {
        LlmResponseDto response = new LlmResponseDto();
        response.setToolCalls(List.of(new LlmToolCall(
                "call-1", "search_resources", "{\"keyword\":\"数学建模\"}")));
        return response;
    }

    private LlmResponseDto singleToolCallResponse(String callId, String keyword) {
        LlmResponseDto response = new LlmResponseDto();
        response.setToolCalls(List.of(new LlmToolCall(
                callId, "search_resources", "{\"keyword\":\"" + keyword + "\"}")));
        return response;
    }

    private LlmResponseDto multiToolCallResponse() {
        LlmResponseDto response = new LlmResponseDto();
        response.setToolCalls(List.of(
                new LlmToolCall("call-1", "search_resources", "{\"keyword\":\"数学建模\"}"),
                new LlmToolCall("call-2", "search_resources", "{\"keyword\":\"蓝桥杯\"}")));
        return response;
    }

    private LlmResponseDto textResponse(String content) {
        LlmResponseDto response = new LlmResponseDto();
        response.setContent(content);
        return response;
    }

    private static final class ScriptedLlmClient implements LlmClient {

        private final List<LlmResponseDto> responses;
        private final List<LlmRequestDto> requests = new ArrayList<>();
        private int index;

        private ScriptedLlmClient(List<LlmResponseDto> responses) {
            this.responses = responses;
        }

        @Override
        public LlmResponseDto chat(LlmRequestDto request) {
            requests.add(request);
            int current = Math.min(index, responses.size() - 1);
            index++;
            return responses.get(current);
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public String getProvider() {
            return "deepseek";
        }

        @Override
        public String getModel() {
            return "deepseek-flash";
        }
    }

    private static final class StreamingScriptedLlmClient implements LlmClient {

        @Override
        public LlmResponseDto chat(LlmRequestDto request) {
            throw new UnsupportedOperationException("chat() not used in streaming test");
        }

        @Override
        public void chatStream(LlmRequestDto request, LlmStreamListener listener) {
            listener.onThinkingStart();
            listener.onReasoningDelta("thinking about the answer");
            listener.onThinkingEnd();
            listener.onContentDelta("你好");
            LlmResponseDto response = new LlmResponseDto();
            response.setContent("你好");
            listener.onComplete(response);
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public String getProvider() {
            return "deepseek";
        }

        @Override
        public String getModel() {
            return "deepseek-flash";
        }
    }

    private static final class CompletingStreamingLlmClient implements LlmClient {

        private final LlmResponseDto response;

        private CompletingStreamingLlmClient(LlmResponseDto response) {
            this.response = response;
        }

        @Override
        public LlmResponseDto chat(LlmRequestDto request) {
            return response;
        }

        @Override
        public void chatStream(LlmRequestDto request, LlmStreamListener listener) {
            listener.onComplete(response);
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public String getProvider() {
            return "deepseek";
        }

        @Override
        public String getModel() {
            return "deepseek-flash";
        }
    }

    private static final class RecordingAgentEventListener implements AgentEventListener {

        private int thinkingStarts;
        private int thinkingEnds;
        private final List<String> tokens = new ArrayList<>();
        private final List<String> order = new ArrayList<>();

        @Override
        public void onThinkingStart(java.time.Instant startedAt) {
            thinkingStarts++;
            order.add("thinking_start");
        }

        @Override
        public void onThinkingEnd(long elapsedMs) {
            thinkingEnds++;
            order.add("thinking_end");
        }

        @Override
        public void onToken(String delta) {
            tokens.add(delta);
            order.add("token");
        }
    }

    private static final class FakeListGrowthDirectionsTool implements AgentTool {

        @Override
        public String name() {
            return "list_growth_directions";
        }

        @Override
        public String description() {
            return "列出成长方向";
        }

        @Override
        public String displayDescription() {
            return "计算成长方向推荐";
        }

        @Override
        public Map<String, Object> parameterSchema() {
            return Map.of("type", "object", "properties", Map.of(), "additionalProperties", false);
        }

        @Override
        public ToolResult execute(AgentToolContext context, JsonObject arguments) {
            return ToolResult.ok(Map.of("count", 1, "recommendations", List.of()));
        }
    }

    private static final class FakeSearchResourcesTool implements AgentTool {

        @Override
        public String name() {
            return "search_resources";
        }

        @Override
        public String description() {
            return "搜索资源";
        }

        @Override
        public String displayDescription() {
            return "检索成长资源库";
        }

        @Override
        public Map<String, Object> parameterSchema() {
            return Map.of(
                    "type", "object",
                    "properties", Map.of("keyword", Map.of("type", "string")),
                    "additionalProperties", false);
        }

        @Override
        public ToolResult execute(AgentToolContext context, JsonObject arguments) {
            return ToolResult.ok(Map.of("count", 1, "items", List.of("数学建模竞赛")));
        }
    }
}
