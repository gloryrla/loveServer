package com.love.auth;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

/**
 * JWT 인증 시 사용. userId = DB PK(Long), loginId = 로그인 아이디(String).
 * authentication.getName()이 로그인 아이디를 반환하도록 getUsername() 구현.
 */
public record PrincipalDetails(Long userId, String loginId) implements UserDetails {

    @Override
    public String getUsername() {
        return loginId;
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.emptyList();
    }
}
