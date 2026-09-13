package org.example.lifecomposer.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.lifecomposer.dto.LlmRequestDto;
import org.example.lifecomposer.dto.LlmResponseDto;
import org.example.lifecomposer.dto.QaRequest;
import org.example.lifecomposer.dto.QaResponse;
import org.example.lifecomposer.config.LlmConfig;
import org.springframework.stereotype.Service;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Service
public class QaService {

    private final LlmClientFactory llmClientFactory;
    private final PlanningHistoryService planningHistoryService;
    private final LlmConfig llmConfig;

    private static final Logger LOG = LogManager.getLogger(QaService.class);
    private final FeedbackService feedbackService;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public QaService(LlmClientFactory llmClientFactory,
                     PlanningHistoryService planningHistoryService,
                     LlmConfig llmConfig,
                     FeedbackService feedbackService) {
        this.llmClientFactory = llmClientFactory;
        this.planningHistoryService = planningHistoryService;
        this.llmConfig = llmConfig;
        this.feedbackService = feedbackService;
    }

    public QaResponse askQuestion(Long userId, QaRequest request) {
        String useCase = request.getUseCase() != null ? request.getUseCase() : "qa";
        LlmConfig.UseCaseConfig uc = llmConfig.resolveOrDefault(useCase);

        // Validate useCase: if explicitly provided but not found in config and not the default "qa"
        if (!"qa".equals(useCase) && !llmConfig.getUseCases().containsKey(useCase.toLowerCase())) {
            throw new IllegalStateException("未知的 useCase: " + useCase);
        }

        LlmClient client = llmClientFactory.getClient(useCase);

        try {
            LlmRequestDto llmRequest = new LlmRequestDto();
            llmRequest.setMessage(request.getMessage());
            llmRequest.setUseCase(useCase);

            LlmResponseDto llmResponse = client.chat(llmRequest);

            // Only /api/chat/* forbids fallback. Other generative use cases keep
            // the configurable mock fallback for provider failures.
            if (llmResponse.isFailed()) {
                return fallbackMock(userId, request, useCase, llmResponse.getErrorMessage());
            }

            QaResponse response = mapToQaResponse(llmResponse);
            String requestJson = toJson(request);
            String responseJson = toJson(response);

            Long historyId = planningHistoryService.appendRecord(
                    userId, "QA", requestJson, responseJson,
                    response.getProvider(), response.getModel(),
                    response.isMocked() ? "MOCKED" : (llmResponse.isFailed() ? "FAILED" : "SUCCESS"),
                    llmResponse.getErrorMessage()
            );
            response.setHistoryId(historyId);
            return response;
        } catch (Exception e) {
            // Do not persist the full prompt in logs/feedback; it may contain
            // user-sensitive content. Use-case and exception are enough for triage.
            LogHelper.logError(LOG, feedbackService, "QA请求失败 useCase=" + useCase, e, "/api/qa/ask");
            return fallbackMock(userId, request, useCase, e.getMessage());
        }
    }

    private QaResponse mapToQaResponse(LlmResponseDto llmResponse) {
        QaResponse response = new QaResponse();
        response.setAnswer(llmResponse.getContent());
        response.setMocked(llmResponse.isMocked());
        response.setProvider(llmResponse.getProvider());
        response.setModel(llmResponse.getModel());
        response.setTimestamp(llmResponse.getTimestamp());
        response.setError(llmResponse.getErrorMessage());
        return response;
    }

    private QaResponse fallbackMock(Long userId, QaRequest request, String useCase, String errorMsg) {
        QaResponse response = new QaResponse();
        response.setAnswer("[Mock] 你的问题是: " + request.getMessage() + " (useCase=" + useCase + ")");
        response.setMocked(true);
        response.setProvider("mock");
        LlmConfig.UseCaseConfig uc = llmConfig.resolveOrDefault(useCase);
        response.setModel(uc.getModel());
        response.setError(errorMsg);

        String requestJson = toJson(request);
        String responseJson = toJson(response);
        Long historyId = planningHistoryService.appendRecord(
                userId, "QA", requestJson, responseJson,
                "mock", response.getModel(),
                "FAILED", errorMsg
        );
        response.setHistoryId(historyId);
        return response;
    }

    private String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}
