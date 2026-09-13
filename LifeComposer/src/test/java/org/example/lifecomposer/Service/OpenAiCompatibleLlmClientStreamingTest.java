package org.example.lifecomposer.Service;

import okhttp3.OkHttpClient;
import org.example.lifecomposer.config.LlmConfig.UseCaseConfig;
import org.example.lifecomposer.dto.LlmResponseDto;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiCompatibleLlmClientStreamingTest {

    @Test
    void stopsThinkingOnThinkEndMarkerBeforeVisibleToken() throws Exception {
        RecordingStreamListener listener = new RecordingStreamListener();
        consume("""
                data: {"choices":[{"delta":{"reasoning_content":"let me think "}}]}

                data: {"choices":[{"delta":{"reasoning_content":"... </think>"}}]}

                data: {"choices":[{"delta":{"content":"final answer"}}]}

                data: [DONE]

                """, listener);

        assertEquals(1, listener.thinkingStarts);
        assertEquals(1, listener.thinkingEnds);
        assertTrue(listener.order.indexOf("thinking_end") < listener.order.indexOf("token"));
        assertEquals(List.of("final answer"), listener.tokens);
    }

    @Test
    void doesNotStartThinkingWithoutReasoningContent() throws Exception {
        RecordingStreamListener listener = new RecordingStreamListener();
        consume("""
                data: {"choices":[{"delta":{"content":"just an answer"}}]}

                data: [DONE]

                """, listener);

        assertEquals(0, listener.thinkingStarts);
        assertEquals(0, listener.thinkingEnds);
        assertEquals(List.of("just an answer"), listener.tokens);
    }

    @Test
    void endsThinkingOnFirstContentWhenProviderOmitsMarker() throws Exception {
        RecordingStreamListener listener = new RecordingStreamListener();
        consume("""
                data: {"choices":[{"delta":{"reasoning_content":"reasoning without marker"}}]}

                data: {"choices":[{"delta":{"content":"answer"}}]}

                data: [DONE]

                """, listener);

        assertEquals(1, listener.thinkingStarts);
        assertEquals(1, listener.thinkingEnds);
        assertTrue(listener.order.indexOf("thinking_end") < listener.order.indexOf("token"));
    }

    @Test
    void errorsOnEmptyStream() throws Exception {
        RecordingStreamListener listener = new RecordingStreamListener();
        consume("", listener);

        assertEquals(1, listener.errors);
        assertEquals(0, listener.thinkingStarts);
        assertEquals(List.of(), listener.tokens);
    }

    @Test
    void errorsOnInvalidJsonChunks() throws Exception {
        RecordingStreamListener listener = new RecordingStreamListener();
        consume("""
                data: not-json

                """, listener);

        assertEquals(1, listener.errors);
        assertEquals(List.of(), listener.tokens);
    }

    @Test
    void errorsOnWrongChoicesType() throws Exception {
        RecordingStreamListener listener = new RecordingStreamListener();
        consume("""
                data: {"choices":"oops"}

                """, listener);

        assertEquals(1, listener.errors);
    }

    @Test
    void errorsOnPrematureEofWithoutDoneOrFinishReason() throws Exception {
        RecordingStreamListener listener = new RecordingStreamListener();
        consume("""
                data: {"choices":[{"delta":{"content":"partial"}}]}

                """, listener);

        assertEquals(1, listener.errors);
    }

    @Test
    void ignoresEmptyReasoningAndContentDeltas() throws Exception {
        RecordingStreamListener listener = new RecordingStreamListener();
        consume("""
                data: {"choices":[{"delta":{"role":"assistant","reasoning_content":""}}]}

                data: {"choices":[{"delta":{"content":""}}]}

                data: {"choices":[{"delta":{"reasoning_content":"real thinking"}}]}

                data: {"choices":[{"delta":{"reasoning_content":"... </think>"}}]}

                data: {"choices":[{"delta":{"content":"answer"}}]}

                data: [DONE]

                """, listener);

        assertEquals(1, listener.thinkingStarts);
        assertEquals(1, listener.thinkingEnds);
        assertEquals(List.of("answer"), listener.tokens);
        assertEquals(List.of("thinking_start", "thinking_end", "token"), listener.order);
    }

    private void consume(String sseBody, RecordingStreamListener listener) throws Exception {
        client().consumeStream(new BufferedReader(new StringReader(sseBody)), listener);
    }

    private OpenAiCompatibleLlmClient client() {
        UseCaseConfig config = new UseCaseConfig();
        config.setProvider("deepseek");
        config.setBaseUrl("http://localhost:1/v1");
        config.setModel("deepseek-flash");
        config.setEnabled(true);
        return new OpenAiCompatibleLlmClient(config, new OkHttpClient(), "dummy-test-key");
    }

    private static final class RecordingStreamListener implements LlmStreamListener {

        private int thinkingStarts;
        private int thinkingEnds;
        private int errors;
        private final List<String> tokens = new ArrayList<>();
        private final List<String> order = new ArrayList<>();

        @Override
        public void onThinkingStart() {
            thinkingStarts++;
            order.add("thinking_start");
        }

        @Override
        public void onThinkingEnd() {
            thinkingEnds++;
            order.add("thinking_end");
        }

        @Override
        public void onContentDelta(String delta) {
            tokens.add(delta);
            order.add("token");
        }

        @Override
        public void onError(LlmResponseDto error) {
            errors++;
        }
    }
}
