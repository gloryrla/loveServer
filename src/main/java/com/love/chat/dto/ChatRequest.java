package com.love.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ChatRequest(
        @NotEmpty List<Message> messages,
        @NotBlank String userArchetype,
        @NotBlank String userName,
        @NotBlank String character,
        String requestType  // null 또는 "" = 일반 대화, "문장 추천해줘", "감정 정리 도와줘", "다른 해결책 알려줘", "카톡 내용 평가받기"
) {
    public record Message(
            @NotBlank String role,
            @NotBlank String content
    ) {}
}
