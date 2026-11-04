package com.crane.bandwebbackend.domain.auth.service;

import com.crane.bandwebbackend.domain.auth.repository.AccessTokenBlacklistRepository;
import com.crane.bandwebbackend.domain.auth.repository.OneTimeTokenRepository;
import com.crane.bandwebbackend.domain.auth.repository.RefreshTokenRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// §16 물리 삭제 범위 중 인증 토큰 3종. 모두 조건부 DELETE·UPDATE라 여러 번 실행돼도 안전하다
@Service
@RequiredArgsConstructor
public class AuthTokenCleanupService {

    static final Duration ONE_TIME_TOKEN_RETENTION = Duration.ofDays(1);
    static final Duration REFRESH_TOKEN_RETENTION = Duration.ofDays(7);

    private final OneTimeTokenRepository oneTimeTokens;
    private final RefreshTokenRepository refreshTokens;
    private final AccessTokenBlacklistRepository blacklist;
    private final Clock clock;

    public record Deleted(int oneTimeTokens, int refreshTokens, int blacklistEntries) {}

    // 소비되지 않은 만료 토큰에 남은 카카오 프로필 값(닉네임·이미지)을 만료 직후 지운다
    @Transactional
    public int clearExpiredProfiles() {
        return oneTimeTokens.clearExpiredProfiles(now());
    }

    // 1회용 토큰은 만료 + 1일, Refresh Token은 만료 + 7일(폐기된 토큰도 같은 기준), 블랙리스트는 만료 시각이 지나면 삭제
    @Transactional
    public Deleted deleteExpired() {
        LocalDateTime now = now();
        int oneTime = oneTimeTokens.deleteExpiredBefore(now.minus(ONE_TIME_TOKEN_RETENTION));
        LocalDateTime refreshCutoff = now.minus(REFRESH_TOKEN_RETENTION);
        refreshTokens.detachReplacementsBefore(refreshCutoff);
        int refresh = refreshTokens.deleteExpiredBefore(refreshCutoff);
        int blacklisted = blacklist.deleteByExpiresAtBefore(now);
        return new Deleted(oneTime, refresh, blacklisted);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
