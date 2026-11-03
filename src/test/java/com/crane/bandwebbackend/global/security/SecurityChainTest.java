package com.crane.bandwebbackend.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crane.bandwebbackend.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class SecurityChainTest {

    private static final String PROTECTED = "/api/v1/_test/protected";
    private static final String WRITE = "/api/v1/_test/write";
    private static final String ORIGIN = "http://localhost:5173";
    private static final String REQUEST_ID = "req-[a-z0-9]{12}";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JwtProvider jwtProvider;

    @Autowired
    AppProperties properties;

    @Autowired
    CookieFactory cookies;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM access_token_blacklist");
        jdbc.update("DELETE FROM users");
    }

    private long insertUser(String status, int tokenVersion, boolean superAdmin) {
        SimpleJdbcInsert insert = new SimpleJdbcInsert(jdbc)
                .withTableName("users")
                .usingColumns("name", "status", "is_super_admin", "token_version")
                .usingGeneratedKeyColumns("id");
        return insert.executeAndReturnKey(Map.of(
                        "name", "tester",
                        "status", status,
                        "is_super_admin", superAdmin,
                        "token_version", tokenVersion))
                .longValue();
    }

    private Cookie accessCookie(String token) {
        return new Cookie(cookies.accessName(), token);
    }

    private String validToken(long userId, int tokenVersion) {
        return jwtProvider.createAccessToken(userId, "sid-1", tokenVersion).token();
    }

    private void assertEnvelope(ResultActions result, String code, String message) throws Exception {
        result.andExpect(jsonPath("$.error.code").value(code))
                .andExpect(jsonPath("$.error.message").value(message))
                .andExpect(jsonPath("$.error.details").isEmpty())
                .andExpect(jsonPath("$.error.requestId").value(org.hamcrest.Matchers.matchesPattern(REQUEST_ID)))
                .andExpect(content().contentTypeCompatibleWith("application/json"));
    }

    private void assertUnauthorized(ResultActions result) throws Exception {
        result.andExpect(status().isUnauthorized());
        assertEnvelope(result, "UNAUTHENTICATED", "인증이 필요하거나 인증 정보가 유효하지 않습니다.");
    }

    @Test
    @DisplayName("0.5 유효한 토큰이면 보호 경로를 통과하고 주체가 userId다")
    void validTokenPasses() throws Exception {
        long id = insertUser("ACTIVE", 3, false);

        mockMvc.perform(get(PROTECTED).cookie(accessCookie(validToken(id, 3))))
                .andExpect(status().isOk())
                .andExpect(content().string(Long.toString(id)));
    }

    @Test
    @DisplayName("0.5 쿠키가 없으면 401 오류 봉투(requestId 포함)")
    void noCookie() throws Exception {
        assertUnauthorized(mockMvc.perform(get(PROTECTED)));
    }

    @Test
    @DisplayName("0.5 변조된 토큰은 401")
    void tampered() throws Exception {
        long id = insertUser("ACTIVE", 0, false);
        String token = validToken(id, 0);
        int idx = token.length() - 10;
        String tampered = token.substring(0, idx) + (token.charAt(idx) == 'A' ? 'B' : 'A') + token.substring(idx + 1);

        assertUnauthorized(mockMvc.perform(get(PROTECTED).cookie(accessCookie(tampered))));
    }

    @Test
    @DisplayName("0.5 만료된 토큰은 401")
    void expired() throws Exception {
        long id = insertUser("ACTIVE", 0, false);
        JwtProvider past = new JwtProvider(properties, Clock.offset(Clock.systemUTC(), Duration.ofHours(-1)));
        String token = past.createAccessToken(id, "sid-1", 0).token();

        assertUnauthorized(mockMvc.perform(get(PROTECTED).cookie(accessCookie(token))));
    }

    @Test
    @DisplayName("0.5 블랙리스트에 있는 jti는 401")
    void blacklisted() throws Exception {
        long id = insertUser("ACTIVE", 0, false);
        JwtProvider.IssuedToken issued = jwtProvider.createAccessToken(id, "sid-1", 0);
        jdbc.update(
                "INSERT INTO access_token_blacklist (jti, user_id, expires_at) VALUES (?, ?, ?)",
                issued.jti(),
                id,
                LocalDateTime.now().plusMinutes(15));

        assertUnauthorized(mockMvc.perform(get(PROTECTED).cookie(accessCookie(issued.token()))));
    }

    @Test
    @DisplayName("G-02 token_version이 다르면 401")
    void tokenVersionMismatch() throws Exception {
        long id = insertUser("ACTIVE", 5, false);

        assertUnauthorized(mockMvc.perform(get(PROTECTED).cookie(accessCookie(validToken(id, 4)))));
    }

    @Test
    @DisplayName("G-03 WITHDRAWN 사용자는 401")
    void withdrawn() throws Exception {
        long id = insertUser("WITHDRAWN", 0, false);

        assertUnauthorized(mockMvc.perform(get(PROTECTED).cookie(accessCookie(validToken(id, 0)))));
    }

    @Test
    @DisplayName("0.5 users에 없는 사용자는 401")
    void userNotFound() throws Exception {
        assertUnauthorized(mockMvc.perform(get(PROTECTED).cookie(accessCookie(validToken(999_999L, 0)))));
    }

    @Test
    @DisplayName("0.5 U14 X-Requested-With가 없거나 비어 있는 POST는 인증 여부와 무관하게 403 오류 봉투")
    void postWithoutHeader() throws Exception {
        long id = insertUser("ACTIVE", 0, false);
        Cookie valid = accessCookie(validToken(id, 0));

        assertEnvelope(
                mockMvc.perform(post(WRITE).cookie(valid)).andExpect(status().isForbidden()),
                "FORBIDDEN",
                "요청을 수행할 권한이 없습니다.");
        assertEnvelope(
                mockMvc.perform(post(WRITE)).andExpect(status().isForbidden()), "FORBIDDEN", "요청을 수행할 권한이 없습니다.");
        assertEnvelope(
                mockMvc.perform(post(WRITE).cookie(valid).header("X-Requested-With", " "))
                        .andExpect(status().isForbidden()),
                "FORBIDDEN",
                "요청을 수행할 권한이 없습니다.");
        mockMvc.perform(delete(WRITE).cookie(valid)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("0.5 U14 헤더가 있으면 값과 상관없이 통과, 비인증이면 401")
    void postWithHeader() throws Exception {
        long id = insertUser("ACTIVE", 0, false);

        mockMvc.perform(post(WRITE).cookie(accessCookie(validToken(id, 0))).header("X-Requested-With", "anything"))
                .andExpect(status().isOk());
        assertUnauthorized(mockMvc.perform(post(WRITE).header("X-Requested-With", "anything")));
    }

    @Test
    @DisplayName("0.5 GET은 X-Requested-With가 필요 없다")
    void getNeedsNoHeader() throws Exception {
        long id = insertUser("ACTIVE", 0, false);

        mockMvc.perform(get(PROTECTED).cookie(accessCookie(validToken(id, 0)))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("0.5 U16 CORS 사전 요청: 허용 출처는 통과하고 allowCredentials, 다른 출처는 거부")
    void corsPreflight() throws Exception {
        mockMvc.perform(options(WRITE)
                        .header("Origin", ORIGIN)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "x-requested-with,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGIN))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));

        mockMvc.perform(options(WRITE)
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    @DisplayName("0.5 U16 실제 요청 응답에 X-Request-Id가 노출되고 allowCredentials가 붙는다")
    void corsActualRequest() throws Exception {
        mockMvc.perform(get(PROTECTED).header("Origin", ORIGIN))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGIN))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
                .andExpect(header().string("Access-Control-Expose-Headers", "X-Request-Id"));
    }

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
        "GET,/api/v1/auth/kakao/authorize",
        "GET,/api/v1/auth/kakao/callback",
        "POST,/api/v1/auth/exchange",
        "POST,/api/v1/auth/refresh",
        "GET,/api/v1/auth/restoration",
        "POST,/api/v1/auth/restoration/restore",
        "POST,/api/v1/auth/restoration/start-new",
        "POST,/api/v1/invitations/preview"
    })
    @DisplayName("G-01 비인증 허용 경로는 토큰이 없어도, 잘못된 토큰이 있어도 보안 계층에서 막히지 않는다")
    void publicPaths(String method, String path) throws Exception {
        for (Cookie cookie : new Cookie[] {null, accessCookie("garbage.token.value")}) {
            var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(
                            HttpMethod.valueOf(method), path)
                    .header("X-Requested-With", "test");
            if (cookie != null) {
                request.cookie(cookie);
            }
            int status = mockMvc.perform(request).andReturn().getResponse().getStatus();
            assertThat(status).isNotIn(401, 403);
        }
    }

    @Test
    @DisplayName("G-01 허용 목록 밖의 /api/** 경로와 허용 경로의 다른 메서드는 인증이 필요하다")
    void otherApiRequiresAuth() throws Exception {
        assertUnauthorized(mockMvc.perform(get("/api/v1/auth/logout")));
        assertUnauthorized(mockMvc.perform(get("/api/v1/auth/exchange")));
        assertUnauthorized(mockMvc.perform(post("/api/v1/auth/kakao/authorize").header("X-Requested-With", "t")));
    }
}
