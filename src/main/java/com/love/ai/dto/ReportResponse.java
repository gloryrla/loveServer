package com.love.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/** Python POST /ai/report 응답. 내 대화·상대방 감정 분석 결과 + 통합 리포트 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReportResponse(
        List<Map<String, Object>> userEmotionAnalyses,
        List<Map<String, Object>> assistantEmotionAnalyses,
        Map<String, Object> report
) {}
