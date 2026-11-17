package com.crane.bandwebbackend.domain.club.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crane.bandwebbackend.TestcontainersConfiguration;
import com.crane.bandwebbackend.global.security.CookieFactory;
import com.crane.bandwebbackend.global.security.JwtProvider;
import com.crane.bandwebbackend.global.storage.InMemoryStorageConfig;
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

@Import({TestcontainersConfiguration.class, InMemoryStorageConfig.class})
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class ClubJoinCodeApiTest {

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
    long clubId;

    @BeforeEach
    void setUp() {
        clean();
        me = user("나", "me_user_x");
        other = user("남", "other_user_x");
        jdbc.update(
                "INSERT INTO club (name, club_type, status, is_searchable, created_by, join_code)"
                        + " VALUES ('Crane Band', 'BAND', 'ACTIVE', false, ?, 'ABCD234XYZ')",
                other);
        clubId = jdbc.queryForObject("SELECT id FROM club", Long.class);
        membership(me, "CLUB_ADMIN");
    }

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM audit_log");
        jdbc.update("DELETE FROM part");
        jdbc.update("DELETE FROM membership");
        jdbc.update("DELETE FROM club");
        jdbc.update("DELETE FROM users");
    }

    // ---- 도우미 ----

    private long user(String name, String handle) {
        jdbc.update("INSERT INTO users (name, handle, status) VALUES (?, ?, 'ACTIVE')", name, handle);
        return jdbc.queryForObject("SELECT id FROM users WHERE handle = ?", Long.class, handle);
    }

    private void membership(long user, String role) {
        jdbc.update(
                "INSERT INTO membership (club_id, user_id, status, member_type, role, join_source)"
                        + " VALUES (?, ?, 'ACTIVE', 'REGULAR', ?, 'JOIN_CODE')",
                clubId,
                user,
                role);
    }

    private Cookie access(long userId) {
        return new Cookie(
                cookies.accessName(),
                jwtProvider.createAccessToken(userId, "sid-1", 0).getToken());
    }

    private String url() {
        return "/api/v1/clubs/" + clubId + "/join-code";
    }

    private MockHttpServletRequestBuilder regenerate(long actor, String body) {
        MockHttpServletRequestBuilder b = post(url() + "/regenerate")
                .header("X-Requested-With", "XMLHttpRequest")
                .cookie(access(actor));
        return body == null ? b : b.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private MockHttpServletRequestBuilder disable(long actor) {
        return delete(url()).header("X-Requested-With", "XMLHttpRequest").cookie(access(actor));
    }

    private String code() {
        return jdbc.queryForObject("SELECT join_code FROM club WHERE id = ?", String.class, clubId);
    }

    private int auditCount(String action) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action = ?", Integer.class, action);
    }

    private MockHttpServletRequestBuilder resolve(String joinCode) {
        return post("/api/v1/clubs/join-code/resolve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"joinCode\":\"" + joinCode + "\"}")
                .header("X-Requested-With", "XMLHttpRequest")
                .cookie(access(other));
    }

    // ---- API 17 ----

    @Test
    @DisplayName("API 17: 임원은 가입 코드를 조회하고, 발급 전이면 joinCode 키는 있고 값은 null이다")
    void getJoinCode() throws Exception {
        mockMvc.perform(get(url()).cookie(access(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.joinCode").value("ABCD234XYZ"));
        jdbc.update("UPDATE club SET join_code = NULL WHERE id = ?", clubId);
        String body = mockMvc.perform(get(url()).cookie(access(me)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(body).contains("\"joinCode\":null");
    }

    @Test
    @DisplayName("G-04 API 17·18·19: 비임원 403, 비소속 404")
    void adminOnly() throws Exception {
        long member = user("부원", "member_user_x");
        membership(member, "MEMBER");
        mockMvc.perform(get(url()).cookie(access(member))).andExpect(status().isForbidden());
        mockMvc.perform(regenerate(member, null)).andExpect(status().isForbidden());
        mockMvc.perform(disable(member)).andExpect(status().isForbidden());
        long stranger = user("타인", "stranger_user_x");
        mockMvc.perform(get(url()).cookie(access(stranger))).andExpect(status().isNotFound());
        mockMvc.perform(regenerate(stranger, null)).andExpect(status().isNotFound());
        mockMvc.perform(disable(stranger)).andExpect(status().isNotFound());
        assertThat(code()).isEqualTo("ABCD234XYZ");
        assertThat(auditCount("JOIN_CODE_REGENERATED")).isZero();
    }

    // ---- API 18 ----

    @Test
    @DisplayName("API 18: 재발급하면 코드가 바뀌고 이전 코드는 API 13에서 404이며 감사 기록이 남는다")
    void regenerateReplacesCode() throws Exception {
        mockMvc.perform(resolve("ABCD234XYZ")).andExpect(status().isOk());
        mockMvc.perform(regenerate(me, "{\"reason\":\"  유출 우려  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.joinCode").value(matchesPattern("^[A-Z2-9]{10}$")));
        String fresh = code();
        assertThat(fresh).isNotEqualTo("ABCD234XYZ").matches("^[A-Z2-9]{10}$");
        mockMvc.perform(resolve("ABCD234XYZ")).andExpect(status().isNotFound());
        mockMvc.perform(resolve(fresh)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject(
                        "SELECT reason FROM audit_log WHERE action = 'JOIN_CODE_REGENERATED' AND club_id = ?"
                                + " AND actor_user_id = ? AND target_type = 'CLUB' AND target_id = ?"
                                + " AND detail IS NULL",
                        String.class,
                        clubId,
                        me,
                        clubId))
                .isEqualTo("유출 우려");
    }

    @Test
    @DisplayName("API 18: 코드가 없던 동아리도 새로 발급하고, 바디 없는 호출과 reason 없는 바디는 200이다")
    void regenerateFromNullAndNoBody() throws Exception {
        jdbc.update("UPDATE club SET join_code = NULL WHERE id = ?", clubId);
        mockMvc.perform(regenerate(me, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.joinCode").value(matchesPattern("^[A-Z2-9]{10}$")));
        assertThat(code()).isNotNull();
        mockMvc.perform(regenerate(me, "{}")).andExpect(status().isOk());
        assertThat(auditCount("JOIN_CODE_REGENERATED")).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE reason IS NULL", Integer.class))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("API 18: reason이 null·빈 문자열·501자면 400이고 코드는 그대로다")
    void regenerateInvalidReason() throws Exception {
        for (String body :
                new String[] {"{\"reason\":null}", "{\"reason\":\"   \"}", "{\"reason\":\"" + "a".repeat(501) + "\"}"
                }) {
            mockMvc.perform(regenerate(me, body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.error.details[0].field").value("reason"));
        }
        assertThat(code()).isEqualTo("ABCD234XYZ");
        assertThat(auditCount("JOIN_CODE_REGENERATED")).isZero();
    }

    // ---- API 19 ----

    @Test
    @DisplayName("API 19: 비활성화하면 204이고 코드가 null이 되며 감사 기록이 남고, 다시 호출해도 204이고 기록은 늘지 않는다")
    void disableIsIdempotent() throws Exception {
        mockMvc.perform(disable(me)).andExpect(status().isNoContent());
        assertThat(code()).isNull();
        mockMvc.perform(resolve("ABCD234XYZ")).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM audit_log WHERE action = 'JOIN_CODE_DISABLED' AND club_id = ?"
                                + " AND actor_user_id = ? AND target_type = 'CLUB' AND target_id = ?",
                        Integer.class,
                        clubId,
                        me,
                        clubId))
                .isEqualTo(1);
        mockMvc.perform(disable(me)).andExpect(status().isNoContent());
        assertThat(auditCount("JOIN_CODE_DISABLED")).isEqualTo(1);
    }

    // ---- 비활성 동아리 ----

    @Test
    @DisplayName("G-08 API 18·19: 비활성 동아리는 409 CLUB_INACTIVE이고 조회(017)는 허용한다")
    void inactiveClub() throws Exception {
        jdbc.update("UPDATE club SET status = 'INACTIVE' WHERE id = ?", clubId);
        mockMvc.perform(regenerate(me, null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("STATE_CONFLICT"))
                .andExpect(jsonPath("$.error.details[0].reason").value("CLUB_INACTIVE"));
        mockMvc.perform(disable(me))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.details[0].reason").value("CLUB_INACTIVE"));
        assertThat(code()).isEqualTo("ABCD234XYZ");
        mockMvc.perform(get(url()).cookie(access(me))).andExpect(status().isOk());
    }
}
