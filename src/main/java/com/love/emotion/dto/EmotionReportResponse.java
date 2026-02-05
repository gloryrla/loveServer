package com.love.emotion.dto;

import java.util.Map;

/**
 * BERT 감정 분류 결과를 보고서 형태로 반환
 */
public record EmotionReportResponse(
        String emotion,           // 대표 감정 라벨 (예: 기쁨, 슬픔, 분노, 불안, 중립 등)
        double confidence,       // 해당 감정 신뢰도 0~1
        Map<String, Double> scores,  // 감정별 점수 (선택)
        String report            // 요약 보고서 문장 (예: "입력하신 문장은 '기쁨'(92%) 감정으로 분류되었습니다.")
) {}
