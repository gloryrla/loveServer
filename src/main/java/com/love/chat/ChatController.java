package com.love.chat;

import com.love.chat.dto.ChatRequest;
import com.love.chat.dto.ChatResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping
    public ChatResponse chat(@RequestBody @Valid ChatRequest request) {
        log.info("POST /api/chat - messages count: {}", request.messages().size());
        return chatService.getChatResponse(request);
    }
}
