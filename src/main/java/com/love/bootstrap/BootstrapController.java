package com.love.bootstrap;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 앱 초기화용. 프론트에서 로그인 후 라우팅 결정에 사용.
 * partnerType.isSet == false 이면 온보딩(/ideal), true면 채팅(/chat)으로 보냄.
 */
@RestController
public class BootstrapController {

    @GetMapping("/api/bootstrap")
    public Map<String, Object> bootstrap() {
        // TODO: DB에서 사용자별 파트너 타입 설정 여부 조회 후 반환
        return Map.of(
                "partnerType", Map.of("isSet", false)
        );
    }
}
