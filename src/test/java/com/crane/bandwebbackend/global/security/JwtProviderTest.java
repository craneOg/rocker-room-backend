package com.crane.bandwebbackend.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtProviderTest {

    // 테스트 전용 키(32바이트 이상)
    private static final String KEY = "unit-test-only-key-0123456789-abcdefghij-0123456789-abcdef";
    private static final Instant NOW = Instant.parse("2026-11-02T00:00:00Z");

    private static AppProperties props(String key) {
        return new AppProperties(
                new AppProperties.Jwt(key, Duration.ofMinutes(15)),
                new AppProperties.Cors("http://localhost:5173"),
                new AppProperties.Cookie(true, true));
    }

    private static JwtProvider provider(String key, Instant now) {
        return new JwtProvider(props(key), Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("0.5 발급한 토큰을 검증하면 sub·sid·jti·tv·exp(15분)가 그대로 나온다")
    void issueAndParse() {
        JwtProvider provider = provider(KEY, NOW);

        JwtProvider.IssuedToken issued = provider.createAccessToken(42L, "sid-1", 7);
        JwtProvider.AccessTokenClaims claims = provider.parse(issued.token());

        assertThat(claims.userId()).isEqualTo(42L);
        assertThat(claims.sid()).isEqualTo("sid-1");
        assertThat(claims.tokenVersion()).isEqualTo(7);
        assertThat(claims.jti()).isEqualTo(issued.jti());
        assertThat(issued.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        assertThat(claims.expiresAt()).isEqualTo(issued.expiresAt());
    }

    @Test
    @DisplayName("0.5 서명이 변조된 토큰은 INVALID_SIGNATURE")
    void tamperedSignature() {
        JwtProvider provider = provider(KEY, NOW);
        String token = provider.createAccessToken(1L, "s", 0).token();
        int idx = token.length() - 10;
        char replaced = token.charAt(idx) == 'A' ? 'B' : 'A';
        String tampered = token.substring(0, idx) + replaced + token.substring(idx + 1);

        assertReason(provider, tampered, AccessTokenException.Reason.INVALID_SIGNATURE);
    }

    @Test
    @DisplayName("0.5 만료된 토큰은 EXPIRED")
    void expired() {
        String token = provider(KEY, NOW).createAccessToken(1L, "s", 0).token();

        assertReason(provider(KEY, NOW.plus(Duration.ofMinutes(16))), token, AccessTokenException.Reason.EXPIRED);
    }

    @Test
    @DisplayName("0.5 다른 키로 서명한 토큰은 거부")
    void otherKey() {
        String token = provider("another-test-only-key-0123456789-abcdefgh", NOW)
                .createAccessToken(1L, "s", 0)
                .token();

        assertReason(provider(KEY, NOW), token, AccessTokenException.Reason.INVALID_SIGNATURE);
    }

    @Test
    @DisplayName("0.5 알고리즘 none 토큰은 거부")
    void algNone() {
        String token = Jwts.builder()
                .subject("1")
                .id("00000000-0000-0000-0000-000000000000")
                .claim("tv", 0)
                .expiration(Date.from(NOW.plus(Duration.ofMinutes(5))))
                .compact();

        assertReason(provider(KEY, NOW), token, AccessTokenException.Reason.MALFORMED);
    }

    @Test
    @DisplayName("0.5 같은 키라도 HS256이 아닌 알고리즘(HS384)은 거부")
    void otherAlgorithm() {
        SecretKey key = Keys.hmacShaKeyFor(KEY.getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder()
                .subject("1")
                .id("00000000-0000-0000-0000-000000000000")
                .claim("tv", 0)
                .expiration(Date.from(NOW.plus(Duration.ofMinutes(5))))
                .signWith(key, Jwts.SIG.HS384)
                .compact();

        assertThatThrownBy(() -> provider(KEY, NOW).parse(token)).isInstanceOf(AccessTokenException.class);
    }

    @Test
    @DisplayName("0.5 형식이 틀린 문자열은 MALFORMED")
    void malformed() {
        assertReason(provider(KEY, NOW), "not-a-jwt", AccessTokenException.Reason.MALFORMED);
    }

    @Test
    @DisplayName("0.5 키가 비었거나 32바이트 미만이면 기동 실패")
    void shortKeyFailsStartup() {
        assertThatThrownBy(() -> provider("", NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> provider(null, NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> provider("a".repeat(31), NOW)).isInstanceOf(IllegalStateException.class);
        provider("a".repeat(32), NOW);
    }

    private static void assertReason(JwtProvider provider, String token, AccessTokenException.Reason reason) {
        assertThatThrownBy(() -> provider.parse(token))
                .isInstanceOfSatisfying(
                        AccessTokenException.class,
                        e -> assertThat(e.getReason()).isEqualTo(reason));
    }

    @Test
    @DisplayName("0.5 설정 객체의 toString에 JWT 키가 노출되지 않는다")
    void secretIsMaskedInToString() {
        AppProperties.Jwt jwt = new AppProperties.Jwt("0123456789abcdef0123456789abcdef", Duration.ofMinutes(15));

        assertThat(jwt.toString()).doesNotContain("0123456789abcdef").contains("***");
    }
}
