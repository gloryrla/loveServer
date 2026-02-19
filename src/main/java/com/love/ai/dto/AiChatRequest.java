package com.love.ai.dto;

import java.util.List;

/**
 * Java → Python POST /ai/chat 요청
 */
public record AiChatRequest(
        Long conversationId,
        Long userId,
        String partnerType,
        String mode,
        List<MessageItem> messages
) {
    public record MessageItem(String role, String content) {}
}
