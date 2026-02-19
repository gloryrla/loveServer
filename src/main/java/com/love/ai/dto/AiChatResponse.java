package com.love.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * Python → Java POST /ai/chat 응답
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiChatResponse(
        String assistantMessage,
        EmotionAnalysis emotionAnalysis,
        Report report,
        TokenUsage tokenUsage
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EmotionAnalysis(
            String emotion,
            Double confidence,
            Map<String, Double> scores
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Report(
            String summary,
            Map<String, Object> details
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TokenUsage(
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens
    ) {}
}
