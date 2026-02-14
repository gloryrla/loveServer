package com.love.auth.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record TokenResponse(
        String accessToken,
        UserInfo user,
        PartnerType partnerType,
        ConversationSummary lastConversation,
        List<MessageDto> lastConversationMessages
) {
    public record UserInfo(
            Long id,
            String userId,
            String name,
            LocalDate birthDate
    ) {}

    public record PartnerType(
            boolean isSet,
            String value
    ) {}

    public record ConversationSummary(
            Long id,
            String title,
            String scenarioKey,
            Instant updatedAt
    ) {}

    public record MessageDto(
            Long id,
            Long conversationId,
            String role,
            String content,
            Instant createdAt
    ) {}
}