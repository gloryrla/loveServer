package com.love.emotion;

import com.love.emotion.dto.ConversationEmotionReportRequest;
import com.love.emotion.dto.ConversationEmotionReportResponse;
import com.love.global.error.BizException;
import com.love.global.error.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 대화 발화 목록을 저장·분석해 사용자/상대방별 감정 분포를 집계하고,
 * "대화할 때 OOO 습관으로 해보는 건 어떨까요?" 형태의 제안 문장을 생성
 */
@Slf4j
@Service
public class ConversationEmotionReportService {

    private final EmotionAnalysisService emotionAnalysisService;
    private final WebClient openaiClient;
    private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";

    public ConversationEmotionReportService(
            EmotionAnalysisService emotionAnalysisService,
            @Value("${app.openai.api-key:}") String openaiApiKey
    ) {
        this.emotionAnalysisService = emotionAnalysisService;
        String key = (openaiApiKey != null && !openaiApiKey.isBlank()) ? openaiApiKey : "dummy";
        this.openaiClient = WebClient.builder()
                .baseUrl(OPENAI_API_URL)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + key)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public ConversationEmotionReportResponse buildReport(ConversationEmotionReportRequest request) {
        List<ConversationEmotionReportRequest.Utterance> utterances = request.utterances();
        if (utterances == null || utterances.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST);
        }

        Map<String, Long> userCounts = new HashMap<>();
        Map<String, Long> partnerCounts = new HashMap<>();

        for (ConversationEmotionReportRequest.Utterance u : utterances) {
            String role = (u.role() == null) ? "" : u.role().trim().toLowerCase();
            String text = (u.text() == null) ? "" : u.text().trim();
            if (text.isEmpty()) continue;

            boolean isUser = role.equals("user") || role.equals("speaker");
            String emotion = emotionAnalysisService.getEmotionForText(text);
            if (isUser) {
                userCounts.merge(emotion, 1L, Long::sum);
            } else {
                partnerCounts.merge(emotion, 1L, Long::sum);
            }
        }

        String topUserEmotion = topEmotion(userCounts);
        String topPartnerEmotion = topEmotion(partnerCounts);
        String suggestion = buildSuggestionWithOpenAI(topUserEmotion, topPartnerEmotion, userCounts, partnerCounts);

        return new ConversationEmotionReportResponse(
                sortByCountDesc(userCounts),
                sortByCountDesc(partnerCounts),
                suggestion
        );
    }

    private String topEmotion(Map<String, Long> counts) {
        if (counts == null || counts.isEmpty()) return "중립";
        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("중립");
    }

    private Map<String, Long> sortByCountDesc(Map<String, Long> map) {
        if (map == null || map.isEmpty()) return Map.of();
        return map.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
    }

    private String buildSuggestionWithOpenAI(String topUserEmotion, String topPartnerEmotion,
                                             Map<String, Long> userCounts, Map<String, Long> partnerCounts) {
        String userSummary = userCounts.isEmpty() ? "없음" : topUserEmotion + " 등";
        String partnerSummary = partnerCounts.isEmpty() ? "없음" : topPartnerEmotion + " 등";

        String prompt = String.format(
                "다음 대화 감정 분석 결과를 바탕으로, 한 문장으로만 대화 습관 제안을 해줘. " +
                "감정은 분노, 두려움, 기쁨, 평온, 슬픔 중 하나로 해석해. " +
                "사용자(발화자) 쪽에서는 '%s' 감정이 많았고, 상대방(청자) 쪽에서는 '%s' 감정이 많았어. " +
                "분노·슬픔·갈등이 있어도 '우리 사이 좋지~' 식으로만 넘기지 말고, 그 감정을 인정한 뒤 구체적인 습관(한템포 쉬고 말하기, 말 끝까지 듣기, 감정 말로 꺼내기 등)을 제안해. " +
                "반드시 이 형식으로 끝나: '대화할 때 [구체적 습관 한 가지] 습관으로 해보는 건 어떨까요?' " +
                "한국어로만 답하고, 80자 이내로 짧게.",
                userSummary, partnerSummary
        );

        try {
            Map<String, Object> body = Map.of(
                    "model", "gpt-4o-mini",
                    "messages", List.of(
                            Map.of("role", "system", "content",
                                    "당신은 연애/대화 코치입니다. 감정은 분노, 두려움, 기쁨, 평온, 슬픔만 사용하고, 한 문장으로만 답해주세요. " +
                                    "갈등·분노·슬픔이 있을 때는 무조건 좋게만 포장하지 말고, 그 감정을 인정한 뒤 현실적인 대화 습관(한템포 쉬기, 상대 말 끝까지 듣기, '지금 화나 있어'처럼 감정 말로 꺼내기 등)을 구체적으로 제안하세요."),
                            Map.of("role", "user", "content", prompt)
                    ),
                    "temperature", 0.7,
                    "max_tokens", 150
            );
            var response = openaiClient.post()
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(OpenAIChoice.class)
                    .block();
            if (response != null && response.choices() != null && !response.choices().isEmpty()) {
                String content = response.choices().get(0).message().content();
                if (content != null && !content.isBlank()) {
                    return content.trim();
                }
            }
        } catch (Exception e) {
            log.warn("OpenAI suggestion failed, using template: {}", e.getMessage());
        }

        return String.format(
                "사용자 텍스트에서는 '%s' 감정이 많았고, 상대방 텍스트에서는 '%s' 감정이 많았으므로, 대화할 때 감정이 격해지면 한템포 쉬고 말하는 습관으로 해보는 건 어떨까요?",
                topUserEmotion, topPartnerEmotion
        );
    }

    private record OpenAIChoice(List<Choice> choices) {}
    private record Choice(Message message) {}
    private record Message(String content) {}
}
