package com.love.conversation;

import com.love.ai.AiChatClient;
import com.love.ai.dto.AiChatRequest;
import com.love.ai.dto.AiChatResponse;
import com.love.ai.dto.ReportRequest;
import com.love.ai.dto.ReportResponse;
import com.love.conversation.dto.CreateConversationRequest;
import com.love.conversation.dto.CreateMessageRequest;
import com.love.user.PartnerTypeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

    private static final int MAX_HISTORY_MESSAGES = 20;

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final MessageEmotionRepository messageEmotionRepository;
    private final ConversationReportRepository conversationReportRepository;
    private final AiChatClient aiChatClient;
    private final PartnerTypeService partnerTypeService;

    @Transactional
    public Conversation createConversation(Long userId, CreateConversationRequest req) {
        Conversation c = new Conversation();
        c.setUserId(userId);
        c.setTitle(req.title());
        c.setScenarioKey(req.scenarioKey());
        c.setPersonaKey(req.personaKey());
        return conversationRepository.save(c);
    }

    /**
     * 메시지 추가. role=USER이면 Python AI 호출 후 ASSISTANT 메시지만 저장·반환 (감정/리포트는 결과 보고서에서만).
     */
    @Transactional
    public AddMessageResult addMessage(Long userId, Long conversationId, CreateMessageRequest req) {
        var conv = conversationRepository.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new IllegalArgumentException("대화방이 없거나 접근 권한이 없습니다."));

        if (req.content() == null || req.content().isBlank()) {
            throw new IllegalArgumentException("content는 비어있을 수 없습니다.");
        }
        if (req.role() == null) {
            throw new IllegalArgumentException("role은 필수입니다.");
        }

        // 1) USER 메시지 DB 저장
        Message userMessage = new Message();
        userMessage.setConversationId(conversationId);
        userMessage.setRole(req.role());
        userMessage.setContent(req.content().trim());
        userMessage.setClientMessageId(req.clientMessageId());
        userMessage = messageRepository.save(userMessage);

        updateConversationLastMessageAt(conv);

        // 2) USER일 때만 Python 호출 → ASSISTANT 메시지·감정·리포트 저장
        if (req.role() != MessageRole.USER) {
            return new AddMessageResult(userMessage, null, null, null);
        }

        // 최근 대화 히스토리 조회 (방금 저장한 USER 포함)
        List<Message> recentMessages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
        int from = Math.max(0, recentMessages.size() - MAX_HISTORY_MESSAGES);
        List<Message> history = recentMessages.subList(from, recentMessages.size());

        String partnerType = partnerTypeService.getPartnerType(userId).orElse("기본");
        String mode = conv.getMode() != null ? conv.getMode().name() : "SIMULATION";

        List<AiChatRequest.MessageItem> messageItems = history.stream()
                .map(m -> new AiChatRequest.MessageItem(
                        m.getRole() == MessageRole.USER ? "USER" : "ASSISTANT",
                        m.getContent()
                ))
                .toList();

        AiChatRequest aiRequest = new AiChatRequest(
                conversationId,
                userId,
                partnerType,
                mode,
                messageItems
        );

        Optional<AiChatResponse> aiResponse = aiChatClient.chat(aiRequest);

        if (aiResponse.isEmpty()) {
            log.error("Python AI 채팅 실패 conversationId={} (Python 서버 확인, OPENAI_API_KEY 확인)", conversationId);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "여친 답변 생성에 실패했어요. 잠시 후 다시 시도해 주세요.");
        }

        AiChatResponse res = aiResponse.get();

        // 3) ASSISTANT 메시지 DB 저장
        Message assistantMessage = new Message();
        assistantMessage.setConversationId(conversationId);
        assistantMessage.setRole(MessageRole.AI);
        assistantMessage.setContent(res.assistantMessage() != null ? res.assistantMessage() : "");
        assistantMessage.setClientMessageId(null);
        assistantMessage = messageRepository.save(assistantMessage);

        updateConversationLastMessageAt(conv);

        MessageEmotion emotion = null;
        if (res.emotionAnalysis() != null) {
            emotion = new MessageEmotion();
            emotion.setMessageId(assistantMessage.getId());
            emotion.setConversationId(conversationId);
            emotion.setEmotion(res.emotionAnalysis().emotion());
            emotion.setConfidence(res.emotionAnalysis().confidence() != null ? res.emotionAnalysis().confidence() : 0.0);
            emotion.setScoresJson(res.emotionAnalysis().scores() != null ? toJsonScores(res.emotionAnalysis().scores()) : null);
            emotion = messageEmotionRepository.save(emotion);
        }

        ConversationReport report = null;
        if (res.report() != null) {
            report = new ConversationReport();
            report.setConversationId(conversationId);
            report.setMessageId(assistantMessage.getId());
            report.setSummary(res.report().summary());
            report.setDetailsJson(res.report().details() != null ? toJsonDetails(res.report().details()) : null);
            report = conversationReportRepository.save(report);
        }

        return new AddMessageResult(userMessage, assistantMessage, emotion, report);
    }

    private void updateConversationLastMessageAt(Conversation conv) {
        conv.setLastMessageAt(java.time.Instant.now());
        conversationRepository.save(conv);
    }

    private static String toJsonScores(java.util.Map<String, Double> scores) {
        if (scores == null || scores.isEmpty()) return null;
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(scores);
        } catch (Exception e) {
            return null;
        }
    }

    private static String toJsonDetails(java.util.Map<String, Object> details) {
        if (details == null || details.isEmpty()) return null;
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(details);
        } catch (Exception e) {
            return null;
        }
    }

    private static String toJsonObject(Object o) {
        if (o == null) return null;
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(o);
        } catch (Exception e) {
            return null;
        }
    }

    @Transactional(readOnly = true)
    public ConversationThread getThread(Long userId, Long conversationId) {
        Conversation c = conversationRepository.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new IllegalArgumentException("대화방이 없거나 접근 권한이 없습니다."));

        List<Message> messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
        return new ConversationThread(c, messages);
    }

    public record ConversationThread(Conversation conversation, List<Message> messages) {}

    public record AddMessageResult(Message userMessage, Message assistantMessage, MessageEmotion emotion, ConversationReport report) {}

    public record ConversationSummary(
            Long id,
            String title,
            String mode,
            java.time.Instant lastMessageAt
    ) {}

    public List<ConversationSummary> listConversations(Long userId) {
        return conversationRepository.findByUserIdOrderByUpdatedAtDesc(userId)
                .stream()
                .map(c -> new ConversationSummary(
                        c.getId(),
                        c.getTitle(),
                        c.getMode() != null ? c.getMode().name() : "SIMULATION",
                        c.getLastMessageAt() != null ? c.getLastMessageAt() : c.getUpdatedAt()
                ))
                .toList();
    }

    /**
     * 결과 보고서 분석: 해당 대화의 내 대화(USER) + 상대방(AI) 전부 Python으로 보내서 감정 분석 + 통합 리포트.
     * 자바는 전달만 하고, 답변 생성·감정 분석은 전부 Python.
     */
    @Transactional
    public ReportResult runReport(Long userId, Long conversationId) {
        conversationRepository.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new IllegalArgumentException("대화방이 없거나 접근 권한이 없습니다."));

        List<Message> allOrdered = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
        List<Message> userMessages = allOrdered.stream().filter(m -> m.getRole() == MessageRole.USER).toList();
        List<Message> assistantMessages = allOrdered.stream().filter(m -> m.getRole() == MessageRole.AI).toList();

        if (userMessages.isEmpty() && assistantMessages.isEmpty()) {
            throw new IllegalArgumentException("분석할 대화가 없습니다.");
        }

        List<String> userTexts = userMessages.stream().map(Message::getContent).toList();
        List<String> assistantTexts = assistantMessages.stream().map(Message::getContent).toList();
        Optional<ReportResponse> opt = aiChatClient.fetchReport(new ReportRequest(userTexts, assistantTexts));
        if (opt.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "감정 분석 서버 호출에 실패했어요.");
        }

        ReportResponse res = opt.get();
        saveEmotions(conversationId, userMessages, res.userEmotionAnalyses() != null ? res.userEmotionAnalyses() : List.of());
        saveEmotions(conversationId, assistantMessages, res.assistantEmotionAnalyses() != null ? res.assistantEmotionAnalyses() : List.of());

        Map<String, Object> reportMap = res.report();
        String summary = reportMap != null && reportMap.get("summary") != null ? reportMap.get("summary").toString() : "";
        String detailsJson = reportMap != null && reportMap.get("details") != null ? toJsonObject(reportMap.get("details")) : null;
        ConversationReport report = new ConversationReport();
        report.setConversationId(conversationId);
        report.setMessageId(assistantMessages.isEmpty() ? (userMessages.isEmpty() ? null : userMessages.get(userMessages.size() - 1).getId()) : assistantMessages.get(assistantMessages.size() - 1).getId());
        report.setSummary(summary);
        report.setDetailsJson(detailsJson);
        conversationReportRepository.save(report);

        return new ReportResult(summary, detailsJson);
    }

    private void saveEmotions(Long conversationId, List<Message> messages, List<Map<String, Object>> emotionAnalyses) {
        int size = Math.min(messages.size(), emotionAnalyses.size());
        for (int i = 0; i < size; i++) {
            Message msg = messages.get(i);
            Map<String, Object> em = emotionAnalyses.get(i);
            MessageEmotion me = new MessageEmotion();
            me.setMessageId(msg.getId());
            me.setConversationId(conversationId);
            me.setEmotion(em.get("emotion") != null ? em.get("emotion").toString() : "평온");
            Object conf = em.get("confidence");
            me.setConfidence(conf instanceof Number n ? n.doubleValue() : 0.0);
            me.setScoresJson(em.get("scores") != null ? toJsonObject(em.get("scores")) : null);
            messageEmotionRepository.save(me);
        }
    }

    public record ReportResult(String summary, String detailsJson) {}
}
