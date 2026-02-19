package com.love.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Python POST /ai/report 요청. 내 대화(USER) + 상대방(AI) 텍스트 전달, 감정 분석은 Python에서 전부 수행 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReportRequest(List<String> userTexts, List<String> assistantTexts) {}
