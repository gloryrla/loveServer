package com.love.emotion.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 대화 발화 목록 (블렌더봇 형식과 유사: role + text)
 * role: "user"(발화자/사용자), "assistant"(청자/상대방) 또는 "speaker", "listener"
 */
public record ConversationEmotionReportRequest(
        @NotEmpty @Valid List<Utterance> utterances
) {
    public record Utterance(
            String role,   // "user" | "assistant" | "speaker" | "listener"
            String text
    ) {}
}
