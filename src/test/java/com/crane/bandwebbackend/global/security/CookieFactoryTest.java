package com.crane.bandwebbackend.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

class CookieFactoryTest {

    private static CookieFactory factory(boolean hostPrefix, boolean secure) {
        return new CookieFactory(new AppProperties(
                new AppProperties.Jwt("x".repeat(32), Duration.ofMinutes(15)),
                new AppProperties.Cors("http://localhost:5173"),
                new AppProperties.Cookie(hostPrefix, secure)));
    }

    private static void assertCommon(ResponseCookie c, String name, String path, long maxAgeSeconds, boolean secure) {
        assertThat(c.getName()).isEqualTo(name);
        assertThat(c.getPath()).isEqualTo(path);
        assertThat(c.getMaxAge().getSeconds()).isEqualTo(maxAgeSeconds);
        assertThat(c.isHttpOnly()).isTrue();
        assertThat(c.isSecure()).isEqualTo(secure);
        assertThat(c.getSameSite()).isEqualTo("Lax");
        assertThat(c.getDomain()).isNull();
    }

    @Test
    @DisplayName("0.5 U12 운영·스테이징: 접두사 이름, Secure, Path, Max-Age")
    void production() {
        CookieFactory f = factory(true, true);

        assertCommon(f.access("v"), "__Host-rr_at", "/", 900, true);
        assertCommon(f.refresh("v"), "__Secure-rr_rt", "/api/v1/auth/refresh", 30L * 24 * 3600, true);
        assertCommon(f.state("v"), "__Host-rr_state", "/", 600, true);
        assertCommon(f.exchange("v"), "__Host-rr_xchg", "/", 60, true);
        assertCommon(f.restore("v"), "__Host-rr_restore", "/", 600, true);
    }

    @Test
    @DisplayName("0.5 U13 local: 접두사 없는 이름과 Secure=false, 나머지 속성은 동일")
    void local() {
        CookieFactory f = factory(false, false);

        assertCommon(f.access("v"), "rr_at", "/", 900, false);
        assertCommon(f.refresh("v"), "rr_rt", "/api/v1/auth/refresh", 30L * 24 * 3600, false);
        assertCommon(f.state("v"), "rr_state", "/", 600, false);
        assertCommon(f.exchange("v"), "rr_xchg", "/", 60, false);
        assertCommon(f.restore("v"), "rr_restore", "/", 600, false);
    }

    @Test
    @DisplayName("0.5 삭제 쿠키는 같은 이름·Path에 Max-Age=0과 빈 값")
    void deleteCookies() {
        CookieFactory f = factory(true, true);

        assertCommon(f.deleteAccess(), "__Host-rr_at", "/", 0, true);
        assertCommon(f.deleteRefresh(), "__Secure-rr_rt", "/api/v1/auth/refresh", 0, true);
        assertCommon(f.deleteState(), "__Host-rr_state", "/", 0, true);
        assertCommon(f.deleteExchange(), "__Host-rr_xchg", "/", 0, true);
        assertCommon(f.deleteRestore(), "__Host-rr_restore", "/", 0, true);
        assertThat(f.deleteAccess().getValue()).isEmpty();
        assertThat(f.access("v").toString())
                .contains("HttpOnly", "Secure", "SameSite=Lax")
                .doesNotContain("Domain");
    }

    @Test
    @DisplayName("Access 쿠키 Max-Age는 app.jwt.access-ttl을 따른다")
    void accessMaxAgeFollowsJwtTtl() {
        CookieFactory f = new CookieFactory(new AppProperties(
                new AppProperties.Jwt("x".repeat(32), Duration.ofMinutes(5)),
                new AppProperties.Cors("http://localhost:5173"),
                new AppProperties.Cookie(false, false)));

        assertThat(f.access("v").getMaxAge()).isEqualTo(Duration.ofMinutes(5));
    }
}
