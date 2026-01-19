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
        return Map.of("id", c.getId());
    }

    // 2) 메시지 추가
    @PostMapping("/{id}/messages")
    public Map<String, Object> addMessage(
            @AuthenticationPrincipal PrincipalDetails me,
            @PathVariable("id") Long conversationId,
            @RequestBody CreateMessageRequest req
    ) {
        var m = service.addMessage(me.userId(), conversationId, req);
        return Map.of("id", m.getId());
    }

    // 3) 대화 + 메시지 조회
    @GetMapping("/{id}")
    public ConversationService.ConversationThread get(
            @AuthenticationPrincipal PrincipalDetails me,
            @PathVariable("id") Long conversationId
    ) {
        return service.getThread(me.userId(), conversationId);
    }

    @GetMapping
    public List<ConversationService.ConversationSummary> list(@AuthenticationPrincipal PrincipalDetails principal) {
        return service.listConversations(principal.userId());
    }
}