package com.love.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ChatRequest(
        @NotEmpty List<Message> messages,
        @NotBlank String userArchetype,
        @NotBlank String userName,
        @NotBlank String character
) {
    public record Message(
            @NotBlank String role,
            @NotBlank String content
    ) {}
}
