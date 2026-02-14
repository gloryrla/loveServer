package com.love.user;

import com.love.auth.PrincipalDetails;
import com.love.conversation.Conversation;
import com.love.conversation.ConversationRepository;
import com.love.conversation.ConversationService;
import com.love.conversation.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SessionController {

    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ConversationRepository conversationRepository;
    private final ConversationService conversationService;

    @GetMapping("/session")
    public SessionResponse getSession(@AuthenticationPrincipal PrincipalDetails me) {
        if (me == null) {
            throw new IllegalStateException("인증이 필요합니다.");
        }

        Long userId = me.userId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("사용자를 찾을 수 없습니다."));

        // 1) 회원정보
        SessionResponse.UserInfo userInfo = new SessionResponse.UserInfo(
                user.getId(),
                user.getUserId(),
                user.getName(),
                user.getBirthDate()
        );

        // 2) 테스트 유형 (여친 유형)
        SessionResponse.PartnerType partnerType = loadPartnerType(userId);

        // 3) 최근 대화 + 메시지
        List<Conversation> conversations = conversationRepository.findByUserIdOrderByUpdatedAtDesc(userId);
        SessionResponse.ConversationSummary lastConversation = null;
        List<SessionResponse.MessageDto> lastConversationMessages = Collections.emptyList();

        if (!conversations.isEmpty()) {
            Conversation conv = conversations.get(0);
            lastConversation = new SessionResponse.ConversationSummary(
                    conv.getId(),
                    conv.getTitle(),
                    conv.getMode() != null ? conv.getMode().name() : "SIMULATION",
                    conv.getLastMessageAt() != null ? conv.getLastMessageAt() : conv.getUpdatedAt()
            );

            List<Message> messages = conversationService.getThread(userId, conv.getId()).messages();
            lastConversationMessages = messages.stream()
                    .map(m -> new SessionResponse.MessageDto(
                            m.getId(),
                            m.getConversationId(),
                            m.getRole().name(),
                            m.getContent(),
                            m.getClientMessageId(),
                            m.getCreatedAt()
                    ))
                    .toList();
        }

        return new SessionResponse(
                userInfo,
                partnerType,
                lastConversation,
                lastConversationMessages
        );
    }

    private SessionResponse.PartnerType loadPartnerType(Long userId) {
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
            return new SessionResponse.PartnerType(true, value);
        } catch (EmptyResultDataAccessException e) {
            return new SessionResponse.PartnerType(false, null);
        }
    }

    public record SessionResponse(
            UserInfo user,
            PartnerType partnerType,
            ConversationSummary lastConversation,
            List<MessageDto> lastConversationMessages
    ) {
        public record UserInfo(
                Long id,
                String userId,
                String name,
                LocalDate birthDate
        ) {}

        public record PartnerType(
                boolean isSet,
                String value
        ) {}

        public record ConversationSummary(
                Long id,
                String title,
                String mode,
                Instant lastMessageAt
        ) {}

        public record MessageDto(
                Long id,
                Long conversationId,
                String role,
                String content,
                String clientMessageId,
                Instant createdAt
        ) {}
    }
}
