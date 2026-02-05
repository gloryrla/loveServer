package com.love.emotion;

import com.love.emotion.dto.EmotionReportRequest;
import com.love.emotion.dto.EmotionReportResponse;
import com.love.global.error.BizException;
import com.love.global.error.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

/**
 * BERT 감정 분류 서비스(HTTP)를 호출해 사용자 텍스트의 감정을 분류하고 보고서 형태로 반환
 */
@Slf4j
@Service
public class EmotionAnalysisService {

    private final WebClient webClient;
    private final String bertServiceUrl;

    public EmotionAnalysisService(@Value("${app.bert.service-url:}") String bertServiceUrl) {
        this.bertServiceUrl = (bertServiceUrl == null || bertServiceUrl.isBlank())
                ? "http://localhost:5001"
                : bertServiceUrl.strip().replaceAll("/$", "");
        this.webClient = WebClient.builder()
                .baseUrl(this.bertServiceUrl)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * 사용자 텍스트에 대해 BERT 감정 분류를 수행하고 보고서 DTO 반환
     */
    public EmotionReportResponse analyzeAndReport(EmotionReportRequest request) {
        String text = request.text().trim();
        log.info("Emotion analysis requested, text length: {}", text.length());

        if (text.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST);
        }

        try {
            // BERT 서비스 규약: POST /analyze, body: { "text": "..." }
            // 응답: { "emotion": "기쁨", "confidence": 0.92, "scores": { "기쁨": 0.92, "슬픔": 0.05, ... } }
            BertEmotionResponse bert = webClient.post()
                    .uri("/analyze")
                    .bodyValue(Map.of("text", text))
                    .retrieve()
                    .bodyToMono(BertEmotionResponse.class)
                    .block();

            if (bert == null || bert.emotion() == null) {
                throw new BizException(ErrorCode.BAD_REQUEST);
            }

            String report = buildReport(text, bert.emotion(), bert.confidence());
            log.info("Emotion analysis done: emotion={}, confidence={}", bert.emotion(), bert.confidence());

            return new EmotionReportResponse(
                    bert.emotion(),
                    bert.confidence(),
                    bert.scores() != null ? bert.scores() : Map.of(),
                    report
            );
        } catch (Exception e) {
            log.error("BERT emotion service call failed", e);
            throw new BizException(ErrorCode.BAD_REQUEST);
        }
    }

    /**
     * 한 문장에 대한 감정 라벨만 반환 (대화 집계용)
     */
    public String getEmotionForText(String text) {
        if (text == null || text.trim().isEmpty()) return "중립";
        try {
            BertEmotionResponse bert = webClient.post()
                    .uri("/analyze")
                    .bodyValue(Map.of("text", text.trim()))
                    .retrieve()
                    .bodyToMono(BertEmotionResponse.class)
                    .block();
            if (bert == null || bert.emotion() == null) return "중립";
            return bert.emotion();
        } catch (Exception e) {
            log.warn("BERT emotion for text failed, using 중립: {}", e.getMessage());
            return "중립";
        }
    }

    private String buildReport(String inputText, String emotion, double confidence) {
        int pct = (int) Math.round(confidence * 100);
        String preview = inputText.length() > 30 ? inputText.substring(0, 30) + "…" : inputText;
        return String.format(
                "입력: \"%s\" → 감정: '%s'(%d%%). 감정 분류 보고서입니다.",
                preview, emotion, pct
        );
    }

    /** BERT 서비스가 반환하는 JSON 구조 */
    private record BertEmotionResponse(
            String emotion,
            Double confidence,
            Map<String, Double> scores
    ) {}
}
