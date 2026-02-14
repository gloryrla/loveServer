package com.love.auth;


import com.love.auth.dto.LoginRequest;
import com.love.auth.dto.SignupRequest;
import com.love.auth.dto.SignupResponse;
import com.love.auth.dto.TokenResponse;
import com.love.conversation.Conversation;
import com.love.conversation.ConversationRepository;
import com.love.conversation.ConversationService;
import com.love.conversation.Message;
import com.love.user.User;
import com.love.user.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final JdbcTemplate jdbcTemplate;
    private final ConversationRepository conversationRepository;
    private final ConversationService conversationService;

    @PostMapping("/signup")
    public SignupResponse signup(@RequestBody @Valid SignupRequest req) {
        log.info("signup req: {}", req);
        if (userRepository.existsByUserId(req.userId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 존재하는 아이디");
        }

        User user = User.of(
                req.userId(),
                passwordEncoder.encode(req.password()),
                req.name(),
                req.birthDate()
        );

        User saved = userRepository.save(user);
        log.info("saved user {}", saved);

        return new SignupResponse(saved.getId(), saved.getUserId(), saved.getName());
    }

    @PostMapping("/login")
    public TokenResponse login(@RequestBody @Valid LoginRequest req) {
        log.info("login req = {}", req);
        User user = userRepository.findByUserId(req.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "아이디/비번 틀림"));

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "아이디/비번 틀림");
        }

        String accessToken = jwtProvider.createAccessToken(user.getId(), user.getUserId());

        // 1) 회원정보
        TokenResponse.UserInfo userInfo = new TokenResponse.UserInfo(
                user.getId(),
                user.getUserId(),
                user.getName(),
                user.getBirthDate()
        );

        // 2) 테스트 유형 (여친 유형)
        TokenResponse.PartnerType partnerType = loadPartnerType(user.getId());

        // 3) 최근 대화 + 메시지
        List<Conversation> conversations = conversationRepository.findByUserIdOrderByUpdatedAtDesc(user.getId());
        TokenResponse.ConversationSummary lastConversation = null;
        List<TokenResponse.MessageDto> lastConversationMessages = Collections.emptyList();

        if (!conversations.isEmpty()) {
            Conversation conv = conversations.get(0);
            lastConversation = new TokenResponse.ConversationSummary(
                    conv.getId(),
                    conv.getTitle(),
                    conv.getScenarioKey(),
                    conv.getUpdatedAt()
            );

            List<Message> messages = conversationService.getThread(user.getId(), conv.getId()).messages();
            lastConversationMessages = messages.stream()
                    .map(m -> new TokenResponse.MessageDto(
                            m.getId(),
                            m.getConversationId(),
                            m.getRole().name(),
                            m.getContent(),
                            m.getCreatedAt()
                    ))
                    .toList();
        }

        return new TokenResponse(
                accessToken,
                userInfo,
                partnerType,
                lastConversation,
                lastConversationMessages
        );
    }

    private TokenResponse.PartnerType loadPartnerType(Long userId) {
        try {
            String value = jdbcTemplate.queryForObject(
                    """
                    select result_value
                    from test_results
                    where user_id = ?
                      and test_key = 'partner_type'
                    order by id desc
                    limit 1
                    """,
                    String.class,
                    userId
            );
            return new TokenResponse.PartnerType(true, value);
        } catch (EmptyResultDataAccessException e) {
            return new TokenResponse.PartnerType(false, null);
        }
    }
}