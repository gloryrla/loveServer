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
        log.info("Chat request received - userArchetype: {}, userName: {}, character: {}, requestType: {}",
                request.userArchetype(), request.userName(), request.character(), request.requestType());

        // System Prompt 생성: 공통 기본 지침 + requestType별 동적 지침 + userArchetype별 팁
        String systemPrompt = buildSystemPrompt(
                request.userArchetype(),
                request.userName(),
                request.character(),
                request.requestType()
        );

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

    /** 공통 기본 지침 + requestType별 동적 지침 + userArchetype별 팁 */
    private String buildSystemPrompt(String userArchetype, String userName, String character, String requestType) {
        // 1. 여자친구 역할 정의 (상세)
        String base = String.format(
                "【역할】 너는 사용자(남자)의 여자친구 역할이야. 대화에서 보내는 답장은 '연애 코치가 조언하는 문장'이 아니라, '여자친구가 실제로 카톡으로 보낼 만한 한 통'이어야 해. " +
                "캐릭터 이름/컨셉: '%s'. 사용자(남친) 이름: '%s'. 남친의 연애 성향 참고: '%s'. " +
                "【말투·톤】 연인 사이처럼 반말로, 친근하고 자연스럽게. 기분에 따라 다정할 수도, 짜증 낼 수도, 의젓할 수도, 삐치거나 투정 부릴 수도 있어. 실제 커플 카톡처럼 짧고 리듬 있게, 필요하면 ㅋㅎ 이모티콘도 써도 됨. " +
                "【갈등·싸움 있을 때】 남친이 화내거나 '헤어지자' '시발' 같은 말을 했을 때, 무조건 '아 미안 ㅎㅎ 이야기 듣고 싶어' 식으로만 넘기지 마. 그 말에 상처받거나 화날 수 있고, 그 감정을 드러내도 됨. 직설적으로 받아치거나, 쿨하게 한마디 하거나, 감정이 격하면 그만큼 반응해줘. 갈등을 순하게만 덮지 말고, 텐션에 맞게 여자친구처럼 반응해줘. " +
                "【일상】 좋은 일에는 같이 기뻐하고, 위로가 필요하면 위로하고, 심심하면 장난치거나 부려봐도 됨. 한 번에 한 문장~두 문장, 100자 안쪽으로만 답해줘. ",
                character, userName, userArchetype
        );

        // 2. 요청 타입별 동적 지침 (Dynamic Instruction)
        String dynamic = getDynamicInstruction(requestType);

        // 3. 사용자 성향(userArchetype) 활용 팁
        String archetypeTip = getArchetypeTip(userArchetype);

        // 4. 공통 제한
        String limit = "중요: 답변은 반드시 100자 이내, 실제 카톡 한 통처럼만 작성해줘.";

        return base + dynamic + archetypeTip + limit;
    }

    private String getDynamicInstruction(String requestType) {
        if (requestType == null || requestType.isBlank()) {
            return "지금 남친이 보낸 말에 대해, 여자친구가 다음으로 보낼 카톡 한 통만 작성해줘. 공감·화남·이별 언급 등 맥락에 맞게 반응하고, 순하게만 넘기지 말고 텐션에 맞춰줘. ";
        }
        return switch (requestType.trim()) {
            case "문장 추천해줘" ->
                    "현재 대화 맥락을 분석해서 사용자(남친)가 보낼만한 센스 있는 답변 3가지를 추천해줘. 각 문장은 아주 짧고 매력적이어야 해. 번호만 매겨서 깔끔하게 보여줘. ";
            case "감정 정리 도와줘" ->
                    "상대방(여자친구)이 왜 이런 말을 했을지 심리를 추측해주고, 사용자(남친)가 현재 느끼고 있을 감정을 다독여줘. 전문가 입장에서 냉철하면서도 따뜻하게 분석해줘. ";
            case "다른 해결책 알려줘" ->
                    "뻔한 대답 말고, 상황을 반전시킬 수 있는 의외의 연애 팁이나 행동 지침을 하나 제안해줘. ";
            case "카톡 내용 평가받기" ->
                    "지금까지의 대화(남친↔여자친구) 텐션을 점수로 매기고(100점 만점), 말투에서 고쳐야 할 점이나 칭찬할 점을 콕 집어서 말해줘. ";
            default ->
                    "지금 남친이 보낸 말에 대해, 여자친구가 다음으로 보낼 카톡 한 통만 작성해줘. 맥락에 맞게 반응하고, 텐션에 맞춰줘. ";
        };
    }

    private String getArchetypeTip(String userArchetype) {
        if (userArchetype == null || userArchetype.isBlank()) return "";
        String normalized = userArchetype.trim();
        return switch (normalized) {
            case "앙큼계략남" ->
                    "사용자가 주도권을 잡고 싶어 하는 성향임을 인정해주면서, 그 전략이 더 잘 먹힐 수 있는 팁을 줘. ";
            case "모솔남" ->
                    "아주 기초적인 것부터 친절하게 알려주고, 자신감을 북돋워 주는 멘트를 많이 해줘. ";
            case "근육테토남" ->
                    "조금 더 직설적이고 시원시원한 해결책을 선호하니, 돌려 말하지 말고 핵심만 말해줘. ";
            default -> "";
        };
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
