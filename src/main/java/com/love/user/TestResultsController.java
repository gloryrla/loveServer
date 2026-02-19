package com.love.user;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/test-results")
@RequiredArgsConstructor
public class TestResultsController {

    private final UserRepository userRepository;
    private final TestResultRepository testResultRepository;

    /**
     * '여친 유형' 저장 또는 갱신.
     * - 최초: 새로 저장. 이미 있으면 기존 row의 result_value만 갱신 (다시 테스트하기 지원).
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

        String loginId = authentication.getName();
        User user = userRepository.findByUserId(loginId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));

        if (body == null || body.value() == null || body.value().trim().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "value는 필수입니다.");
        }

        String value = body.value().trim();
        var existing = testResultRepository.findFirstByUserIdAndTestKeyOrderByIdDesc(user.getId(), "partner_type");

        if (existing.isPresent()) {
            TestResult tr = existing.get();
            tr.setResultValue(value);
            testResultRepository.save(tr);
        } else {
            TestResult tr = new TestResult();
            tr.setUserId(user.getId());
            tr.setTestKey("partner_type");
            tr.setResultValue(value);
            testResultRepository.save(tr);
        }
    }

    public record PartnerTypeSaveRequest(String value) {}
}
