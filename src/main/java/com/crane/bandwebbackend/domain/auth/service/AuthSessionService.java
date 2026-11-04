package com.crane.bandwebbackend.domain.auth.service;

import com.crane.bandwebbackend.domain.auth.entity.RefreshToken;
import com.crane.bandwebbackend.domain.auth.repository.RefreshTokenRepository;
import com.crane.bandwebbackend.domain.user.entity.User;
import com.crane.bandwebbackend.domain.user.repository.UserRepository;
import com.crane.bandwebbackend.global.security.JwtProvider;
import com.crane.bandwebbackend.global.time.KstTime;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 로그인 묶음(family) 시작과 토큰 발급. API 3·4·132·133이 함께 쓴다. 호출한 쪽의 트랜잭션에 합류한다
@Service
@RequiredArgsConstructor
public class AuthSessionService {

    static final Duration REFRESH_TTL = Duration.ofDays(30);
    static final Duration FAMILY_TTL = Duration.ofDays(90);

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;
    private final Clock clock;

    // 쿠키에 담을 원문
    @Getter
    @RequiredArgsConstructor
    public static class Tokens {

        private final String accessToken;
        private final String refreshToken;
    }

    // 새 로그인 묶음. 클라이언트 정보는 기록용이며 차단 기준이 아니다
    @Transactional
    public Tokens start(User user, String ip, String userAgent) {
        LocalDateTime now = now();
        String familyId = UUID.randomUUID().toString();
        String refresh = TokenCodec.newRaw();
        refreshTokenRepository.save(RefreshToken.issue(
                user,
                familyId,
                TokenCodec.hash(refresh),
                now.plus(REFRESH_TTL),
                now.plus(FAMILY_TTL),
                ip,
                truncate(userAgent, 255)));
        touchLastActive(user.getId(), now);
        return new Tokens(accessToken(user, familyId), refresh);
    }

    String accessToken(User user, String familyId) {
        return jwtProvider
                .createAccessToken(user.getId(), familyId, user.getTokenVersion())
                .getToken();
    }

    // G-11: 오늘 0시(KST) 이후 기록이 없을 때만 갱신
    void touchLastActive(long userId, LocalDateTime now) {
        LocalDate kstToday = KstTime.toKstDate(now);
        LocalDateTime dayStartUtc = KstTime.toUtc(kstToday.atStartOfDay().atOffset(KstTime.KST));
        userRepository.touchLastActive(userId, now, dayStartUtc);
    }

    LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
