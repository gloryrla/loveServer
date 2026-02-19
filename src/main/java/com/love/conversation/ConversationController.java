package com.love.conversation;

import com.love.auth.PrincipalDetails;
import com.love.conversation.dto.CreateConversationRequest;
import com.love.conversation.dto.CreateMessageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService service;

    public ConversationController(ConversationService service) {
        this.service = service;
    }

    // 1) 대화방 생성
    @PostMapping
    public Map<String, Object> create(
            @AuthenticationPrincipal PrincipalDetails me,
            @RequestBody CreateConversationRequest req
    ) {
        var c = service.createConversation(me.userId(), req);
        return Map.of("conversationId", c.getId());
    }

    // 2) 메시지 추가 (USER 전송 시 Python AI 호출 → ASSISTANT·감정·리포트 저장 후 최종 응답 반환)
    @PostMapping("/{id}/messages")
    public AddMessageResponse addMessage(
            @AuthenticationPrincipal PrincipalDetails me,
            @PathVariable("id") Long conversationId,
            @RequestBody CreateMessageRequest req
    ) {
        var result = service.addMessage(me.userId(), conversationId, req);
        return new AddMessageResponse(
                toMessageDto(result.userMessage()),
                result.assistantMessage() != null ? toMessageDto(result.assistantMessage()) : null,
                result.emotion() != null ? new EmotionDto(result.emotion().getEmotion(), result.emotion().getConfidence(), result.emotion().getScoresJson()) : null,
                result.report() != null ? new ReportDto(result.report().getSummary(), result.report().getDetailsJson()) : null
        );
    }

    /** FE 스펙: role은 USER | ASSISTANT. DB는 USER | AI 이므로 AI → ASSISTANT 로 노출 */
    private static String roleForApi(MessageRole r) {
        return r == MessageRole.AI ? "ASSISTANT" : r.name();
    }

    private static MessageDto toMessageDto(Message m) {
        return new MessageDto(
                m.getId(),
                m.getConversationId(),
                roleForApi(m.getRole()),
                m.getContent(),
                m.getClientMessageId(),
                m.getCreatedAt()
        );
    }

    public record AddMessageResponse(
            MessageDto userMessage,
            MessageDto assistantMessage,
            EmotionDto emotionAnalysis,
            ReportDto report
    ) {}

    public record EmotionDto(String emotion, Double confidence, String scoresJson) {}

    public record ReportDto(String summary, String detailsJson) {}

    public record ReportResponseDto(String summary, String detailsJson) {}

    // 3) 대화 + 메시지 조회
    @GetMapping("/{id}")
    public ConversationService.ConversationThread get(
            @AuthenticationPrincipal PrincipalDetails me,
            @PathVariable("id") Long conversationId
    ) {
        return service.getThread(me.userId(), conversationId);
    }

    // 4) 결과 보고서 분석 (해당 대화의 AI 답변들 감정 분석 + 리포트 저장·반환)
    @PostMapping("/{id}/report")
    public ReportResponseDto runReport(
            @AuthenticationPrincipal PrincipalDetails me,
            @PathVariable("id") Long conversationId
    ) {
        var result = service.runReport(me.userId(), conversationId);
        return new ReportResponseDto(result.summary(), result.detailsJson());
    }

    // 5) 특정 대화의 메시지만 조회
    @GetMapping("/{id}/messages")
    public List<MessageDto> getMessages(
            @AuthenticationPrincipal PrincipalDetails me,
            @PathVariable("id") Long conversationId
    ) {
        var thread = service.getThread(me.userId(), conversationId);
        return thread.messages().stream()
                .map(m -> new MessageDto(
                        m.getId(),
                        m.getConversationId(),
                        roleForApi(m.getRole()),
                        m.getContent(),
                        m.getClientMessageId(),
                        m.getCreatedAt()
                ))
                .toList();
    }

    public record MessageDto(
            Long id,
            Long conversationId,
            String role,
            String content,
            String clientMessageId,
            java.time.Instant createdAt
    ) {}

    @GetMapping
    public List<ConversationService.ConversationSummary> list(@AuthenticationPrincipal PrincipalDetails principal) {
        return service.listConversations(principal.userId());
    }
}