package com.crane.bandwebbackend.domain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
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
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class AuthExchangeFlowTest {

    private static final KakaoProfile PROFILE = new KakaoProfile("k-1", "홍길동", "https://img.example/1.png");
    private static final String REQUEST_ID = "req-[a-z0-9]{12}";

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

    @BeforeEach
    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM refresh_token");
        jdbc.update("DELETE FROM one_time_token");
        jdbc.update("DELETE FROM auth_identity");
        jdbc.update("DELETE FROM users");
    }

    // ---- 도우미: authorize → callback까지 실제로 거쳐 교환 쿠키 원문을 얻는다 ----

    private String login(String returnTo) throws Exception {
        when(kakao.fetchProfile(anyString())).thenReturn(PROFILE);
        var authorize = get("/api/v1/auth/kakao/authorize");
        if (returnTo != null) {
            authorize.param("returnTo", returnTo);
        }
        String state = cookieValue(mockMvc.perform(authorize).andReturn(), cookies.stateName());
        MvcResult callback = mockMvc.perform(get("/api/v1/auth/kakao/callback")
                        .param("code", "c")
                        .param("state", state)
                        .cookie(new Cookie(cookies.stateName(), state)))
                .andReturn();
        return cookieValue(callback, cookies.exchangeName());
    }

    private MockHttpServletRequestBuilder exchangeRequest(String exchangeToken) {
        var request = post("/api/v1/auth/exchange")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .header("X-Requested-With", "XMLHttpRequest");
        if (exchangeToken != null) {
            request.cookie(new Cookie(cookies.exchangeName(), exchangeToken));
        }
        return request;
    }

    private MvcResult exchange(String exchangeToken) throws Exception {
        return mockMvc.perform(exchangeRequest(exchangeToken)).andReturn();
    }

    private static String setCookie(MvcResult result, String name) {
        List<String> all = result.getResponse().getHeaders("Set-Cookie");
        return all.stream()
                .filter(c -> c.startsWith(name + "="))
                .findFirst()
                .orElseThrow(() -> new AssertionError(name + " 쿠키가 없습니다: " + all));
    }

    private static boolean hasCookie(MvcResult result, String name) {
        return result.getResponse().getHeaders("Set-Cookie").stream().anyMatch(c -> c.startsWith(name + "="));
    }

    private static String cookieValue(MvcResult result, String name) {
        return setCookie(result, name).split(";", 2)[0].substring(name.length() + 1);
    }

    private static String sha256Hex(String raw) throws Exception {
        return HexFormat.of()
                .withUpperCase()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private void assertInvalidExchange(MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body)
                .contains("\"code\":\"UNAUTHENTICATED\"")
                .contains("\"reason\":\"LOGIN_EXCHANGE_INVALID\"")
                .doesNotContain("\"field\"");
        assertThat(hasCookie(result, cookies.accessName())).isFalse();
        assertThat(hasCookie(result, cookies.refreshName())).isFalse();
        assertThat(count("refresh_token")).isZero();
    }

    // ---- 성공 ----

    @Test
    @DisplayName("API 3: 새 사용자는 NEW_USER·onboardingRequired=true로 Access·Refresh를 쿠키로만 받는다")
    void newUser() throws Exception {
        String exchangeToken = login("/join/K7M2Q9XA4B");

        ResultActions actions = mockMvc.perform(exchangeRequest(exchangeToken));
        actions.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loginResult").value("NEW_USER"))
                .andExpect(jsonPath("$.data.onboardingRequired").value(true))
                .andExpect(jsonPath("$.data.returnTo").value("/join/K7M2Q9XA4B"))
                .andExpect(jsonPath("$.data.user.name").value("홍길동"))
                .andExpect(jsonPath("$.data.user.handle").doesNotExist())
                .andExpect(jsonPath("$.data.user.profileImageUrl").value("https://img.example/1.png"))
                .andExpect(jsonPath("$.data.user.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.user.isSuperAdmin").value(false))
                .andExpect(jsonPath("$.data.user.id").isString())
                .andExpect(jsonPath("$.data.user.createdAt").value(org.hamcrest.Matchers.endsWith("+09:00")))
                .andExpect(jsonPath("$.meta.requestId").value(org.hamcrest.Matchers.matchesPattern(REQUEST_ID)));
        MvcResult result = actions.andReturn();
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);

        // 토큰은 본문에 없다
        String access = cookieValue(result, cookies.accessName());
        String refresh = cookieValue(result, cookies.refreshName());
        assertThat(body).doesNotContain(access).doesNotContain(refresh);

        assertThat(setCookie(result, cookies.accessName()))
                .contains("HttpOnly", "SameSite=Lax", "Max-Age=900", "Path=/")
                .doesNotContain("Domain");
        assertThat(setCookie(result, cookies.refreshName()))
                .contains("HttpOnly", "SameSite=Lax", "Max-Age=2592000", "Path=/api/v1/auth/refresh")
                .doesNotContain("Domain");
        assertThat(setCookie(result, cookies.exchangeName())).contains("Max-Age=0");
        assertThat(hasCookie(result, cookies.restoreName())).isFalse();
        assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");

        Map<String, Object> user = jdbc.queryForMap("SELECT id, token_version, last_active_at FROM users");
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT HEX(token_hash) h, family_id, user_id, revoked_at, revoke_reason, replaced_by_id, created_ip,"
                        + " TIMESTAMPDIFF(SECOND, created_at, expires_at) ttl,"
                        + " TIMESTAMPDIFF(SECOND, created_at, family_expires_at) fttl FROM refresh_token");
        assertThat(row.get("h")).isEqualTo(sha256Hex(refresh));
        assertThat(row.get("user_id")).isEqualTo(user.get("id"));
        assertThat(row.get("revoked_at")).isNull();
        assertThat(row.get("revoke_reason")).isNull();
        assertThat(row.get("replaced_by_id")).isNull();
        assertThat(row.get("created_ip")).isNotNull();
        assertThat(((Number) row.get("ttl")).longValue()).isBetween(30L * 24 * 3600 - 1, 30L * 24 * 3600);
        assertThat(((Number) row.get("fttl")).longValue()).isBetween(90L * 24 * 3600 - 1, 90L * 24 * 3600);
        assertThat(user.get("last_active_at")).isNotNull();

        JwtProvider.AccessTokenClaims claims = jwtProvider.parse(access);
        assertThat(claims.userId()).isEqualTo(((Number) user.get("id")).longValue());
        assertThat(claims.sid()).isEqualTo(row.get("family_id"));
        assertThat(claims.tokenVersion()).isEqualTo(((Number) user.get("token_version")).intValue());
    }

    @Test
    @DisplayName("API 3: handle이 있는 사용자는 EXISTING_USER·onboardingRequired=false, returnTo가 없으면 null")
    void existingUser() throws Exception {
        login(null);
        jdbc.update("UPDATE users SET handle = 'gyuchae', token_version = 3");

        ResultActions actions = mockMvc.perform(exchangeRequest(login(null)));
        actions.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loginResult").value("EXISTING_USER"))
                .andExpect(jsonPath("$.data.onboardingRequired").value(false))
                .andExpect(jsonPath("$.data.user.handle").value("gyuchae"))
                .andExpect(jsonPath("$.data.returnTo").doesNotExist());
        MvcResult result = actions.andReturn();
        JwtProvider.AccessTokenClaims claims = jwtProvider.parse(cookieValue(result, cookies.accessName()));
        assertThat(claims.tokenVersion()).isEqualTo(3);
    }

    @Test
    @DisplayName("API 3: 로그인마다 새 로그인 묶음(family)이 생기고, last_active_at은 하루 1회만 갱신한다")
    void separateFamiliesAndDailyActivity() throws Exception {
        exchange(login(null));
        Object firstActive = jdbc.queryForObject("SELECT last_active_at FROM users", Object.class);
        exchange(login(null));

        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT family_id) FROM refresh_token", Integer.class))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT last_active_at FROM users", Object.class))
                .isEqualTo(firstActive);
    }

    // ---- 복구 선택 ----

    @Test
    @DisplayName("API 3: 탈퇴 계정은 RESTORATION_REQUIRED, 복구 선택 쿠키만 받고 Access·Refresh·행은 없다")
    void withdrawnUser() throws Exception {
        login(null);
        jdbc.update("UPDATE users SET status = 'WITHDRAWN', withdrawn_at = UTC_TIMESTAMP(6)");

        ResultActions actions = mockMvc.perform(exchangeRequest(login("/join/ABC")));
        actions.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loginResult").value("RESTORATION_REQUIRED"))
                .andExpect(jsonPath("$.data.user").isEmpty())
                .andExpect(jsonPath("$.data.onboardingRequired").value(false))
                .andExpect(jsonPath("$.data.returnTo").value("/join/ABC"));
        MvcResult result = actions.andReturn();
        assertThat(hasCookie(result, cookies.accessName())).isFalse();
        assertThat(hasCookie(result, cookies.refreshName())).isFalse();
        assertThat(setCookie(result, cookies.restoreName()))
                .contains("HttpOnly", "SameSite=Lax", "Max-Age=600", "Path=/");
        assertThat(count("refresh_token")).isZero();
        String restore = cookieValue(result, cookies.restoreName());
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM one_time_token WHERE purpose = 'ACCOUNT_RESTORATION'"
                                + " AND HEX(token_hash) = ? AND used_at IS NULL AND user_id = (SELECT id FROM users)",
                        Integer.class,
                        sha256Hex(restore)))
                .isEqualTo(1);
    }

    // ---- 실패 ----

    @Test
    @DisplayName("API 3: 같은 교환 쿠키를 두 번 쓰면 두 번째는 401 LOGIN_EXCHANGE_INVALID")
    void reuse() throws Exception {
        String token = login(null);
        assertThat(exchange(token).getResponse().getStatus()).isEqualTo(200);
        int refreshRows = count("refresh_token");

        assertThat(exchange(token).getResponse().getStatus()).isEqualTo(401);
        assertThat(count("refresh_token")).isEqualTo(refreshRows);
    }

    @Test
    @DisplayName("API 3: 쿠키가 없거나 발급한 적 없는 값이거나 만료되었으면 401 LOGIN_EXCHANGE_INVALID")
    void invalidTokens() throws Exception {
        assertInvalidExchange(exchange(null));
        assertInvalidExchange(exchange("never-issued"));

        String token = login(null);
        jdbc.update("UPDATE one_time_token SET expires_at = '2000-01-01 00:00:00'");
        assertInvalidExchange(exchange(token));
    }

    @Test
    @DisplayName("API 3: 다른 용도의 토큰(OAuth state)은 교환 쿠키로 쓸 수 없다")
    void wrongPurpose() throws Exception {
        MvcResult authorize =
                mockMvc.perform(get("/api/v1/auth/kakao/authorize")).andReturn();
        String state = cookieValue(authorize, cookies.stateName());

        assertInvalidExchange(exchange(state));
        assertThat(jdbc.queryForObject("SELECT used_at IS NULL FROM one_time_token", Boolean.class))
                .isTrue();
    }

    @Test
    @DisplayName("API 3: X-Requested-With가 없으면 403이고 교환 쿠키는 소비되지 않는다")
    void missingRequestedWith() throws Exception {
        String token = login(null);

        mockMvc.perform(post("/api/v1/auth/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .cookie(new Cookie(cookies.exchangeName(), token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        assertThat(exchange(token).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("API 3: JSON이 아닌 Content-Type은 400 VALIDATION_ERROR(UNSUPPORTED_MEDIA_TYPE)")
    void unsupportedMediaType() throws Exception {
        String token = login(null);

        mockMvc.perform(post("/api/v1/auth/exchange")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("{}")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie(cookies.exchangeName(), token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0].reason").value("UNSUPPORTED_MEDIA_TYPE"));
        assertThat(count("refresh_token")).isZero();
    }

    @Test
    @DisplayName("API 3: 인증 쿠키 없이 호출할 수 있다(G-01)")
    void noAuthRequired() throws Exception {
        assertThat(exchange(null).getResponse().getStatus()).isEqualTo(401);
        assertThat(exchange(null).getResponse().getContentAsString()).contains("LOGIN_EXCHANGE_INVALID");
    }

    @Test
    @DisplayName("API 3: 같은 교환 쿠키로 동시에 요청해도 정확히 한 요청만 로그인에 성공하고 Refresh 행은 1개")
    void concurrent() throws Exception {
        String token = login(null);
        int threads = 6;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return exchange(token).getResponse().getStatus();
            }));
        }
        start.countDown();
        int ok = 0;
        int unauthorized = 0;
        for (Future<Integer> result : results) {
            int code = result.get();
            if (code == 200) {
                ok++;
            } else if (code == 401) {
                unauthorized++;
            }
        }
        pool.shutdown();

        assertThat(ok).isEqualTo(1);
        assertThat(unauthorized).isEqualTo(threads - 1);
        assertThat(count("refresh_token")).isEqualTo(1);
    }
}
