package com.crane.bandwebbackend.domain.auth.service;

import com.crane.bandwebbackend.domain.auth.entity.AccessTokenBlacklist;
import com.crane.bandwebbackend.domain.auth.entity.RevokeReason;
import com.crane.bandwebbackend.domain.auth.repository.AccessTokenBlacklistRepository;
import com.crane.bandwebbackend.domain.auth.repository.RefreshTokenRepository;
import com.crane.bandwebbackend.domain.user.repository.UserRepository;
import com.crane.bandwebbackend.global.security.AuthPrincipal;
import com.crane.bandwebbackend.global.security.JwtProvider;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// API 5·6: 인증된 요청만 도달한다(필터가 Access Token·블랙리스트·token_version을 이미 확인)
@Service
@RequiredArgsConstructor
public class AuthLogoutService {

    private final AccessTokenBlacklistRepository blacklist;
    private final RefreshTokenRepository refreshTokens;
    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;
    private final Clock clock;

    // 현재 기기: 이 Access Token을 만료 시각까지 블랙리스트에 올리고, 같은 로그인 묶음(sid)의 Refresh를 폐기한다
    @Transactional
    public void logout(AuthPrincipal principal, String accessToken) {
        JwtProvider.AccessTokenClaims claims = jwtProvider.parse(accessToken);
        LocalDateTime expiresAt = LocalDateTime.ofInstant(claims.getExpiresAt(), ZoneOffset.UTC);
        blacklist.save(AccessTokenBlacklist.create(
                claims.getJti(), userRepository.getReferenceById(principal.userId()), expiresAt));
        refreshTokens.revokeFamily(principal.sid(), LocalDateTime.now(clock), RevokeReason.LOGOUT);
    }

    // 모든 기기: token_version을 올려 모든 Access Token을 무효화하고, 모든 Refresh를 폐기한다
    @Transactional
    public void logoutAll(AuthPrincipal principal) {
        userRepository.incrementTokenVersion(principal.userId());
        refreshTokens.revokeAllByUser(principal.userId(), LocalDateTime.now(clock), RevokeReason.LOGOUT_ALL);
    }
}
