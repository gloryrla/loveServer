package com.love.auth;


import com.love.auth.dto.LoginRequest;
import com.love.auth.dto.SignupRequest;
import com.love.auth.dto.SignupResponse;
import com.love.auth.dto.TokenResponse;
import com.love.user.User;
import com.love.user.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

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
        return new TokenResponse(accessToken);
    }
}