package com.love.conversation.dto;

import com.love.conversation.MessageRole;

public record CreateMessageRequest(
        MessageRole role,
        String content
) {}