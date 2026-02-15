package com.love.auth;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);
    private final JwtProvider jwtProvider;

    public JwtAuthFilter(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);

        try {
            Long userId = jwtProvider.getUserId(token);
            String email = jwtProvider.parse(token).getPayload().get("email", String.class);

            var principal = new PrincipalDetails(userId, email);

            // UsernamePasswordAuthenticationToken(principal, credentials, authorities)
            var auth = new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
            SecurityContextHolder.getContext().setAuthentication(auth);

            chain.doFilter(request, response);

        } catch (JwtException | IllegalArgumentException e) {
            // 토큰 만료/서명 오류 등 → 인증 미설정, 이후 인증 필요 경로는 401
            log.warn("JWT invalid (request will be unauthenticated): {} - {}", e.getClass().getSimpleName(), e.getMessage());
            chain.doFilter(request, response);
        }
    }
}