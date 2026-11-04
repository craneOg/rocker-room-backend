package com.crane.bandwebbackend.domain.auth.controller;

import static com.crane.bandwebbackend.domain.auth.controller.LoginFixture.cookieValue;
import static com.crane.bandwebbackend.domain.auth.controller.LoginFixture.jsonPost;
import static com.crane.bandwebbackend.domain.auth.controller.LoginFixture.setCookie;
import static com.crane.bandwebbackend.domain.auth.controller.LoginFixture.sha256Hex;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crane.bandwebbackend.TestcontainersConfiguration;
import com.crane.bandwebbackend.domain.auth.client.KakaoOAuthClient;
import com.crane.bandwebbackend.domain.auth.controller.LoginFixture.Session;
import com.crane.bandwebbackend.domain.auth.dto.KakaoProfile;
import com.crane.bandwebbackend.global.security.CookieFactory;
import com.crane.bandwebbackend.global.security.JwtProvider;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class AuthSessionFlowTest {

    private static final KakaoProfile ALICE = new KakaoProfile("k-alice", "앨리스", null);
    private static final String PROTECTED = "/api/v1/_test/protected";
    private static final long DAY = 24L * 3600;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    CookieFactory cookies;

    @Autowired
    JwtProvider jwtProvider;

    @MockitoBean
    KakaoOAuthClient kakao;

    LoginFixture fixture;

    @BeforeEach
    void setUp() {
        clean();
        fixture = new LoginFixture(mockMvc, cookies);
    }

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM access_token_blacklist");
        jdbc.update("DELETE FROM refresh_token");
        jdbc.update("DELETE FROM one_time_token");
        jdbc.update("DELETE FROM auth_identity");
        jdbc.update("DELETE FROM users");
    }

    private MockHttpServletRequestBuilder refreshRequest(String refreshToken) {
        var request = jsonPost("/api/v1/auth/refresh");
        if (refreshToken != null) {
            request.cookie(new Cookie(cookies.refreshName(), refreshToken));
        }
        return request;
    }

    private MvcResult refresh(String refreshToken) throws Exception {
        return mockMvc.perform(refreshRequest(refreshToken)).andReturn();
    }

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder request, Session session) {
        return request.cookie(new Cookie(cookies.accessName(), session.getAccessToken()));
    }

    private int protectedStatus(String accessToken) throws Exception {
        return mockMvc.perform(get(PROTECTED).cookie(new Cookie(cookies.accessName(), accessToken)))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private void assertUnauthenticated(MvcResult result, String reason) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .contains("\"code\":\"UNAUTHENTICATED\"")
                .contains("\"reason\":\"" + reason + "\"");
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    // ---- API 4 ----

    @Test
    @DisplayName("API 4: 재발급하면 Refresh가 회전하고 새 Access·Refresh 쿠키를 받는다")
    void rotates() throws Exception {
        Session first = fixture.login(kakao, ALICE);
        String familyId = jdbc.queryForObject("SELECT family_id FROM refresh_token", String.class);

        MvcResult result = fresh(first);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String newRefresh = cookieValue(result, cookies.refreshName());
        String newAccess = cookieValue(result, cookies.accessName());
        assertThat(newRefresh).isNotEqualTo(first.getRefreshToken());
        assertThat(newAccess).isNotEqualTo(first.getAccessToken());
        assertThat(setCookie(result, cookies.accessName())).contains("HttpOnly", "Max-Age=900", "Path=/");
        assertThat(setCookie(result, cookies.refreshName()))
                .contains("HttpOnly", "Max-Age=2592000", "Path=/api/v1/auth/refresh");
        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .doesNotContain(newRefresh)
                .doesNotContain(newAccess)
                .doesNotContain("loginResult");

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, HEX(token_hash) h, family_id, revoke_reason, revoked_at, replaced_by_id, family_expires_at"
                        + " FROM refresh_token ORDER BY id");
        assertThat(rows).hasSize(2);
        Map<String, Object> old = rows.get(0);
        Map<String, Object> next = rows.get(1);
        assertThat(old.get("revoke_reason")).isEqualTo("ROTATED");
        assertThat(old.get("revoked_at")).isNotNull();
        assertThat(old.get("replaced_by_id")).isEqualTo(next.get("id"));
        assertThat(old.get("h")).isEqualTo(sha256Hex(first.getRefreshToken()));
        assertThat(next.get("h")).isEqualTo(sha256Hex(newRefresh));
        assertThat(next.get("family_id")).isEqualTo(familyId).isEqualTo(old.get("family_id"));
        assertThat(next.get("revoked_at")).isNull();
        assertThat(next.get("family_expires_at")).isEqualTo(old.get("family_expires_at"));

        JwtProvider.AccessTokenClaims claims = jwtProvider.parse(newAccess);
        assertThat(claims.getSid()).isEqualTo(familyId);
        assertThat(protectedStatus(newAccess)).isEqualTo(200);
    }

    private MvcResult fresh(Session session) throws Exception {
        return refresh(session.getRefreshToken());
    }

    @Test
    @DisplayName("API 4: 응답 본문은 user와 onboardingRequired")
    void responseBody() throws Exception {
        Session session = fixture.login(kakao, ALICE);
        jdbc.update("UPDATE users SET handle = 'alice_01'");

        mockMvc.perform(refreshRequest(session.getRefreshToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.name").value("앨리스"))
                .andExpect(jsonPath("$.data.user.handle").value("alice_01"))
                .andExpect(jsonPath("$.data.user.id").isString())
                .andExpect(jsonPath("$.data.onboardingRequired").value(false))
                .andExpect(jsonPath("$.meta.requestId").exists());
    }

    @Test
    @DisplayName("API 4: 새 토큰 만료는 min(지금+30일, 묶음 만료)")
    void expiryIsCappedByFamily() throws Exception {
        Session session = fixture.login(kakao, ALICE);
        jdbc.update("UPDATE refresh_token SET family_expires_at = DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 1 DAY)");

        assertThat(fresh(session).getResponse().getStatus()).isEqualTo(200);

        assertThat(
                        count(
                                "SELECT COUNT(*) FROM refresh_token WHERE revoked_at IS NULL AND expires_at = family_expires_at"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("API 4: 일반 경우 새 토큰 만료는 30일(묶음 만료가 더 멀 때)")
    void expiryIsThirtyDays() throws Exception {
        Session session = fixture.login(kakao, ALICE);
        jdbc.update("UPDATE refresh_token SET family_expires_at = DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 80 DAY)");

        fresh(session);

        long seconds = jdbc.queryForObject(
                "SELECT TIMESTAMPDIFF(SECOND, UTC_TIMESTAMP(6), expires_at) FROM refresh_token WHERE revoked_at IS NULL",
                Long.class);
        assertThat(seconds).isBetween(30 * DAY - 60, 30 * DAY);
    }

    @Test
    @DisplayName("API 4: 이미 교체된 토큰을 다시 쓰면 401 REUSE_DETECTED이고 같은 묶음 전체가 폐기된다")
    void reuseDetected() throws Exception {
        Session session = fixture.login(kakao, ALICE);
        MvcResult rotated = fresh(session);
        String newRefresh = cookieValue(rotated, cookies.refreshName());
        String newAccess = cookieValue(rotated, cookies.accessName());

        assertUnauthenticated(refresh(session.getRefreshToken()), "REUSE_DETECTED");

        assertThat(count("SELECT COUNT(*) FROM refresh_token WHERE revoked_at IS NULL"))
                .isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT revoke_reason FROM refresh_token WHERE HEX(token_hash) = ?",
                        String.class,
                        sha256Hex(newRefresh)))
                .isEqualTo("REUSE_DETECTED");
        // 폐기된 묶음의 최신 토큰도 더는 쓸 수 없다
        assertUnauthenticated(refresh(newRefresh), "SESSION_REVOKED");
        assertThat(newAccess).isNotBlank();
    }

    @Test
    @DisplayName("API 4: 다른 로그인 묶음은 재사용 탐지의 영향을 받지 않는다")
    void otherFamilyUnaffected() throws Exception {
        Session phone = fixture.login(kakao, ALICE);
        Session laptop = fixture.login(kakao, ALICE);
        fresh(phone);

        assertUnauthenticated(refresh(phone.getRefreshToken()), "REUSE_DETECTED");

        assertThat(fresh(laptop).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("API 4: 쿠키 없음·발급한 적 없는 값·만료·묶음 만료는 401 REFRESH_INVALID")
    void invalidTokens() throws Exception {
        Session session = fixture.login(kakao, ALICE);

        assertUnauthenticated(refresh(null), "REFRESH_INVALID");
        assertUnauthenticated(refresh("never-issued"), "REFRESH_INVALID");
        assertUnauthenticated(refresh("x".repeat(101)), "REFRESH_INVALID");

        jdbc.update("UPDATE refresh_token SET expires_at = '2000-01-01 00:00:00'");
        assertUnauthenticated(refresh(session.getRefreshToken()), "REFRESH_INVALID");

        jdbc.update("UPDATE refresh_token SET expires_at = DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 1 DAY),"
                + " family_expires_at = '2000-01-01 00:00:00'");
        assertUnauthenticated(refresh(session.getRefreshToken()), "REFRESH_INVALID");
        assertThat(count("SELECT COUNT(*) FROM refresh_token")).isEqualTo(1);
    }

    @Test
    @DisplayName("API 4: 탈퇴한 사용자는 401 SESSION_REVOKED")
    void withdrawnUser() throws Exception {
        Session session = fixture.login(kakao, ALICE);
        jdbc.update("UPDATE users SET status = 'WITHDRAWN', withdrawn_at = UTC_TIMESTAMP(6)");

        assertUnauthenticated(refresh(session.getRefreshToken()), "SESSION_REVOKED");
    }

    @Test
    @DisplayName("API 4: 같은 Refresh로 동시에 요청하면 한 번만 회전하고 나머지는 거부되며 묶음이 폐기된다")
    void concurrentRefresh() throws Exception {
        Session session = fixture.login(kakao, ALICE);
        int threads = 5;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return refresh(session.getRefreshToken()).getResponse().getStatus();
            }));
        }
        start.countDown();
        int ok = 0;
        for (Future<Integer> result : results) {
            if (result.get() == 200) {
                ok++;
            }
        }
        pool.shutdown();

        assertThat(ok).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM refresh_token WHERE revoke_reason = 'ROTATED'"))
                .isEqualTo(1);
        // 선점하지 못한 요청이 재사용으로 판정되어 묶음 전체(새로 발급된 토큰 포함)가 폐기된다
        assertThat(count("SELECT COUNT(*) FROM refresh_token WHERE revoked_at IS NULL"))
                .isZero();
    }

    @Test
    @DisplayName("API 4: X-Requested-With 없음 403, JSON이 아닌 Content-Type 400, 다른 Origin 403")
    void requestGuards() throws Exception {
        Session session = fixture.login(kakao, ALICE);
        Cookie refreshCookie = new Cookie(cookies.refreshName(), session.getRefreshToken());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .cookie(refreshCookie))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("{}")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(refreshCookie))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].reason").value("UNSUPPORTED_MEDIA_TYPE"));
        mockMvc.perform(refreshRequest(session.getRefreshToken()).header("Origin", "https://evil.example"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.details[0].reason").value("ORIGIN_NOT_ALLOWED"));

        // 거부된 요청은 토큰을 회전시키지 않는다
        assertThat(fresh(session).getResponse().getStatus()).isEqualTo(200);
    }

    // ---- API 5 ----

    @Test
    @DisplayName("API 5: 현재 기기 로그아웃은 204, 쿠키 삭제, Access 블랙리스트, 같은 묶음 Refresh 폐기(LOGOUT)")
    void logout() throws Exception {
        Session session = fixture.login(kakao, ALICE);
        String jti = jwtProvider.parse(session.getAccessToken()).getJti();

        MvcResult result = mockMvc.perform(authed(jsonPost("/api/v1/auth/logout"), session))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(204);
        assertThat(result.getResponse().getContentAsByteArray()).isEmpty();
        assertThat(setCookie(result, cookies.accessName()))
                .contains("Max-Age=0")
                .contains("Path=/;");
        assertThat(setCookie(result, cookies.refreshName()))
                .contains("Max-Age=0")
                .contains("Path=/api/v1/auth/refresh;");
        assertThat(count("SELECT COUNT(*) FROM access_token_blacklist WHERE jti = ?", jti))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT revoke_reason FROM refresh_token", String.class))
                .isEqualTo("LOGOUT");

        // 같은 Access는 즉시 거부, Refresh는 폐기됨
        assertThat(protectedStatus(session.getAccessToken())).isEqualTo(401);
        assertUnauthenticated(refresh(session.getRefreshToken()), "SESSION_REVOKED");
    }

    @Test
    @DisplayName("API 5: 다른 기기(다른 로그인 묶음)는 로그아웃되지 않는다")
    void logoutKeepsOtherDevices() throws Exception {
        Session phone = fixture.login(kakao, ALICE);
        Session laptop = fixture.login(kakao, ALICE);

        mockMvc.perform(authed(jsonPost("/api/v1/auth/logout"), phone)).andExpect(status().isNoContent());

        assertThat(protectedStatus(laptop.getAccessToken())).isEqualTo(200);
        assertThat(fresh(laptop).getResponse().getStatus()).isEqualTo(200);
        assertThat(count("SELECT COUNT(*) FROM refresh_token WHERE revoke_reason = 'LOGOUT'"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("API 5: 인증이 없으면 401, X-Requested-With가 없으면 403, 두 번째 로그아웃은 401")
    void logoutGuards() throws Exception {
        Session session = fixture.login(kakao, ALICE);

        mockMvc.perform(jsonPost("/api/v1/auth/logout")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .cookie(new Cookie(cookies.accessName(), session.getAccessToken())))
                .andExpect(status().isForbidden());
        mockMvc.perform(authed(jsonPost("/api/v1/auth/logout"), session)).andExpect(status().isNoContent());
        mockMvc.perform(authed(jsonPost("/api/v1/auth/logout"), session)).andExpect(status().isUnauthorized());
    }

    // ---- API 6 ----

    @Test
    @DisplayName("API 6: 모든 기기 로그아웃은 token_version +1, 모든 Refresh 폐기(LOGOUT_ALL), 모든 Access 무효")
    void logoutAll() throws Exception {
        Session phone = fixture.login(kakao, ALICE);
        Session laptop = fixture.login(kakao, ALICE);
        int before = jdbc.queryForObject("SELECT token_version FROM users", Integer.class);

        MvcResult result = mockMvc.perform(authed(jsonPost("/api/v1/auth/logout-all"), phone))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(204);
        assertThat(setCookie(result, cookies.accessName())).contains("Max-Age=0");
        assertThat(setCookie(result, cookies.refreshName())).contains("Max-Age=0");
        assertThat(jdbc.queryForObject("SELECT token_version FROM users", Integer.class))
                .isEqualTo(before + 1);
        assertThat(count("SELECT COUNT(*) FROM refresh_token WHERE revoke_reason = 'LOGOUT_ALL'"))
                .isEqualTo(2);
        assertThat(protectedStatus(phone.getAccessToken())).isEqualTo(401);
        assertThat(protectedStatus(laptop.getAccessToken())).isEqualTo(401);
        assertUnauthenticated(refresh(laptop.getRefreshToken()), "SESSION_REVOKED");

        // 다시 로그인하면 새 버전의 토큰으로 정상 동작한다
        Session again = fixture.login(kakao, ALICE);
        assertThat(jwtProvider.parse(again.getAccessToken()).getTokenVersion()).isEqualTo(before + 1);
        assertThat(protectedStatus(again.getAccessToken())).isEqualTo(200);
    }

    @Test
    @DisplayName("API 6: 인증이 없으면 401, X-Requested-With가 없으면 403")
    void logoutAllGuards() throws Exception {
        Session session = fixture.login(kakao, ALICE);

        mockMvc.perform(jsonPost("/api/v1/auth/logout-all")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/logout-all")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .cookie(new Cookie(cookies.accessName(), session.getAccessToken())))
                .andExpect(status().isForbidden());
        assertThat(protectedStatus(session.getAccessToken())).isEqualTo(200);
    }
}
