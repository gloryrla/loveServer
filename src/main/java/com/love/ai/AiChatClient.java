package com.love.ai;

import com.love.ai.dto.AiChatRequest;
import com.love.ai.dto.AiChatResponse;
import com.love.ai.dto.ReportRequest;
import com.love.ai.dto.ReportResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Optional;

/**
 * Python AI 서버 호출 전담. LLM/감정/리포트 로직은 Python에만 있음.
 */
@Slf4j
@Component
public class AiChatClient {

    private final WebClient aiServerWebClient;

    public AiChatClient(@Qualifier("aiServerWebClient") WebClient aiServerWebClient) {
        this.aiServerWebClient = aiServerWebClient;
    }

    /**
     * POST /ai/chat - Python에서 OpenAI로 대화 생성. 자바는 호출만 함.
     */
    public Optional<AiChatResponse> chat(AiChatRequest request) {
        log.info("Python /ai/chat 호출 conversationId={} messages={}", request.conversationId(), request.messages() != null ? request.messages().size() : 0);
        try {
            AiChatResponse response = aiServerWebClient
                    .post()
                    .uri("/ai/chat")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(AiChatResponse.class)
                    .block();
            if (response == null || response.assistantMessage() == null || response.assistantMessage().isBlank()) {
                log.warn("Python /ai/chat returned empty assistantMessage");
                return Optional.empty();
            }
            log.info("Python /ai/chat 성공 conversationId={}", request.conversationId());
            return Optional.ofNullable(response);
        } catch (WebClientResponseException e) {
            log.error("Python /ai/chat 실패 status={} body={}", e.getStatusCode(), e.getResponseBodyAsString());
            return Optional.empty();
        } catch (Exception e) {
            log.error("Python /ai/chat 오류 (연결 확인: app.ai-server.url, Python 서버 실행 여부)", e);
            return Optional.empty();
        }
    }

    /**
     * POST /ai/report - 여러 답변 텍스트에 대해 감정 분석 + 통합 리포트. '결과 보고서 분석' 버튼에서만 호출.
     */
    public Optional<ReportResponse> fetchReport(ReportRequest request) {
        try {
            ReportResponse response = aiServerWebClient
                    .post()
                    .uri("/ai/report")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(ReportResponse.class)
                    .block();
            return Optional.ofNullable(response);
        } catch (WebClientResponseException e) {
            log.warn("AI server /ai/report failed: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("AI server /ai/report error", e);
            return Optional.empty();
        }
    }
}
