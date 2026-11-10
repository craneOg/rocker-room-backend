package com.crane.bandwebbackend.domain.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crane.bandwebbackend.TestcontainersConfiguration;
import com.crane.bandwebbackend.global.security.CookieFactory;
import com.crane.bandwebbackend.global.security.JwtProvider;
import com.crane.bandwebbackend.global.time.KstTime;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class MembershipLastViewedApiTest {

    private static final String OLD = "2026-01-01 00:00:00.000000";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JwtProvider jwtProvider;

    @Autowired
    CookieFactory cookies;

    long me;
    long other;
    long club;

    @BeforeEach
    void setUp() {
        clean();
        jdbc.update("INSERT INTO users (name, status) VALUES ('나', 'ACTIVE'), ('남', 'ACTIVE')");
        me = jdbc.queryForObject("SELECT id FROM users WHERE name = '나'", Long.class);
        other = jdbc.queryForObject("SELECT id FROM users WHERE name = '남'", Long.class);
        jdbc.update(
                "INSERT INTO club (name, club_type, status, is_searchable, created_by)"
                        + " VALUES ('밴드', 'BAND', 'ACTIVE', true, ?)",
                me);
        club = jdbc.queryForObject("SELECT id FROM club", Long.class);
    }

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM membership");
        jdbc.update("DELETE FROM club");
        jdbc.update("DELETE FROM users");
    }

    private long membership(long userId, String status) {
        jdbc.update(
                "INSERT INTO membership (club_id, user_id, status, join_source, invited_at, created_at, updated_at)"
                        + " VALUES (?, ?, ?, 'JOIN_CODE', CASE WHEN ? = 'INVITED' THEN UTC_TIMESTAMP(6) END, ?, ?)",
                club,
                userId,
                status,
                status,
                OLD,
                OLD);
        return jdbc.queryForObject("SELECT id FROM membership WHERE user_id = ?", Long.class, userId);
    }

    private Cookie access() {
        return new Cookie(
                cookies.accessName(),
                jwtProvider.createAccessToken(me, "sid-1", 0).getToken());
    }

    private MockHttpServletRequestBuilder touch(Object id) {
        return put("/api/v1/me/memberships/" + id + "/last-viewed").header("X-Requested-With", "XMLHttpRequest");
    }

    private LocalDateTime lastViewed(long id) {
        return jdbc.queryForObject("SELECT last_viewed_at FROM membership WHERE id = ?", LocalDateTime.class, id);
    }

    private LocalDateTime updatedAt(long id) {
        return jdbc.queryForObject("SELECT updated_at FROM membership WHERE id = ?", LocalDateTime.class, id);
    }

    @Test
    @DisplayName("API 140: 본인 소속의 last_viewed_at만 갱신하고 updated_at은 바뀌지 않으며 응답 시각은 +09:00이다")
    void touchesLastViewedOnly() throws Exception {
        long id = membership(me, "ACTIVE");
        LocalDateTime before = updatedAt(id);
        assertThat(lastViewed(id)).isNull();

        String body = mockMvc.perform(touch(id).cookie(access()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.membershipId").value(String.valueOf(id)))
                .andExpect(jsonPath("$.data.lastViewedAt").value(endsWith("+09:00")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String responded = JsonPath.read(body, "$.data.lastViewedAt");

        // 응답 시각은 DB에 저장한(마이크로초로 자른) 값과 같다
        assertThat(KstTime.toUtc(OffsetDateTime.parse(responded))).isEqualTo(lastViewed(id));
        assertThat(updatedAt(id)).isEqualTo(before);
    }

    @Test
    @DisplayName("API 140: 같은 소속을 다시 호출해도 성공하고 시각이 갱신되며 updated_at은 그대로다(멱등)")
    void idempotent() throws Exception {
        long id = membership(me, "GRADUATED");
        LocalDateTime before = updatedAt(id);

        mockMvc.perform(touch(id).cookie(access())).andExpect(status().isOk());
        LocalDateTime first = lastViewed(id);
        mockMvc.perform(touch(id).cookie(access())).andExpect(status().isOk());

        assertThat(lastViewed(id)).isAfterOrEqualTo(first);
        assertThat(updatedAt(id)).isEqualTo(before);
    }

    @Test
    @DisplayName("API 140: 타인의 소속은 404이고 값이 바뀌지 않는다(존재 비노출)")
    void othersMembership() throws Exception {
        long id = membership(other, "ACTIVE");

        mockMvc.perform(touch(id).cookie(access()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

        assertThat(lastViewed(id)).isNull();
    }

    @Test
    @DisplayName("API 140: 없는 소속은 404")
    void missingMembership() throws Exception {
        mockMvc.perform(touch(999999).cookie(access()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "INVITED", "WITHDRAWN"})
    @DisplayName("API 140: 접근할 수 없는 소속 상태(PENDING·INVITED·WITHDRAWN)는 404이고 값이 바뀌지 않는다")
    void inaccessibleMembership(String status) throws Exception {
        long id = membership(me, status);

        mockMvc.perform(touch(id).cookie(access())).andExpect(status().isNotFound());

        assertThat(lastViewed(id)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "0", "-1", "99999999999999999999"})
    @DisplayName("API 140: membershipId가 양의 10진 문자열이 아니면 400 VALIDATION_ERROR")
    void invalidId(String id) throws Exception {
        mockMvc.perform(touch(id).cookie(access()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0].field").value(endsWith("membershipId")));
    }
}
