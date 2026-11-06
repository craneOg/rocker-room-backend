package com.crane.bandwebbackend.domain.user.controller;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crane.bandwebbackend.TestcontainersConfiguration;
import com.crane.bandwebbackend.global.security.CookieFactory;
import com.crane.bandwebbackend.global.security.JwtProvider;
import jakarta.servlet.http.Cookie;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class SuccessionNoticeApiTest {

    private static final String BASE = "/api/v1/me/succession-notices";

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
        me = user("나", "me_user");
        other = user("남", "other_user");
        jdbc.update("INSERT INTO club (name, created_by) VALUES ('크레인', ?)", me);
        club = jdbc.queryForObject("SELECT id FROM club", Long.class);
    }

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM succession_notice");
        jdbc.update("DELETE FROM team");
        jdbc.update("DELETE FROM performance");
        jdbc.update("DELETE FROM club");
        jdbc.update("DELETE FROM access_token_blacklist");
        jdbc.update("DELETE FROM users");
    }

    // ---- 도우미 ----

    private long user(String name, String handle) {
        jdbc.update("INSERT INTO users (name, handle, status) VALUES (?, ?, 'ACTIVE')", name, handle);
        return jdbc.queryForObject("SELECT id FROM users WHERE handle = ?", Long.class, handle);
    }

    private long adminNotice(long userId) {
        jdbc.update(
                "INSERT INTO succession_notice (user_id, club_id, notice_type) VALUES (?, ?, 'ADMIN')", userId, club);
        return jdbc.queryForObject("SELECT MAX(id) FROM succession_notice", Long.class);
    }

    private long teamLeaderNotice(long userId) {
        jdbc.update(
                "INSERT INTO performance (club_id, title, preset, status, created_by_user_id)"
                        + " VALUES (?, '2026 가을 공연', 'CUSTOM', 'ACTIVE', ?)",
                club,
                me);
        long perf = jdbc.queryForObject("SELECT MAX(id) FROM performance", Long.class);
        jdbc.update(
                "INSERT INTO team (performance_id, name, source, created_by_user_id) VALUES (?, '1팀', 'ADMIN', ?)",
                perf,
                me);
        long team = jdbc.queryForObject("SELECT MAX(id) FROM team", Long.class);
        jdbc.update(
                "INSERT INTO succession_notice (user_id, club_id, notice_type, performance_id, team_id)"
                        + " VALUES (?, ?, 'TEAM_LEADER', ?, ?)",
                userId,
                club,
                perf,
                team);
        return jdbc.queryForObject("SELECT MAX(id) FROM succession_notice", Long.class);
    }

    private Cookie access(long userId) {
        return new Cookie(
                cookies.accessName(),
                jwtProvider.createAccessToken(userId, "sid-1", 0).getToken());
    }

    private MockHttpServletRequestBuilder confirm(long userId, String noticeId) {
        return post(BASE + "/" + noticeId + "/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .header("X-Requested-With", "XMLHttpRequest")
                .cookie(access(userId));
    }

    // ---- API 135 ----

    @Test
    @DisplayName("API 135: 본인의 미확인 안내만 오래된 순으로 돌려주고 ADMIN은 팀·공연이 null, TEAM_LEADER는 이름을 담는다")
    void list() throws Exception {
        long admin = adminNotice(me);
        long leader = teamLeaderNotice(me);
        adminNotice(other);
        long confirmed = adminNotice(me);
        jdbc.update("UPDATE succession_notice SET confirmed_at = UTC_TIMESTAMP(6) WHERE id = ?", confirmed);

        mockMvc.perform(get(BASE).cookie(access(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.items[0].id").value(String.valueOf(admin)))
                .andExpect(jsonPath("$.data.items[0].type").value("ADMIN"))
                .andExpect(jsonPath("$.data.items[0].clubId").value(String.valueOf(club)))
                .andExpect(jsonPath("$.data.items[0].clubName").value("크레인"))
                .andExpect(jsonPath("$.data.items[0].teamId").isEmpty())
                .andExpect(jsonPath("$.data.items[0].teamName").isEmpty())
                .andExpect(jsonPath("$.data.items[0].performanceId").isEmpty())
                .andExpect(jsonPath("$.data.items[0].performanceName").isEmpty())
                .andExpect(jsonPath("$.data.items[0].createdAt").value(endsWith("+09:00")))
                .andExpect(jsonPath("$.data.items[0].confirmedAt").doesNotExist())
                .andExpect(jsonPath("$.data.items[1].id").value(String.valueOf(leader)))
                .andExpect(jsonPath("$.data.items[1].type").value("TEAM_LEADER"))
                .andExpect(jsonPath("$.data.items[1].teamName").value("1팀"))
                .andExpect(jsonPath("$.data.items[1].performanceName").value("2026 가을 공연"))
                .andExpect(jsonPath("$.meta.requestId").value(matchesPattern("req-[a-z0-9]{12}")));
    }

    @Test
    @DisplayName("API 135: 안내가 없으면 빈 items")
    void listEmpty() throws Exception {
        mockMvc.perform(get(BASE).cookie(access(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(0)));
    }

    @Test
    @DisplayName("API 135·136: 인증이 없으면 401")
    void unauthenticated() throws Exception {
        long id = adminNotice(me);
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(BASE + "/" + id + "/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized());
    }

    // ---- API 136 ----

    @Test
    @DisplayName("API 136: 확인하면 confirmedAt이 기록되고 이후 135에서 사라진다")
    void confirmNotice() throws Exception {
        long id = adminNotice(me);

        mockMvc.perform(confirm(me, String.valueOf(id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(String.valueOf(id)))
                .andExpect(jsonPath("$.data.type").value("ADMIN"))
                .andExpect(jsonPath("$.data.confirmedAt").value(endsWith("+09:00")));

        mockMvc.perform(get(BASE).cookie(access(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(0)));
    }

    @Test
    @DisplayName("API 136: 이미 확인한 안내를 다시 확인해도 200이고 최초 확인 시각을 유지한다")
    void confirmIsIdempotent() throws Exception {
        long id = adminNotice(me);
        String first = mockMvc.perform(confirm(me, String.valueOf(id)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String firstAt = com.jayway.jsonpath.JsonPath.read(first, "$.data.confirmedAt");

        mockMvc.perform(confirm(me, String.valueOf(id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.confirmedAt").value(firstAt));
    }

    @Test
    @DisplayName("API 136: 타인의 안내·없는 안내·숫자가 아닌 ID는 모두 404이고 타인의 안내는 확인되지 않는다")
    void confirmNotFound() throws Exception {
        long others = adminNotice(other);

        mockMvc.perform(confirm(me, String.valueOf(others)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        mockMvc.perform(confirm(me, "999999")).andExpect(status().isNotFound());
        mockMvc.perform(confirm(me, "abc")).andExpect(status().isNotFound());

        mockMvc.perform(get(BASE).cookie(access(other))).andExpect(jsonPath("$.data.items", hasSize(1)));
    }

    @Test
    @DisplayName("API 136: X-Requested-With 헤더가 없으면 403")
    void confirmWithoutRequestedWith() throws Exception {
        long id = adminNotice(me);
        mockMvc.perform(post(BASE + "/" + id + "/confirm").cookie(access(me))).andExpect(status().isForbidden());
    }
}
