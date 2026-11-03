package com.crane.bandwebbackend.domain.auth.controller;

import static com.crane.bandwebbackend.domain.auth.controller.LoginFixture.cookieValue;
import static com.crane.bandwebbackend.domain.auth.controller.LoginFixture.hasCookie;
import static com.crane.bandwebbackend.domain.auth.controller.LoginFixture.jsonPost;
import static com.crane.bandwebbackend.domain.auth.controller.LoginFixture.setCookie;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crane.bandwebbackend.TestcontainersConfiguration;
import com.crane.bandwebbackend.domain.auth.client.KakaoOAuthClient;
import com.crane.bandwebbackend.domain.auth.dto.KakaoProfile;
import com.crane.bandwebbackend.global.security.CookieFactory;
import com.crane.bandwebbackend.global.security.JwtProvider;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
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
class AccountRestorationFlowTest {

    private static final KakaoProfile FIRST = new KakaoProfile("k-alice", "앨리스", null);
    // 재로그인 때 카카오가 주는 값(133의 새 사용자는 이 값으로 만들어진다)
    private static final KakaoProfile AGAIN = new KakaoProfile("k-alice", "새닉네임", "https://k.example/new.png");
    private static final String PROTECTED = "/api/v1/_test/protected";

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
    long oldUserId;

