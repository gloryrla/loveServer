package com.love.emotion.dto;

import java.util.Map;

/**
 * 대화 전체에 대한 감정 집계 + 습관 제안
 */
public record ConversationEmotionReportResponse(
        Map<String, Long> userEmotionSummary,      // 사용자(발화자) 발화별 감정 집계 (감정명 -> 횟수)
        Map<String, Long> partnerEmotionSummary,  // 상대방(청자) 발화별 감정 집계
        String suggestion                          // "사용자 텍스트에서는 OO 감정이 많았고, 상대방 텍스트에서는 OO 감정이 많았으므로, 대화할 때 XXX 습관으로 해보는 건 어떨까요?"
) {}
