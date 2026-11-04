package com.crane.bandwebbackend.domain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crane.bandwebbackend.TestcontainersConfiguration;
import com.crane.bandwebbackend.domain.auth.client.KakaoOAuthClient;
import com.crane.bandwebbackend.domain.auth.client.KakaoOAuthException;
import com.crane.bandwebbackend.domain.auth.dto.KakaoProfile;
import com.crane.bandwebbackend.global.security.CookieFactory;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;

@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class KakaoLoginFlowTest {

    private static final String COMPLETE = "http://localhost:5173/auth/complete";
    private static final KakaoProfile PROFILE = new KakaoProfile("k-1", "홍길동", "https://img.example/1.png");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    CookieFactory cookies;

    @MockitoBean
    KakaoOAuthClient kakao;

    @BeforeEach
    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM one_time_token");
        jdbc.update("DELETE FROM auth_identity");
        jdbc.update("DELETE FROM users");
    }

    // ---- 도우미 ----

    private MvcResult authorize(String returnTo) throws Exception {
        var request = get("/api/v1/auth/kakao/authorize");
        if (returnTo != null) {
            request.param("returnTo", returnTo);
        }
        return mockMvc.perform(request).andReturn();
    }

    private static String cookieValue(MvcResult result, String name) {
        return setCookie(result, name).split(";", 2)[0].substring(name.length() + 1);
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

    private static String sha256Hex(String raw) throws Exception {
        return HexFormat.of()
                .withUpperCase()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
    }

    private String startLogin(String returnTo) throws Exception {
        return cookieValue(authorize(returnTo), cookies.stateName());
    }

    private MvcResult callback(String state, String stateCookie, Map<String, String> params) throws Exception {
        var request = get("/api/v1/auth/kakao/callback");
        params.forEach(request::param);
        if (state != null) {
            request.param("state", state);
        }
        if (stateCookie != null) {
            request.cookie(new Cookie(cookies.stateName(), stateCookie));
        }
        return mockMvc.perform(request).andReturn();
    }

    // 발급부터 만료까지의 초. 두 시각이 같은 경로로 저장되므로 JVM 시간대와 무관하다(DB 함수나 now() 비교는 쓰지 않는다)
    private long ttlSeconds(String raw) throws Exception {
        return jdbc.queryForObject(
                "SELECT TIMESTAMPDIFF(SECOND, created_at, expires_at) FROM one_time_token WHERE HEX(token_hash) = ?",
                Long.class,
                sha256Hex(raw));
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private void assertRedirectedWithError(MvcResult result, String error) {
        assertThat(result.getResponse().getStatus()).isEqualTo(302);
        assertThat(result.getResponse().getHeader("Location")).isEqualTo(COMPLETE + "?error=" + error);
        assertThat(hasCookie(result, cookies.exchangeName())).isFalse();
        assertThat(setCookie(result, cookies.stateName())).contains("Max-Age=0");
    }

    // ---- API 1 ----

    @Test
    @DisplayName("API 1: 카카오 인증 URL로 302, state 쿠키 발급, DB에는 state 해시와 returnTo만 저장한다")
    void authorizeRedirects() throws Exception {
        MvcResult result = authorize("/join/K7M2Q9XA4B");

        assertThat(result.getResponse().getStatus()).isEqualTo(302);
        URI location = URI.create(result.getResponse().getHeader("Location"));
        assertThat(location.getScheme() + "://" + location.getHost() + location.getPath())
                .isEqualTo("https://kauth.kakao.com/oauth/authorize");
        Map<String, String> query = UriComponentsBuilder.fromUri(location)
                .build(true)
                .getQueryParams()
                .toSingleValueMap();
        assertThat(query)
                .containsEntry("client_id", "test-client-id")
                .containsEntry("response_type", "code")
                .containsEntry("scope", "profile_nickname,profile_image")
                .containsEntry("redirect_uri", "http://localhost:8080/api/v1/auth/kakao/callback");

        String stateCookie = setCookie(result, cookies.stateName());
        String state = cookieValue(result, cookies.stateName());
        assertThat(query.get("state")).isEqualTo(state);
        assertThat(stateCookie)
                .contains("HttpOnly", "SameSite=Lax", "Max-Age=600", "Path=/")
                .doesNotContain("Domain");

        Map<String, Object> row =
                jdbc.queryForMap("SELECT HEX(token_hash) h, purpose, return_to, user_id, used_at FROM one_time_token");
        assertThat(row.get("h")).isEqualTo(sha256Hex(state));
        assertThat(row.get("purpose")).isEqualTo("OAUTH_STATE");
        assertThat(row.get("return_to")).isEqualTo("/join/K7M2Q9XA4B");
        assertThat(row.get("user_id")).isNull();
        assertThat(row.get("used_at")).isNull();
        assertThat(ttlSeconds(state)).isBetween(599L, 600L);
        assertThat(result.getResponse().getHeader("Referrer-Policy")).isEqualTo("no-referrer");
        assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(result.getResponse().getContentAsByteArray()).isEmpty();
    }

    @Test
    @DisplayName("API 1: returnTo가 없으면 null로 저장하고, 호출마다 다른 state를 만든다")
    void authorizeWithoutReturnTo() throws Exception {
        String first = startLogin(null);
        String second = startLogin(null);

        assertThat(first).isNotEqualTo(second).hasSize(43);
        assertThat(jdbc.queryForList("SELECT return_to FROM one_time_token WHERE return_to IS NOT NULL"))
                .isEmpty();
    }

    @Test
    @DisplayName("API 1: 허용되지 않는 returnTo는 400 VALIDATION_ERROR이고 행을 만들지 않는다")
    void authorizeRejectsBadReturnTo() throws Exception {
        for (String bad :
                List.of("//evil.com", "https://evil.com", "/a\\b", "x".repeat(10), "/" + "a".repeat(200), "")) {
            mockMvc.perform(get("/api/v1/auth/kakao/authorize").param("returnTo", bad))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.error.details[0].field").value("returnTo"))
                    .andExpect(jsonPath("$.error.details[0].reason").value("INVALID_VALUE"))
                    .andExpect(header().doesNotExist("Set-Cookie"));
        }
        assertThat(count("one_time_token")).isZero();
    }

    // ---- API 2 ----

    @Test
    @DisplayName("API 2: 새 사용자는 users·auth_identity를 만들고 교환 쿠키와 함께 쿼리 없는 완료 페이지로 302")
    void callbackNewUser() throws Exception {
        when(kakao.fetchProfile("code-1")).thenReturn(PROFILE);
        String state = startLogin("/join/ABC");

        MvcResult result = callback(state, state, Map.of("code", "code-1"));

        assertThat(result.getResponse().getStatus()).isEqualTo(302);
        assertThat(result.getResponse().getHeader("Location")).isEqualTo(COMPLETE);
        assertThat(result.getResponse().getHeader("Referrer-Policy")).isEqualTo("no-referrer");
        assertThat(setCookie(result, cookies.stateName())).contains("Max-Age=0");
        String exchange = setCookie(result, cookies.exchangeName());
        assertThat(exchange)
                .contains("HttpOnly", "SameSite=Lax", "Max-Age=60", "Path=/")
                .doesNotContain("Domain");
        assertThat(hasCookie(result, cookies.accessName())).isFalse();
        assertThat(hasCookie(result, cookies.refreshName())).isFalse();
        String exchangeToken = cookieValue(result, cookies.exchangeName());
        assertThat(result.getResponse().getHeader("Location")).doesNotContain(exchangeToken);

        Map<String, Object> user = jdbc.queryForMap("SELECT id, name, handle, profile_image_url, status FROM users");
        assertThat(user)
                .containsEntry("name", "홍길동")
                .containsEntry("profile_image_url", "https://img.example/1.png")
                .containsEntry("status", "ACTIVE");
        assertThat(user.get("handle")).isNull();
        Map<String, Object> identity = jdbc.queryForMap("SELECT user_id, provider, provider_uid FROM auth_identity");
        assertThat(identity)
                .containsEntry("provider", "KAKAO")
                .containsEntry("provider_uid", "k-1")
                .containsEntry("user_id", user.get("id"));

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT HEX(token_hash) h, user_id, return_to, used_at FROM one_time_token WHERE purpose = 'LOGIN_EXCHANGE'");
        assertThat(row.get("h")).isEqualTo(sha256Hex(exchangeToken));
        assertThat(row.get("user_id")).isEqualTo(user.get("id"));
        assertThat(row.get("return_to")).isEqualTo("/join/ABC");
        assertThat(row.get("used_at")).isNull();
        assertThat(ttlSeconds(exchangeToken)).isBetween(59L, 60L);
        assertThat(jdbc.queryForObject(
                        "SELECT used_at IS NOT NULL FROM one_time_token WHERE purpose = 'OAUTH_STATE'", Boolean.class))
                .isTrue();
    }

    @Test
    @DisplayName("API 2: 기존 사용자는 새로 만들지 않고 이름·사진도 카카오 값으로 덮어쓰지 않는다")
    void callbackExistingUser() throws Exception {
        when(kakao.fetchProfile(anyString())).thenReturn(PROFILE);
        String first = startLogin(null);
        callback(first, first, Map.of("code", "c"));
        jdbc.update("UPDATE users SET name = '바꾼이름', profile_image_url = NULL");

        String second = startLogin(null);
        MvcResult result = callback(second, second, Map.of("code", "c"));

        assertThat(result.getResponse().getHeader("Location")).isEqualTo(COMPLETE);
        assertThat(count("users")).isEqualTo(1);
        assertThat(count("auth_identity")).isEqualTo(1);
        Map<String, Object> user = jdbc.queryForMap("SELECT name, profile_image_url FROM users");
        assertThat(user).containsEntry("name", "바꾼이름");
        assertThat(user.get("profile_image_url")).isNull();
        assertThat(count("one_time_token")).isEqualTo(4);
    }

    @Test
    @DisplayName("API 2: 탈퇴한 계정도 거부하지 않고 같은 방식으로 교환 쿠키를 준다(분기는 API 3)")
    void callbackWithdrawnUser() throws Exception {
        when(kakao.fetchProfile(anyString())).thenReturn(PROFILE);
        String first = startLogin(null);
        callback(first, first, Map.of("code", "c"));
        jdbc.update("UPDATE users SET status = 'WITHDRAWN', withdrawn_at = UTC_TIMESTAMP(6)");

        String second = startLogin(null);
        MvcResult result = callback(second, second, Map.of("code", "c"));

        assertThat(result.getResponse().getHeader("Location")).isEqualTo(COMPLETE);
        assertThat(hasCookie(result, cookies.exchangeName())).isTrue();
        assertThat(count("users")).isEqualTo(1);
    }

    @Test
    @DisplayName("API 2: 쿼리 state와 쿠키 state가 다르면 OAUTH_FAILED이고 카카오를 부르지 않는다")
    void stateMismatch() throws Exception {
        String state = startLogin(null);
        String other = startLogin(null);

        MvcResult result = callback(state, other, Map.of("code", "c"));

        assertRedirectedWithError(result, "OAUTH_FAILED");
        verify(kakao, never()).fetchProfile(anyString());
        assertThat(count("users")).isZero();
    }

    @Test
    @DisplayName("API 2: state 쿠키가 없거나 state 쿼리가 없으면 OAUTH_FAILED(400이 아님)")
    void stateMissing() throws Exception {
        String state = startLogin(null);

        assertRedirectedWithError(callback(state, null, Map.of("code", "c")), "OAUTH_FAILED");
        assertRedirectedWithError(callback(null, state, Map.of("code", "c")), "OAUTH_FAILED");
        verify(kakao, never()).fetchProfile(anyString());
    }

    @Test
    @DisplayName("API 2: 같은 state를 두 번 쓰면 두 번째는 OAUTH_FAILED")
    void stateReuse() throws Exception {
        when(kakao.fetchProfile(anyString())).thenReturn(PROFILE);
        String state = startLogin(null);

        assertThat(callback(state, state, Map.of("code", "c")).getResponse().getHeader("Location"))
                .isEqualTo(COMPLETE);
        assertRedirectedWithError(callback(state, state, Map.of("code", "c")), "OAUTH_FAILED");
        assertThat(count("one_time_token")).isEqualTo(2);
    }

    @Test
    @DisplayName("API 2: 만료된 state는 OAUTH_FAILED")
    void stateExpired() throws Exception {
        String state = startLogin(null);
        jdbc.update("UPDATE one_time_token SET expires_at = '2000-01-01 00:00:00'");

        assertRedirectedWithError(callback(state, state, Map.of("code", "c")), "OAUTH_FAILED");
        verify(kakao, never()).fetchProfile(anyString());
    }

    @Test
    @DisplayName("API 2: 발급한 적 없는 state는 OAUTH_FAILED")
    void unknownState() throws Exception {
        assertRedirectedWithError(
                callback("not-a-real-token", "not-a-real-token", Map.of("code", "c")), "OAUTH_FAILED");
        verify(kakao, never()).fetchProfile(anyString());
    }

    @Test
    @DisplayName("API 2: 카카오 동의 취소(access_denied)는 CONSENT_CANCELED")
    void consentCanceled() throws Exception {
        String state = startLogin(null);

        MvcResult result =
                callback(state, state, Map.of("error", "access_denied", "error_description", "user canceled"));

        assertRedirectedWithError(result, "CONSENT_CANCELED");
        verify(kakao, never()).fetchProfile(anyString());
        assertThat(count("users")).isZero();
    }

    @Test
    @DisplayName("API 2: 그 밖의 카카오 오류는 OAUTH_FAILED")
    void otherKakaoError() throws Exception {
        String state = startLogin(null);

        assertRedirectedWithError(callback(state, state, Map.of("error", "server_error")), "OAUTH_FAILED");
    }

    @Test
    @DisplayName("API 2: 유효한 state인데 code가 없으면 OAUTH_FAILED")
    void codeMissing() throws Exception {
        String state = startLogin(null);

        assertRedirectedWithError(callback(state, state, Map.of()), "OAUTH_FAILED");
    }

    @Test
    @DisplayName("API 2: 카카오 호출이 실패하면 OAUTH_FAILED이고 사용자를 만들지 않는다")
    void kakaoFailure() throws Exception {
        when(kakao.fetchProfile(anyString())).thenThrow(new KakaoOAuthException("카카오 호출 실패: HttpClientErrorException"));
        String state = startLogin(null);

        MvcResult result = callback(state, state, Map.of("code", "bad"));

        assertRedirectedWithError(result, "OAUTH_FAILED");
        assertThat(count("users")).isZero();
        assertThat(count("auth_identity")).isZero();
    }

    @Test
    @DisplayName("API 2: code·state·error가 모두 없으면 카카오를 거치지 않은 요청이라 400 VALIDATION_ERROR")
    void noParams() throws Exception {
        mockMvc.perform(get("/api/v1/auth/kakao/callback"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0].field").value("request"));
    }

    @Test
    @DisplayName("API 2: 인증 쿠키 없이 호출할 수 있다(G-01)")
    void noAuthRequired() throws Exception {
        mockMvc.perform(get("/api/v1/auth/kakao/callback").param("error", "access_denied"))
                .andExpect(status().isFound());
    }
}
