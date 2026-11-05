package com.crane.bandwebbackend.domain.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.util.Map;
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
class AdminApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JwtProvider jwtProvider;

    @Autowired
    CookieFactory cookies;

    long admin;
    long normal;

    @BeforeEach
    void setUp() {
        clean();
        admin = user("운영자", "admin_user", true, true, null);
        normal = user("일반", "normal_user", false, true, null);
    }

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM audit_log");
        jdbc.update("DELETE FROM access_token_blacklist");
        jdbc.update("DELETE FROM refresh_token");
        jdbc.update("DELETE FROM membership");
        jdbc.update("DELETE FROM club");
        jdbc.update("DELETE FROM auth_identity");
        jdbc.update("DELETE FROM users");
    }

    // ---- 도우미 ----

    private long user(String name, String handle, boolean superAdmin, boolean imagePublic, String imageUrl) {
        jdbc.update(
                "INSERT INTO users (name, handle, is_super_admin, profile_image_public, profile_image_url, status,"
                        + " last_active_at) VALUES (?, ?, ?, ?, ?, 'ACTIVE', '2026-10-08 12:10:00.000000')",
                name,
                handle,
                superAdmin,
                imagePublic,
                imageUrl);
        return jdbc.queryForObject("SELECT id FROM users WHERE handle = ?", Long.class, handle);
    }

    private Cookie access(long userId) {
        return new Cookie(
                cookies.accessName(),
                jwtProvider.createAccessToken(userId, "sid-1", 0).getToken());
    }

    private MockHttpServletRequestBuilder forceLogout(long caller, String targetId, String body) {
        return post("/api/v1/admin/users/" + targetId + "/force-logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("X-Requested-With", "XMLHttpRequest")
                .cookie(access(caller));
    }

    private void audit(
            Long clubId,
            Long actor,
            String action,
            String targetType,
            Long targetId,
            String reason,
            String detail,
            String createdAt) {
        jdbc.update(
                "INSERT INTO audit_log (club_id, actor_user_id, action, target_type, target_id, reason, detail, created_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                clubId,
                actor,
                action,
                targetType,
                targetId,
                reason,
                detail,
                createdAt);
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    // ---- 권한 ----

    @Test
    @DisplayName("운영 API는 Super Admin만: 일반 사용자 403, 비로그인 401")
    void onlySuperAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users").param("userId", "1").cookie(access(normal)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mockMvc.perform(get("/api/v1/admin/audit-logs").cookie(access(normal))).andExpect(status().isForbidden());
        mockMvc.perform(forceLogout(normal, String.valueOf(admin), "{\"reason\":\"x\"}"))
                .andExpect(status().isForbidden());
        assertThat(count("SELECT token_version FROM users WHERE id = ?", admin)).isZero();

        mockMvc.perform(get("/api/v1/admin/audit-logs")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/audit-logs").cookie(access(admin))).andExpect(status().isOk());
    }

    // ---- API 123 ----

    @Test
    @DisplayName("API 123: 대상의 모든 Refresh를 FORCE_LOGOUT으로 폐기하고 token_version을 올리며 감사 기록을 남긴다")
    void forceLogoutRevokes() throws Exception {
        long target = user("대상", "target_user", false, true, null);
        for (int i = 0; i < 2; i++) {
            jdbc.update(
                    "INSERT INTO refresh_token (user_id, family_id, token_hash, expires_at, family_expires_at) VALUES (?, UUID(),"
                            + " UNHEX(SHA2(?, 256)), DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 30 DAY), DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 90 DAY))",
                    target,
                    "rt-" + i);
        }
        jdbc.update(
                "UPDATE refresh_token SET revoked_at = UTC_TIMESTAMP(6), revoke_reason = 'LOGOUT' WHERE token_hash = UNHEX(SHA2('rt-0', 256))");
        String oldAccess = jwtProvider.createAccessToken(target, "sid-1", 0).getToken();

        mockMvc.perform(forceLogout(admin, String.valueOf(target), "{\"reason\":\"  계정 탈취 의심  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(String.valueOf(target)))
                .andExpect(jsonPath("$.data.revoked").value(true))
                .andExpect(jsonPath("$.meta.requestId").value(matchesPattern("req-[a-z0-9]{12}")));

        assertThat(count("SELECT token_version FROM users WHERE id = ?", target))
                .isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM refresh_token WHERE user_id = ? AND revoked_at IS NULL", target))
                .isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM refresh_token WHERE revoke_reason = 'FORCE_LOGOUT'", Integer.class))
                .isEqualTo(1);
        // 이미 로그아웃으로 폐기된 토큰의 사유는 그대로 둔다
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM refresh_token WHERE revoke_reason = 'LOGOUT'", Integer.class))
                .isEqualTo(1);
        mockMvc.perform(get("/api/v1/_test/protected").cookie(new Cookie(cookies.accessName(), oldAccess)))
                .andExpect(status().isUnauthorized());

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT club_id, actor_user_id, action, target_type, target_id, reason, detail FROM audit_log");
        assertThat(row)
                .containsEntry("action", "FORCE_LOGOUT")
                .containsEntry("target_type", "USER")
                .containsEntry("target_id", target)
                .containsEntry("actor_user_id", admin)
                .containsEntry("reason", "계정 탈취 의심");
        assertThat(row.get("club_id")).isNull();
        assertThat(String.valueOf(row.get("detail"))).contains("sessionVersion").doesNotContain("대상");
    }

    @Test
    @DisplayName("API 123: 여러 번 호출해도 안전하다(버전은 그때마다 오른다)")
    void forceLogoutRepeated() throws Exception {
        long target = user("대상", "target_user", false, true, null);

        mockMvc.perform(forceLogout(admin, String.valueOf(target), "{\"reason\":\"a\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(forceLogout(admin, String.valueOf(target), "{\"reason\":\"b\"}"))
                .andExpect(status().isOk());

        assertThat(count("SELECT token_version FROM users WHERE id = ?", target))
                .isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM audit_log WHERE action = 'FORCE_LOGOUT'"))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("API 123: 없는 사용자 404, 사유 누락·공백·501자·잘못된 ID는 400이고 아무것도 바꾸지 않는다")
    void forceLogoutValidation() throws Exception {
        long target = user("대상", "target_user", false, true, null);

        mockMvc.perform(forceLogout(admin, "999999", "{\"reason\":\"x\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        for (String body : new String[] {
            "{}",
            "{\"reason\":\"\"}",
            "{\"reason\":\"   \"}",
            "{\"reason\":null}",
            "{\"reason\":\"" + "가".repeat(501) + "\"}"
        }) {
            mockMvc.perform(forceLogout(admin, String.valueOf(target), body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
        for (String badId : new String[] {"abc", "0", "-3"}) {
            mockMvc.perform(forceLogout(admin, badId, "{\"reason\":\"x\"}")).andExpect(status().isBadRequest());
        }
        mockMvc.perform(forceLogout(admin, String.valueOf(target), "{\"reason\":\"" + "가".repeat(500) + "\"}"))
                .andExpect(status().isOk());
        assertThat(count("SELECT COUNT(*) FROM audit_log")).isEqualTo(1);
    }

    @Test
    @DisplayName("API 123: X-Requested-With 없음 403, JSON이 아닌 Content-Type 400")
    void forceLogoutGuards() throws Exception {
        long target = user("대상", "target_user", false, true, null);

        mockMvc.perform(post("/api/v1/admin/users/" + target + "/force-logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"x\"}")
                        .cookie(access(admin)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/users/" + target + "/force-logout")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("{}")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(access(admin)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].reason").value("UNSUPPORTED_MEDIA_TYPE"));
        assertThat(count("SELECT token_version FROM users WHERE id = ?", target))
                .isZero();
    }

    // ---- API 122 ----

    @Test
    @DisplayName("API 122: userId로 조회하면 마지막 활동·카카오 연결 여부·소속 동아리를 돌려주고 providerUid는 싣지 않는다")
    void searchByUserId() throws Exception {
        long target = user("정규채", "gyuchae", false, true, "https://cdn.example/7.png");
        jdbc.update(
                "INSERT INTO auth_identity (user_id, provider, provider_uid) VALUES (?, 'KAKAO', 'secret-uid-1')",
                target);
        for (String name : new String[] {"B Band", "A Band"}) {
            jdbc.update(
                    "INSERT INTO club (name, club_type, status, created_by) VALUES (?, 'BAND', 'ACTIVE', ?)",
                    name,
                    target);
            long clubId = jdbc.queryForObject("SELECT id FROM club WHERE name = ?", Long.class, name);
            jdbc.update(
                    "INSERT INTO membership (club_id, user_id, role, status, join_source, approved_at) VALUES (?, ?, ?, 'ACTIVE', 'JOIN_CODE', UTC_TIMESTAMP(6))",
                    clubId,
                    target,
                    name.startsWith("A") ? "CLUB_ADMIN" : "MEMBER");
        }

        String body = mockMvc.perform(get("/api/v1/admin/users")
                        .param("userId", String.valueOf(target))
                        .cookie(access(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].id").value(String.valueOf(target)))
                .andExpect(jsonPath("$.data.items[0].name").value("정규채"))
                .andExpect(jsonPath("$.data.items[0].handle").value("gyuchae"))
                .andExpect(jsonPath("$.data.items[0].profileImageUrl").value("https://cdn.example/7.png"))
                .andExpect(jsonPath("$.data.items[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.items[0].isSuperAdmin").value(false))
                .andExpect(jsonPath("$.data.items[0].lastActiveAt").value("2026-10-08T21:10:00+09:00"))
                .andExpect(jsonPath("$.data.items[0].kakaoLinked").value(true))
                .andExpect(jsonPath("$.data.items[0].clubs", hasSize(2)))
                .andExpect(jsonPath("$.data.items[0].clubs[0].name").value("A Band"))
                .andExpect(jsonPath("$.data.items[0].clubs[0].role").value("CLUB_ADMIN"))
                .andExpect(jsonPath("$.data.items[0].clubs[0].membershipStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.items[0].clubs[0].joinSource").value("JOIN_CODE"))
                .andExpect(jsonPath("$.data.items[0].clubs[0].clubId").isString())
                .andExpect(jsonPath("$.data.items[0].clubs[1].name").value("B Band"))
                .andExpect(jsonPath("$.data.items[0].clubs[1].role").value("MEMBER"))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(body).doesNotContain("secret-uid-1").doesNotContain("providerUid");
    }

    @Test
    @DisplayName("API 122: handle로 정확 조회하고, 카카오 연결이 없으면 kakaoLinked=false, 소속이 없으면 clubs는 빈 배열")
    void searchByHandle() throws Exception {
        long target = user("정규채", "gyuchae", false, true, null);

        mockMvc.perform(get("/api/v1/admin/users").param("handle", "gyuchae").cookie(access(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(String.valueOf(target)))
                .andExpect(jsonPath("$.data.items[0].kakaoLinked").value(false))
                .andExpect(jsonPath("$.data.items[0].clubs", hasSize(0)));
        // 부분 일치는 찾지 않는다(형식은 맞아도 정확히 같은 handle만 찾고, 5자 미만은 형식 오류)
        mockMvc.perform(get("/api/v1/admin/users").param("handle", "gyuch").cookie(access(admin)))
                .andExpect(jsonPath("$.data.items", hasSize(0)));
        mockMvc.perform(get("/api/v1/admin/users").param("handle", "gyuc").cookie(access(admin)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/admin/users").param("handle", "gyuchae1").cookie(access(admin)))
                .andExpect(jsonPath("$.data.items", hasSize(0)))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    @DisplayName("API 122: 비공개 사진은 Super Admin에게도 null이다(U-13)")
    void privatePhotoHiddenFromAdmin() throws Exception {
        long target = user("비공개", "private_one", false, false, "https://cdn.example/secret.png");

        String body = mockMvc.perform(get("/api/v1/admin/users")
                        .param("userId", String.valueOf(target))
                        .cookie(access(admin)))
                .andExpect(jsonPath("$.data.items[0].profileImageUrl").isEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(body).doesNotContain("secret.png");
    }

    @Test
    @DisplayName("API 122: userId·handle 중 정확히 하나가 필요하다. 없거나 둘 다이거나 형식이 틀리면 400, 없는 사용자는 빈 페이지")
    void searchValidation() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users").cookie(access(admin)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        mockMvc.perform(get("/api/v1/admin/users")
                        .param("userId", "1")
                        .param("handle", "abcde")
                        .cookie(access(admin)))
                .andExpect(status().isBadRequest());
        for (String bad : new String[] {"abc", "0", "-1"}) {
            mockMvc.perform(get("/api/v1/admin/users").param("userId", bad).cookie(access(admin)))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(get("/api/v1/admin/users").param("userId", "999999").cookie(access(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(0)));
        mockMvc.perform(get("/api/v1/admin/users")
                        .param("userId", String.valueOf(normal))
                        .param("page", "1")
                        .cookie(access(admin)))
                .andExpect(jsonPath("$.data.items", hasSize(0)))
                .andExpect(jsonPath("$.data.totalElements").value(1));
        mockMvc.perform(get("/api/v1/admin/users")
                        .param("userId", "1")
                        .param("size", "101")
                        .cookie(access(admin)))
                .andExpect(status().isBadRequest());
    }

    // ---- API 125 ----

    @Test
    @DisplayName("API 125: 최근 기록부터 돌려주고 detail은 객체, 없으면 빈 객체. 동아리와 무관한 기록은 clubId가 null")
    void auditLogsOrderAndShape() throws Exception {
        audit(
                null,
                admin,
                "FORCE_LOGOUT",
                "USER",
                normal,
                "사유",
                "{\"before\": {\"sessionVersion\": 0}}",
                "2026-10-01 00:00:00");
        audit(10L, normal, "PERFORMANCE_CREATED", "PERFORMANCE", 101L, null, null, "2026-10-03 00:00:00");
        audit(null, null, "ACCOUNT_PURGED", "USER", normal, null, "{}", "2026-10-02 00:00:00");

        mockMvc.perform(get("/api/v1/admin/audit-logs").cookie(access(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(3)))
                .andExpect(jsonPath("$.data.items[0].action").value("PERFORMANCE_CREATED"))
                .andExpect(jsonPath("$.data.items[1].action").value("ACCOUNT_PURGED"))
                .andExpect(jsonPath("$.data.items[2].action").value("FORCE_LOGOUT"))
                .andExpect(jsonPath("$.data.items[0].clubId").value("10"))
                .andExpect(jsonPath("$.data.items[0].targetId").value("101"))
                .andExpect(jsonPath("$.data.items[0].actorUserId").value(String.valueOf(normal)))
                .andExpect(jsonPath("$.data.items[0].reason").isEmpty())
                .andExpect(jsonPath("$.data.items[0].detail").isMap())
                .andExpect(jsonPath("$.data.items[0].detail").isEmpty())
                .andExpect(jsonPath("$.data.items[0].createdAt").value("2026-10-03T09:00:00+09:00"))
                .andExpect(jsonPath("$.data.items[1].actorUserId").isEmpty())
                .andExpect(jsonPath("$.data.items[2].clubId").isEmpty())
                .andExpect(jsonPath("$.data.items[2].reason").value("사유"))
                .andExpect(
                        jsonPath("$.data.items[2].detail.before.sessionVersion").value(0))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.meta.requestId").exists());
    }

    @Test
    @DisplayName("API 125: clubId·action(쉼표 다중)·targetType·targetId·actorUserId·기간으로 거른다")
    void auditLogsFilters() throws Exception {
        audit(10L, admin, "CLUB_DEACTIVATED", "CLUB", 10L, "r", null, "2026-10-01 00:00:00");
        audit(10L, normal, "ADMIN_GRANTED", "MEMBERSHIP", 55L, null, null, "2026-10-02 00:00:00");
        audit(null, admin, "FORCE_LOGOUT", "USER", normal, "r", null, "2026-10-03 00:00:00");
        audit(11L, admin, "FORCE_LOGOUT", "USER", admin, "r", null, "2026-10-04 00:00:00");

        mockMvc.perform(get("/api/v1/admin/audit-logs").param("clubId", "10").cookie(access(admin)))
                .andExpect(jsonPath("$.data.items", hasSize(2)));
        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .param("action", "CLUB_DEACTIVATED, FORCE_LOGOUT")
                        .cookie(access(admin)))
                .andExpect(jsonPath("$.data.items", hasSize(3)));
        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .param("targetType", "USER")
                        .cookie(access(admin)))
                .andExpect(jsonPath("$.data.items", hasSize(2)));
        mockMvc.perform(get("/api/v1/admin/audit-logs").param("targetId", "55").cookie(access(admin)))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].action").value("ADMIN_GRANTED"));
        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .param("actorUserId", String.valueOf(admin))
                        .cookie(access(admin)))
                .andExpect(jsonPath("$.data.items", hasSize(3)));
        // 기간은 양 끝을 포함한다: KST 10-02 09:00 = UTC 10-02 00:00
        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .param("from", "2026-10-02T09:00:00+09:00")
                        .param("to", "2026-10-03T09:00:00+09:00")
                        .cookie(access(admin)))
                .andExpect(jsonPath("$.data.items", hasSize(2)));
        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .param("clubId", "10")
                        .param("action", "ADMIN_GRANTED")
                        .param("actorUserId", String.valueOf(normal))
                        .cookie(access(admin)))
                .andExpect(jsonPath("$.data.items", hasSize(1)));
    }

    @Test
    @DisplayName("API 125: 페이지 번호는 0부터이고, 잘못된 ID·기간·size는 400")
    void auditLogsPagingAndValidation() throws Exception {
        for (int i = 1; i <= 5; i++) {
            audit(null, admin, "FORCE_LOGOUT", "USER", normal, "r", null, "2026-10-0" + i + " 00:00:00");
        }

        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .param("size", "2")
                        .param("page", "2")
                        .cookie(access(admin)))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].createdAt").value("2026-10-01T09:00:00+09:00"))
                .andExpect(jsonPath("$.data.totalElements").value(5))
                .andExpect(jsonPath("$.data.totalPages").value(3));
        for (String[] bad : new String[][] {
            {"clubId", "abc"},
            {"targetId", "0"},
            {"actorUserId", "-1"},
            {"from", "yesterday"},
            {"size", "101"},
            {"size", "0"},
            {"page", "-1"}
        }) {
            mockMvc.perform(get("/api/v1/admin/audit-logs")
                            .param(bad[0], bad[1])
                            .cookie(access(admin)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .param("from", "2026-10-05T00:00:00+09:00")
                        .param("to", "2026-10-01T00:00:00+09:00")
                        .cookie(access(admin)))
                .andExpect(status().isBadRequest());
    }
}
