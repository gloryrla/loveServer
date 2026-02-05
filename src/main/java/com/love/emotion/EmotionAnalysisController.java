package com.love.emotion;

import com.love.emotion.dto.ConversationEmotionReportRequest;
import com.love.emotion.dto.ConversationEmotionReportResponse;
import com.love.emotion.dto.EmotionReportRequest;
import com.love.emotion.dto.EmotionReportResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/emotion-analysis")
@RequiredArgsConstructor
public class EmotionAnalysisController {

    private final EmotionAnalysisService emotionAnalysisService;
    private final ConversationEmotionReportService conversationEmotionReportService;

    /**
     * 사용자 텍스트를 BERT로 감정 분류 후 보고서 형태로 반환
     * POST /api/emotion-analysis
     * Body: { "text": "오늘 너무 기쁜 일이 있었어" }
     */
    @PostMapping
    public EmotionReportResponse report(@RequestBody @Valid EmotionReportRequest request) {
        log.info("POST /api/emotion-analysis");
        return emotionAnalysisService.analyzeAndReport(request);
    }

    /**
     * 대화 발화 목록을 저장·분석해 사용자/상대방별 감정 집계 + "대화 습관 제안" 반환
     * POST /api/emotion-analysis/conversation-report
     * Body: { "utterances": [ { "role": "user", "text": "..." }, { "role": "assistant", "text": "..." } ] }
     */
    @PostMapping("/conversation-report")
    public ConversationEmotionReportResponse conversationReport(
            @RequestBody @Valid ConversationEmotionReportRequest request
    ) {
        log.info("POST /api/emotion-analysis/conversation-report, utterances: {}", request.utterances().size());
        return conversationEmotionReportService.buildReport(request);
    }
}
