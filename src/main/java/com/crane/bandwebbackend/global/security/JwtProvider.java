package com.crane.bandwebbackend.global.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// Access Token 발급·검증(U8): HS256 단일 키. 키는 환경 변수 JWT_SECRET, 32바이트 미만이면 기동 실패
@Component
public class JwtProvider {

    private static final int MIN_KEY_BYTES = 32;
    private static final String ALGORITHM = "HS256";

    private final SecretKey key;
    private final Duration accessTtl;
    private final Clock clock;

    public JwtProvider(AppProperties properties, Clock clock) {
        String raw = properties.jwt() == null ? null : properties.jwt().secret();
        if (raw == null || raw.getBytes(StandardCharsets.UTF_8).length < MIN_KEY_BYTES) {
            throw new IllegalStateException("JWT_SECRET must be set and at least 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(raw.getBytes(StandardCharsets.UTF_8));
        this.accessTtl = properties.jwt().accessTtl();
        this.clock = clock;
    }

    public IssuedToken createAccessToken(long userId, String sid, int tokenVersion) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(accessTtl);
        String jti = UUID.randomUUID().toString();
        String token = Jwts.builder()
                .subject(Long.toString(userId))
                .id(jti)
                .claim("sid", sid)
                .claim("tv", tokenVersion)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
        return new IssuedToken(token, jti, expiresAt);
    }

    public AccessTokenClaims parse(String token) {
        try {
            Jws<Claims> jws = Jwts.parser()
                    .verifyWith(key)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token);
            if (!ALGORITHM.equals(jws.getHeader().getAlgorithm())) {
                throw new AccessTokenException(AccessTokenException.Reason.INVALID_SIGNATURE, null);
            }
            Claims c = jws.getPayload();
            return new AccessTokenClaims(
                    Long.parseLong(c.getSubject()),
                    c.get("sid", String.class),
                    c.getId(),
                    c.get("tv", Integer.class),
                    c.getExpiration().toInstant());
        } catch (ExpiredJwtException e) {
            throw new AccessTokenException(AccessTokenException.Reason.EXPIRED, e);
        } catch (SignatureException e) {
            throw new AccessTokenException(AccessTokenException.Reason.INVALID_SIGNATURE, e);
        } catch (JwtException | IllegalArgumentException | NullPointerException e) {
            // 형식 오류, 허용하지 않는 알고리즘(none 포함), 필수 클레임 누락·형변환 실패
            throw new AccessTokenException(AccessTokenException.Reason.MALFORMED, e);
        }
    }

    @Getter
    @RequiredArgsConstructor
    public static class IssuedToken {

        private final String token;
        private final String jti;
        private final Instant expiresAt;
    }

    @Getter
    @RequiredArgsConstructor
    public static class AccessTokenClaims {

        private final long userId;
        private final String sid;
        private final String jti;
        private final int tokenVersion;
        private final Instant expiresAt;
    }
}
