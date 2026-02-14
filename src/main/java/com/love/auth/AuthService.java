package com.love.auth;

import com.love.auth.dto.LoginRequest;
import com.love.auth.dto.SignupRequest;
import com.love.auth.dto.TokenResponse;
import com.love.global.error.BizException;
import com.love.global.error.ErrorCode;
import com.love.user.User;
import com.love.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    @Transactional
    public void signup(SignupRequest req) {
        // ✅ userId 중복 체크
        if (userRepository.existsByUserId(req.userId())) {
            throw new BizException(ErrorCode.BAD_REQUEST); // 필요하면 DUPLICATE_USERID 같은 코드로 분리 추천
        }

        String hash = passwordEncoder.encode(req.password());

        userRepository.save(User.builder()
                .userId(req.userId())
                .passwordHash(hash)
                .name(req.name())
                .birthDate(req.birthDate())
                .build());
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest req) {
        User user = userRepository.findByUserId(req.userId())
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED));

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }

        // ✅ JWT subject도 email 대신 userId로
        String token = jwtProvider.createAccessToken(user.getId(), user.getUserId());
        // AuthService는 단순히 토큰만 반환 (상세 정보는 AuthController에서 처리)
        return new TokenResponse(token, null, null, null, null);
    }
}