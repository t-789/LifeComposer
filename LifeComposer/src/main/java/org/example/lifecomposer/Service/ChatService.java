package org.example.lifecomposer.Service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.lifecomposer.Entity.ChatMessage;
import org.example.lifecomposer.Repository.ChatMessageRepository;
import org.example.lifecomposer.agent.AgentEventListener;
import org.example.lifecomposer.agent.AgentOrchestrator;
import org.example.lifecomposer.dto.ChatResponse;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Thin facade over {@link AgentOrchestrator}. Keeps history/clear-context
 * behaviour used by existing clients while /api/chat/send and
 * /api/chat/stream share the same multi-round tool-use loop.
 */
@Service
public class ChatService {

    private static final Logger LOG = LogManager.getLogger(ChatService.class);

    private final ChatMessageRepository chatMessageRepository;
    private final AgentOrchestrator agentOrchestrator;

    public ChatService(ChatMessageRepository chatMessageRepository,
                       AgentOrchestrator agentOrchestrator) {
        this.chatMessageRepository = chatMessageRepository;
        this.agentOrchestrator = agentOrchestrator;
    }

    public ChatResponse sendMessage(Integer userId, String userMessage) {
        return agentOrchestrator.sendMessage(userId, userMessage);
    }

    public void streamMessage(Integer userId, String userMessage, AgentEventListener listener) {
        agentOrchestrator.streamMessage(userId, userMessage, listener);
    }

    public List<ChatMessage> getHistory(Integer userId) {
        return chatMessageRepository.findByUserId(userId);
    }

    public int clearContext(Integer userId) {
        LOG.info("Clearing chat context for user: {}", userId);
        return chatMessageRepository.deleteByUserId(userId);
    }
}
