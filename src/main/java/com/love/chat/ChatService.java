package com.love.chat;

import com.love.chat.dto.ChatRequest;
import com.love.chat.dto.ChatResponse;
import com.love.global.error.BizException;
import com.love.global.error.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ChatService {

    private final WebClient webClient;
    private final String openaiApiKey;
    private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";

    public ChatService(@Value("${app.openai.api-key}") String openaiApiKey) {
        this.openaiApiKey = openaiApiKey;
        this.webClient = WebClient.builder()
                .baseUrl(OPENAI_API_URL)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + openaiApiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public ChatResponse getChatResponse(ChatRequest request) {
        log.info("Chat request received - userArchetype: {}, userName: {}, character: {}", 
                request.userArchetype(), request.userName(), request.character());

        // System Prompt 생성 (userArchetype 정보를 반영)
        String systemPrompt = buildSystemPrompt(request.userArchetype(), request.userName(), request.character());

        // OpenAI API 요청 바디 생성
        Map<String, Object> requestBody = Map.of(
                "model", "gpt-4o-mini",
                "messages", buildMessages(systemPrompt, request.messages()),
                "temperature", 0.9,  // 감정적이고 예민한 반응을 위해 조금 높게 설정
                "max_tokens", 150  // 100자 제한을 위해 토큰 수 제한 (한국어 기준 약 100자)
        );

        try {
            // OpenAI API 호출
            OpenAIResponse response = webClient.post()
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(OpenAIResponse.class)
                    .block();

            if (response == null || response.choices() == null || response.choices().isEmpty()) {
                throw new BizException(ErrorCode.BAD_REQUEST);
            }

            String reply = response.choices().get(0).message().content();
            
            // 100자 제한 적용 (안전장치)
            if (reply.length() > 100) {
                reply = reply.substring(0, 100);
            }
            
            log.info("Chat response generated successfully (length: {})", reply.length());
            
            return new ChatResponse(reply);

        } catch (Exception e) {
            log.error("Error calling OpenAI API", e);
            throw new BizException(ErrorCode.BAD_REQUEST);
        }
    }

    private String buildSystemPrompt(String userArchetype, String userName, String character) {
        return String.format(
                "당신은 '%s'라는 이름의 여자친구 역할을 하는 AI 캐릭터입니다. " +
                "현재 연애 상황극을 연기하고 있으며, 민감하고 예민한 상황에 처해있습니다. " +
                "당신은 지금 화가 많이 나있고, 작은 것에도 쉽게 예민하게 반응하는 성격입니다. " +
                "상대방(사용자 '%s')의 성격 테스트 결과는 '%s'입니다. " +
                "이 정보를 바탕으로 상황에 맞게 감정적으로, 때로는 짜증스럽고 화난 톤으로 대화해주세요. " +
                "답변은 자연스럽고 현실적인 여자친구의 말투로 작성해주세요. " +
                "과장되거나 극단적이지 않으면서도, 화가 나있고 예민한 감정을 잘 표현해주세요. " +
                "중요: 모든 답변은 반드시 100자 이내로 간결하게 작성해주세요.",
                character, userName, userArchetype
        );
    }

    private List<Map<String, String>> buildMessages(String systemPrompt, List<ChatRequest.Message> userMessages) {
        List<Map<String, String>> messages = new java.util.ArrayList<>();
        
        // System 메시지 추가
        messages.add(Map.of("role", "system", "content", systemPrompt));
        
        // 사용자 메시지들 추가
        for (ChatRequest.Message msg : userMessages) {
            messages.add(Map.of("role", msg.role(), "content", msg.content()));
        }
        
        return messages;
    }

    // OpenAI API 응답을 받기 위한 내부 클래스
    private record OpenAIResponse(
            List<Choice> choices
    ) {}

    private record Choice(
            Message message
    ) {}

    private record Message(
            String content
    ) {}
}