    @BeforeEach
    void setUp() {
        clean();
        fixture = new LoginFixture(mockMvc, cookies);
    }

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM audit_log");
        jdbc.update("DELETE FROM user_withdrawal_archive");
        jdbc.update("DELETE FROM access_token_blacklist");
        jdbc.update("DELETE FROM refresh_token");
        jdbc.update("DELETE FROM one_time_token");
        jdbc.update("DELETE FROM auth_identity");
        jdbc.update("DELETE FROM users");
    }

    // ---- 도우미 ----

    // 로그인해 사용자를 만든 뒤, 10일 전에 탈퇴한 상태(이름·사진은 보관 테이블로)로 바꾼다
    private void withdrawAlice(int daysAgo) throws Exception {
        fixture.login(kakao, FIRST);
        oldUserId = jdbc.queryForObject("SELECT id FROM users", Long.class);
        jdbc.update(
                "UPDATE users SET name = '앨리스', profile_image_url = 'https://cdn.example/alice.png' WHERE id = ?",
                oldUserId);
        jdbc.update(
                "INSERT INTO user_withdrawal_archive (user_id, name, profile_image_url, withdrawn_at)"
                        + " VALUES (?, '앨리스', 'https://cdn.example/alice.png', DATE_SUB(UTC_TIMESTAMP(6), INTERVAL ? DAY))",
                oldUserId,
                daysAgo);
        // 탈퇴(API 9)가 하는 일 중 이 테스트에 필요한 것: 기존 로그인 묶음 폐기
        jdbc.update("UPDATE refresh_token SET revoked_at = UTC_TIMESTAMP(6), revoke_reason = 'WITHDRAWN'");
        jdbc.update(
                "UPDATE users SET name = '탈퇴한 사용자', profile_image_url = NULL, handle = NULL, status = 'WITHDRAWN',"
                        + " withdrawn_at = DATE_SUB(UTC_TIMESTAMP(6), INTERVAL ? DAY) WHERE id = ?",
                daysAgo,
                oldUserId);
    }

    // 탈퇴 계정으로 다시 로그인해 복구 선택 쿠키 원문을 받는다
    private String restoreCookie() throws Exception {
        MvcResult exchange = fixture.exchange(fixture.loginUntilExchangeCookie(kakao, AGAIN));
        assertThat(exchange.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .contains("RESTORATION_REQUIRED");
        return cookieValue(exchange, cookies.restoreName());
    }

    private MockHttpServletRequestBuilder withRestore(MockHttpServletRequestBuilder request, String token) {
        return token == null ? request : request.cookie(new Cookie(cookies.restoreName(), token));
    }

    private MvcResult restore(String token) throws Exception {
        return mockMvc.perform(withRestore(jsonPost("/api/v1/auth/restoration/restore"), token))
                .andReturn();
    }

    private MvcResult startNew(String token) throws Exception {
        return mockMvc.perform(withRestore(jsonPost("/api/v1/auth/restoration/start-new"), token))
                .andReturn();
    }

    private void assertError(MvcResult result, int status, String code, String reason) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(status);
        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .contains("\"code\":\"" + code + "\"")
                .contains("\"field\":\"request\"")
                .contains("\"reason\":\"" + reason + "\"");
        assertThat(hasCookie(result, cookies.accessName())).isFalse();
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private int protectedStatus(String accessToken) throws Exception {
        return mockMvc.perform(get(PROTECTED).cookie(new Cookie(cookies.accessName(), accessToken)))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    // ---- 카카오 프로필 전달 (Q1) ----

    @Test
    @DisplayName("Q1 카카오 프로필은 탈퇴 계정의 교환·복구 토큰에만 실리고, 소비되면 지워진다")
    void profileCarriedOnlyForWithdrawn() throws Exception {
        // 정상 사용자: 어느 토큰에도 프로필이 없다
        fixture.login(kakao, FIRST);
        assertThat(count("SELECT COUNT(*) FROM one_time_token WHERE profile_name IS NOT NULL"))
                .isZero();

        jdbc.update("DELETE FROM one_time_token");
        jdbc.update("DELETE FROM refresh_token");
        jdbc.update("UPDATE users SET status = 'WITHDRAWN', withdrawn_at = UTC_TIMESTAMP(6)");
        jdbc.update(
                "INSERT INTO user_withdrawal_archive (user_id, name, withdrawn_at) SELECT id, '앨리스', UTC_TIMESTAMP(6) FROM users");
        String exchangeCookie = fixture.loginUntilExchangeCookie(kakao, AGAIN);

        assertThat(jdbc.queryForObject(
                        "SELECT profile_name FROM one_time_token WHERE purpose = 'LOGIN_EXCHANGE'", String.class))
                .isEqualTo("새닉네임");

        fixture.exchange(exchangeCookie);

        assertThat(jdbc.queryForObject(
                        "SELECT profile_name FROM one_time_token WHERE purpose = 'LOGIN_EXCHANGE'", String.class))
                .isNull();
        assertThat(
                        jdbc.queryForMap(
                                "SELECT profile_name, profile_image_url FROM one_time_token WHERE purpose = 'ACCOUNT_RESTORATION'"))
                .containsEntry("profile_name", "새닉네임")
                .containsEntry("profile_image_url", "https://k.example/new.png");
    }

    // ---- API 131 ----

    @Test
    @DisplayName("API 131: 보관 이름·사진·탈퇴 시각·복구 기한(+90일)을 돌려주고 쿠키를 소비하지 않는다")
    void info() throws Exception {
        withdrawAlice(10);
        String token = restoreCookie();

        for (int i = 0; i < 2; i++) {
            MvcResult result = mockMvc.perform(withRestore(get("/api/v1/auth/restoration"), token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.displayName").value("앨리스"))
                    .andExpect(jsonPath("$.data.profileImageUrl").value("https://cdn.example/alice.png"))
                    .andExpect(jsonPath("$.data.withdrawnAt").value(org.hamcrest.Matchers.endsWith("+09:00")))
                    .andExpect(jsonPath("$.data.restorableUntil").value(org.hamcrest.Matchers.endsWith("+09:00")))
                    .andExpect(jsonPath("$.meta.requestId").exists())
                    .andReturn();
            String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
            OffsetDateTime withdrawnAt =
                    OffsetDateTime.parse(com.jayway.jsonpath.JsonPath.read(body, "$.data.withdrawnAt"));
            OffsetDateTime until =
                    OffsetDateTime.parse(com.jayway.jsonpath.JsonPath.read(body, "$.data.restorableUntil"));
            assertThat(until).isEqualTo(withdrawnAt.plusDays(90));
        }
        assertThat(
                        count(
                                "SELECT COUNT(*) FROM one_time_token WHERE purpose = 'ACCOUNT_RESTORATION' AND used_at IS NULL"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("API 131: 쿠키가 없거나 모르는 값·만료·이미 쓴 쿠키·다른 용도의 쿠키는 401 RESTORATION_COOKIE_INVALID")
    void infoInvalidCookie() throws Exception {
        withdrawAlice(10);
        String token = restoreCookie();

        assertError(
                mockMvc.perform(get("/api/v1/auth/restoration")).andReturn(),
                401,
                "UNAUTHENTICATED",
                "RESTORATION_COOKIE_INVALID");
        assertError(
                mockMvc.perform(withRestore(get("/api/v1/auth/restoration"), "never-issued"))
                        .andReturn(),
                401,
                "UNAUTHENTICATED",
                "RESTORATION_COOKIE_INVALID");

        String state =
                cookieValue(mockMvc.perform(get("/api/v1/auth/kakao/authorize")).andReturn(), cookies.stateName());
        assertError(
                mockMvc.perform(withRestore(get("/api/v1/auth/restoration"), state))
                        .andReturn(),
                401,
                "UNAUTHENTICATED",
                "RESTORATION_COOKIE_INVALID");

        jdbc.update("UPDATE one_time_token SET expires_at = '2000-01-01 00:00:00'");
        assertError(
                mockMvc.perform(withRestore(get("/api/v1/auth/restoration"), token))
                        .andReturn(),
                401,
                "UNAUTHENTICATED",
                "RESTORATION_COOKIE_INVALID");
    }

    // ---- API 132 ----

    @Test
    @DisplayName("API 132: 보관한 이름·사진으로 계정을 복구하고 로그인시킨다(handle은 복구하지 않음)")
    void restoreAccount() throws Exception {
        withdrawAlice(10);
        int versionBefore = jdbc.queryForObject("SELECT token_version FROM users", Integer.class);
        String token = restoreCookie();

        MvcResult result = restore(token);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .contains("\"loginResult\":\"EXISTING_USER\"");
        assertThat(setCookie(result, cookies.restoreName())).contains("Max-Age=0");
        assertThat(setCookie(result, cookies.accessName())).contains("HttpOnly", "Max-Age=900");
        assertThat(setCookie(result, cookies.refreshName())).contains("Max-Age=2592000", "Path=/api/v1/auth/refresh");

        Map<String, Object> user = jdbc.queryForMap(
                "SELECT id, name, profile_image_url, handle, status, withdrawn_at, token_version FROM users");
        assertThat(user)
                .containsEntry("id", oldUserId)
                .containsEntry("name", "앨리스")
                .containsEntry("profile_image_url", "https://cdn.example/alice.png")
                .containsEntry("status", "ACTIVE")
                .containsEntry("token_version", versionBefore + 1);
        assertThat(user.get("handle")).isNull();
        assertThat(user.get("withdrawn_at")).isNull();
        assertThat(count("SELECT COUNT(*) FROM user_withdrawal_archive")).isZero();
        assertThat(count("SELECT COUNT(*) FROM users")).isEqualTo(1);

        Map<String, Object> audit = jdbc.queryForMap(
                "SELECT club_id, actor_user_id, action, target_type, target_id, detail FROM audit_log");
        assertThat(audit)
                .containsEntry("action", "ACCOUNT_RESTORED")
                .containsEntry("target_type", "USER")
                .containsEntry("target_id", oldUserId)
                .containsEntry("actor_user_id", oldUserId);
        assertThat(audit.get("club_id")).isNull();
        assertThat(String.valueOf(audit.get("detail"))).doesNotContain("앨리스");

        String access = cookieValue(result, cookies.accessName());
        assertThat(jwtProvider.parse(access).tokenVersion()).isEqualTo(versionBefore + 1);
        assertThat(protectedStatus(access)).isEqualTo(200);
        assertThat(count("SELECT COUNT(*) FROM refresh_token WHERE revoked_at IS NULL"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("API 132: 쿠키는 한 번만 쓸 수 있다")
    void restoreCookieSingleUse() throws Exception {
        withdrawAlice(10);
        String token = restoreCookie();

        assertThat(restore(token).getResponse().getStatus()).isEqualTo(200);
        assertError(restore(token), 401, "UNAUTHENTICATED", "RESTORATION_COOKIE_INVALID");
    }

    @Test
    @DisplayName("API 132: 90일이 지났거나 보관 데이터가 없거나 이미 복구된 계정은 409 NOT_RESTORABLE")
    void restoreNotRestorable() throws Exception {
        withdrawAlice(91);
        String expired = restoreCookie();
        assertError(restore(expired), 409, "STATE_CONFLICT", "NOT_RESTORABLE");
        assertThat(jdbc.queryForObject("SELECT status FROM users", String.class))
                .isEqualTo("WITHDRAWN");
        assertThat(count("SELECT COUNT(*) FROM user_withdrawal_archive")).isEqualTo(1);

        jdbc.update("DELETE FROM user_withdrawal_archive");
        jdbc.update("UPDATE one_time_token SET used_at = NULL WHERE purpose = 'ACCOUNT_RESTORATION'");
        assertError(restore(expired), 409, "STATE_CONFLICT", "NOT_RESTORABLE");

        jdbc.update("UPDATE users SET status = 'ACTIVE'");
        assertError(restore(expired), 409, "STATE_CONFLICT", "NOT_RESTORABLE");
        assertThat(count("SELECT COUNT(*) FROM audit_log")).isZero();
    }

    @Test
    @DisplayName("API 132·133: X-Requested-With 없음 403, JSON이 아닌 Content-Type 400, 쿠키 없음 401")
    void requestGuards() throws Exception {
        withdrawAlice(10);
        String token = restoreCookie();
        Cookie cookie = new Cookie(cookies.restoreName(), token);

        for (String path : new String[] {"/api/v1/auth/restoration/restore", "/api/v1/auth/restoration/start-new"}) {
            mockMvc.perform(post(path)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}")
                            .cookie(cookie))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post(path)
                            .contentType(MediaType.TEXT_PLAIN)
                            .content("{}")
                            .header("X-Requested-With", "XMLHttpRequest")
                            .cookie(cookie))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.details[0].reason").value("UNSUPPORTED_MEDIA_TYPE"));
            mockMvc.perform(jsonPost(path)).andExpect(status().isUnauthorized());
        }
        // 거부된 요청은 쿠키를 소비하지 않는다
        assertThat(restore(token).getResponse().getStatus()).isEqualTo(200);
    }

    // ---- API 133 ----

    @Test
    @DisplayName("API 133: 보관 데이터를 파기하고 카카오 프로필로 새 사용자를 만들어 로그인시킨다")
    void startNew() throws Exception {
        withdrawAlice(10);
        String token = restoreCookie();

        MvcResult result = startNew(token);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .contains("\"loginResult\":\"NEW_USER\"");
        assertThat(setCookie(result, cookies.restoreName())).contains("Max-Age=0");

        assertThat(count("SELECT COUNT(*) FROM users")).isEqualTo(2);
        Map<String, Object> old = jdbc.queryForMap("SELECT name, status FROM users WHERE id = ?", oldUserId);
        assertThat(old).containsEntry("name", "탈퇴한 사용자").containsEntry("status", "WITHDRAWN");
        Map<String, Object> fresh = jdbc.queryForMap(
                "SELECT id, name, profile_image_url, handle, status FROM users WHERE id <> ?", oldUserId);
        assertThat(fresh)
                .containsEntry("name", "새닉네임")
                .containsEntry("profile_image_url", "https://k.example/new.png")
                .containsEntry("status", "ACTIVE");
        assertThat(fresh.get("handle")).isNull();

        assertThat(jdbc.queryForMap("SELECT user_id, provider, provider_uid FROM auth_identity"))
                .containsEntry("user_id", fresh.get("id"))
                .containsEntry("provider", "KAKAO")
                .containsEntry("provider_uid", "k-alice");
        assertThat(count("SELECT COUNT(*) FROM user_withdrawal_archive")).isZero();
        assertThat(count("SELECT COUNT(*) FROM one_time_token WHERE user_id = ? AND used_at IS NULL", oldUserId))
                .isZero();

        Map<String, Object> audit = jdbc.queryForMap("SELECT action, target_id, actor_user_id, detail FROM audit_log");
        assertThat(audit)
                .containsEntry("action", "ACCOUNT_PURGED")
                .containsEntry("target_id", oldUserId)
                .containsEntry("actor_user_id", oldUserId);
        assertThat(String.valueOf(audit.get("detail"))).contains("newUserId").doesNotContain("앨리스");

        String access = cookieValue(result, cookies.accessName());
        assertThat(jwtProvider.parse(access).userId()).isEqualTo(((Number) fresh.get("id")).longValue());
        assertThat(protectedStatus(access)).isEqualTo(200);
        assertError(startNew(token), 401, "UNAUTHENTICATED", "RESTORATION_COOKIE_INVALID");
    }

    @Test
    @DisplayName("API 133: 이후 같은 카카오 계정으로 로그인하면 새 사용자로 들어가고 복구 선택으로 가지 않는다")
    void afterStartNewLoginIsNewUser() throws Exception {
        withdrawAlice(10);
        startNew(restoreCookie());
        long freshId = jdbc.queryForObject("SELECT id FROM users WHERE id <> ?", Long.class, oldUserId);

        MvcResult exchange = fixture.exchange(fixture.loginUntilExchangeCookie(kakao, AGAIN));

        assertThat(exchange.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .contains("\"loginResult\":\"NEW_USER\"")
                .doesNotContain("RESTORATION_REQUIRED");
        assertThat(jwtProvider
                        .parse(cookieValue(exchange, cookies.accessName()))
                        .userId())
                .isEqualTo(freshId);
    }

    @Test
    @DisplayName("API 133: 카카오 닉네임이 없으면 기본 이름으로 만든다")
    void startNewWithoutNickname() throws Exception {
        withdrawAlice(10);
        jdbc.update("UPDATE one_time_token SET profile_name = NULL");
        MvcResult exchange =
                fixture.exchange(fixture.loginUntilExchangeCookie(kakao, new KakaoProfile("k-alice", null, null)));
        String token = cookieValue(exchange, cookies.restoreName());

        assertThat(startNew(token).getResponse().getStatus()).isEqualTo(200);

        assertThat(jdbc.queryForObject("SELECT name FROM users WHERE id <> ?", String.class, oldUserId))
                .isEqualTo("사용자");
    }

    @Test
    @DisplayName("API 133: 이미 복구된 계정이면 409 NOT_RESTORABLE이고 새 사용자를 만들지 않는다")
    void startNewAlreadyRestored() throws Exception {
        withdrawAlice(10);
        String token = restoreCookie();
        jdbc.update("UPDATE users SET status = 'ACTIVE'");

        assertError(startNew(token), 409, "STATE_CONFLICT", "NOT_RESTORABLE");

        assertThat(count("SELECT COUNT(*) FROM users")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM auth_identity")).isEqualTo(1);
    }

    @Test
    @DisplayName("API 132·133: 같은 쿠키로 동시에 요청해도 한 요청만 성공한다")
    void concurrent() throws Exception {
        withdrawAlice(10);
        String token = restoreCookie();
        int threads = 4;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            boolean restore = i % 2 == 0;
            results.add(pool.submit(() -> {
                start.await();
                return (restore ? restore(token) : startNew(token))
                        .getResponse()
                        .getStatus();
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
    }
}
