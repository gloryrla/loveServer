package com.love.user;

import com.love.auth.PrincipalDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class MeController {

    @GetMapping("/api/me")
    public Map<String, Object> me(@AuthenticationPrincipal PrincipalDetails me) {
        return Map.of(
                "userId", me.userId(),
                "userId", me.loginId()
        );
    }
}