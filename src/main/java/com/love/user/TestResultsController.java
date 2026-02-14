package com.love.user;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/test-results")
@RequiredArgsConstructor
public class TestResultsController {

    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;

    /**
     * 최초 1회만 저장되는 '여친 유형' 값 저장
     * - 이미 저장되어 있으면 409(CONFLICT)
     */
    @PostMapping("/partner-type")
    @Transactional
    public void savePartnerType(
            Authentication authentication,
            @RequestBody PartnerTypeSaveRequest body
    ) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        String userId = authentication.getName();
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));

        if (body == null || body.value() == null || body.value().trim().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "value는 필수입니다.");
        }

        // 이미 설정되어 있는지 확인
        try {
            Integer exists = jdbcTemplate.queryForObject(
                    "select 1 from test_results where user_id = ? and test_key = 'partner_type' limit 1",
                    Integer.class,
                    user.getId()
            );
            if (exists != null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 여친 유형이 설정되어 있습니다.");
            }
        } catch (EmptyResultDataAccessException ignore) {
            // 아직 설정되지 않음
        }

        // 저장 (created_at은 now()로 세팅)
        int updated = jdbcTemplate.update(
                "insert into test_results (user_id, test_key, result_value, created_at) values (?, 'partner_type', ?, now())",
                user.getId(),
                body.value().trim()
        );

        if (updated != 1) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "저장에 실패했습니다.");
        }
    }

    public record PartnerTypeSaveRequest(String value) {}
}
