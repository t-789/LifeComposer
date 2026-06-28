package org.example.lifecomposer.Service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.lifecomposer.Entity.ChatMessage;
import org.example.lifecomposer.Repository.ChatMessageRepository;
import org.example.lifecomposer.config.LlmConfig;
import org.example.lifecomposer.dto.ChatResponse;
import org.example.lifecomposer.dto.ConversationMessage;
import org.example.lifecomposer.dto.LlmRequestDto;
import org.example.lifecomposer.dto.LlmResponseDto;
import org.springframework.stereotype.Service;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class ChatService {
    private static final Logger LOG = LogManager.getLogger(ChatService.class);
    private static final String SYSTEM_PROMPT = "你是一个乐于助人的大学生成长规划助手。请用中文回答问题，保持友好和专业。";

    private final ChatMessageRepository chatMessageRepository;
    private final LlmClientFactory llmClientFactory;
    private final LlmConfig llmConfig;
    private final FeedbackService feedbackService;

    public ChatService(ChatMessageRepository chatMessageRepository,
                       LlmClientFactory llmClientFactory,
                       LlmConfig llmConfig,
                       FeedbackService feedbackService) {
        this.chatMessageRepository = chatMessageRepository;
        this.llmClientFactory = llmClientFactory;
        this.llmConfig = llmConfig;
        this.feedbackService = feedbackService;
    }

    public ChatResponse sendMessage(Integer userId, String userMessage) {
        try {
            // 1. Save user message
            ChatMessage userMsg = new ChatMessage();
            userMsg.setUserId(userId);
            userMsg.setRole("user");
            userMsg.setContent(userMessage);
            userMsg.setCreateTime(new Timestamp(System.currentTimeMillis()));
            chatMessageRepository.saveMessage(userMsg);

            // 2. Load full history
            List<ChatMessage> history = chatMessageRepository.findByUserId(userId);

            // 3. Build conversation history (exclude the last message which is the current user message)
            List<ConversationMessage> conversationHistory = new ArrayList<>();
            for (int i = 0; i < history.size() - 1; i++) {
                ChatMessage msg = history.get(i);
                ConversationMessage convMsg = new ConversationMessage();
                convMsg.setRole(msg.getRole());
                convMsg.setContent(msg.getContent());
                conversationHistory.add(convMsg);
            }

            // 4. Build LLM request with context
            LlmRequestDto llmRequest = new LlmRequestDto();
            llmRequest.setMessage(userMessage);
            llmRequest.setUseCase("chat");
            llmRequest.setSystemPrompt(SYSTEM_PROMPT);
            llmRequest.setConversationHistory(conversationHistory);

            // 5. Get client and call
            LlmClient client = llmClientFactory.getClient("chat");
            LlmResponseDto response = client.chat(llmRequest);

            // 6. Save assistant response
            ChatMessage assistantMsg = new ChatMessage();
            assistantMsg.setUserId(userId);
            assistantMsg.setRole("assistant");
            assistantMsg.setContent(response.getContent() != null ? response.getContent() : "");
            assistantMsg.setCreateTime(new Timestamp(System.currentTimeMillis()));
            chatMessageRepository.saveMessage(assistantMsg);

            // 7. Return response
            return buildChatResponse(response);

        } catch (Exception e) {
            LOG.error("Chat sendMessage failed for user {}: {}", userId, e.getMessage(), e);
            ChatResponse errorResponse = new ChatResponse();
            errorResponse.setRole("assistant");
            errorResponse.setContent("抱歉，对话时出现了错误：" + e.getMessage());
            errorResponse.setMocked(true);
            errorResponse.setError(e.getMessage());
            return errorResponse;
        }
    }

    public List<ChatMessage> getHistory(Integer userId) {
        return chatMessageRepository.findByUserId(userId);
    }

    public int clearContext(Integer userId) {
        LOG.info("Clearing chat context for user: {}", userId);
        return chatMessageRepository.deleteByUserId(userId);
    }

    private ChatResponse buildChatResponse(LlmResponseDto response) {
        ChatResponse chatResponse = new ChatResponse();
        chatResponse.setRole("assistant");
        chatResponse.setContent(response.getContent() != null ? response.getContent() : "");
        chatResponse.setCreateTime(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        chatResponse.setMocked(response.isMocked());
        chatResponse.setError(response.getErrorMessage());
        return chatResponse;
    }
}
