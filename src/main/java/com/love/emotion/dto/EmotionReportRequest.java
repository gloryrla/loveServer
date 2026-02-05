package com.love.emotion.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 사용자가 입력한 텍스트를 BERT 감정 분류에 넘기기 위한 요청
 */
public record EmotionReportRequest(
        @NotBlank String text
) {}
