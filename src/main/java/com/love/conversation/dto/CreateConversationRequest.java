package com.love.conversation.dto;

public record CreateConversationRequest(
        String title,
        String scenarioKey,
        String personaKey
) {}