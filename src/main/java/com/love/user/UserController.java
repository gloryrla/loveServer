package com.love.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final TestResultRepository testResultRepository;

    @GetMapping("/me")
    public MeResponse me(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        // ✅ 이제 인증 식별자는 userId라고 가정
        String userId = authentication.getName();

        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));

        return new MeResponse(
                user.getId(),
                user.getUserId(),
                user.getName(),
                user.getBirthDate()
        );
    }

    /**
     * '여친 유형' 저장 또는 갱신.
     * - 최초: 새로 저장. 이미 있으면 기존 row의 result_value만 갱신 (다시 테스트하기 지원).
     */
    @PostMapping("/test-results/partner-type")
    @Transactional
    public void savePartnerType(
            Authentication authentication,
            @RequestBody PartnerTypeSaveRequest body
    ) {
        log.info("partner-type save: auth={}, body={}", authentication != null && authentication.isAuthenticated(), body);
        if (authentication == null || !authentication.isAuthenticated()) {
            log.warn("partner-type save: 401 (no auth)");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        String loginId = authentication.getName();
        User user = userRepository.findByUserId(loginId)
                .orElseThrow(() -> {
                    log.warn("partner-type save: 401 (user not found for loginId={})", loginId);
                    return new ResponseStatusException(HttpStatus.UNAUTHORIZED);
                });

        if (body == null || body.value() == null || body.value().trim().isBlank()) {
            log.warn("partner-type save: 400 (value blank)");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "value는 필수입니다.");
        }

        String value = body.value().trim();
        var existing = testResultRepository.findFirstByUserIdAndTestKeyOrderByIdDesc(user.getId(), "partner_type");

        if (existing.isPresent()) {
            TestResult tr = existing.get();
            tr.setResultValue(value);
            testResultRepository.save(tr);
            log.info("partner-type updated for userId={}", user.getId());
        } else {
            TestResult tr = new TestResult();
            tr.setUserId(user.getId());
            tr.setTestKey("partner_type");
            tr.setResultValue(value);
            testResultRepository.save(tr);
            log.info("partner-type created for userId={}", user.getId());
        }
    }

    public record MeResponse(Long id, String userId, String name, java.time.LocalDate birthDate) {}
    public record PartnerTypeSaveRequest(String value) {}
}