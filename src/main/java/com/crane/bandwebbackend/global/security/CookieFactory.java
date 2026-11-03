package com.crane.bandwebbackend.global.security;

import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

// 쿠키 속성을 만드는 유일한 곳(U12, U13). 모두 HttpOnly, SameSite=Lax, Domain 없음.
// 운영·스테이징은 접두사(__Host-/__Secure-)와 Secure, local 프로필은 접두사 없음·Secure=false
@Component
public class CookieFactory {

    public static final String REFRESH_PATH = "/api/v1/auth/refresh";

    private static final Duration REFRESH_MAX_AGE = Duration.ofDays(30);
    private static final Duration EXCHANGE_MAX_AGE = Duration.ofSeconds(60);
    private static final Duration SHORT_MAX_AGE = Duration.ofSeconds(600);

    private final boolean hostPrefix;
    private final boolean secure;
    private final Duration accessMaxAge;

    public CookieFactory(AppProperties properties) {
        this.hostPrefix = properties.cookie().hostPrefix();
        this.secure = properties.cookie().secure();
        this.accessMaxAge = properties.jwt().accessTtl();
    }

    public String accessName() {
        return hostName("rr_at");
    }

    public String refreshName() {
        return (hostPrefix ? "__Secure-" : "") + "rr_rt";
    }

    public String stateName() {
        return hostName("rr_state");
    }

    public String exchangeName() {
        return hostName("rr_xchg");
    }

    public String restoreName() {
        return hostName("rr_restore");
    }

    public ResponseCookie access(String value) {
        return build(accessName(), value, "/", accessMaxAge);
    }

    public ResponseCookie refresh(String value) {
        return build(refreshName(), value, REFRESH_PATH, REFRESH_MAX_AGE);
    }

    public ResponseCookie state(String value) {
        return build(stateName(), value, "/", SHORT_MAX_AGE);
    }

    public ResponseCookie exchange(String value) {
        return build(exchangeName(), value, "/", EXCHANGE_MAX_AGE);
    }

    public ResponseCookie restore(String value) {
        return build(restoreName(), value, "/", SHORT_MAX_AGE);
    }

    public ResponseCookie deleteAccess() {
        return build(accessName(), "", "/", Duration.ZERO);
    }

    public ResponseCookie deleteRefresh() {
        return build(refreshName(), "", REFRESH_PATH, Duration.ZERO);
    }

    public ResponseCookie deleteState() {
        return build(stateName(), "", "/", Duration.ZERO);
    }

    public ResponseCookie deleteExchange() {
        return build(exchangeName(), "", "/", Duration.ZERO);
    }

    public ResponseCookie deleteRestore() {
        return build(restoreName(), "", "/", Duration.ZERO);
    }

    private String hostName(String base) {
        return (hostPrefix ? "__Host-" : "") + base;
    }

    private ResponseCookie build(String name, String value, String path, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path(path)
                .maxAge(maxAge)
                .build();
    }
}
