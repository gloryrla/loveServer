package com.love.conversation;

import com.love.conversation.dto.CreateConversationRequest;
import com.love.conversation.dto.CreateMessageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    public ConversationService(ConversationRepository conversationRepository, MessageRepository messageRepository) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
    }

    @Transactional
    public Conversation createConversation(Long userId, CreateConversationRequest req) {
        Conversation c = new Conversation();
        c.setUserId(userId);
        c.setTitle(req.title());
        c.setScenarioKey(req.scenarioKey());
        return conversationRepository.save(c);
    }

    @Transactional
    public Message addMessage(Long userId, Long conversationId, CreateMessageRequest req) {
        // 내 대화방인지 체크
        conversationRepository.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new IllegalArgumentException("대화방이 없거나 접근 권한이 없습니다."));

        if (req.content() == null || req.content().isBlank()) {
            throw new IllegalArgumentException("content는 비어있을 수 없습니다.");
        }
        if (req.role() == null) {
            throw new IllegalArgumentException("role은 필수입니다.");
        }

        Message m = new Message();
        m.setConversationId(conversationId);
        m.setRole(req.role());
        m.setContent(req.content().trim());
        return messageRepository.save(m);
    }

    @Transactional(readOnly = true)
    public ConversationThread getThread(Long userId, Long conversationId) {
        Conversation c = conversationRepository.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new IllegalArgumentException("대화방이 없거나 접근 권한이 없습니다."));

        List<Message> messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
        return new ConversationThread(c, messages);
    }

    public record ConversationThread(Conversation conversation, List<Message> messages) {}

    public record ConversationSummary(
            Long id,
            String title,
            String scenarioKey,
            java.time.Instant updatedAt
    ) {}

    public List<ConversationSummary> listConversations(Long userId) {
        return conversationRepository.findByUserIdOrderByUpdatedAtDesc(userId)
                .stream()
                .map(c -> new ConversationSummary(
                        c.getId(),
                        c.getTitle(),
                        c.getScenarioKey(),
                        c.getUpdatedAt()
                ))
                .toList();
    }
}