package com.crane.bandwebbackend.domain.club.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crane.bandwebbackend.TestcontainersConfiguration;
import com.crane.bandwebbackend.global.security.CookieFactory;
import com.crane.bandwebbackend.global.security.JwtProvider;
import com.crane.bandwebbackend.global.storage.InMemoryImageStorage;
import com.crane.bandwebbackend.global.storage.InMemoryStorageConfig;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
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
class ClubSettingsApiTest {

    private static final String CDN = InMemoryImageStorage.PUBLIC_BASE;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JwtProvider jwtProvider;

    @Autowired
    CookieFactory cookies;

    @Autowired
    InMemoryImageStorage storage;

    long me;
    long other;
    long clubId;

    @BeforeEach
    void setUp() {
        clean();
        me = user("나", "me_user_x");
        other = user("남", "other_user_x");
        clubId = club("Crane Band", "BAND", "ACTIVE");
        membership(clubId, me, "ACTIVE", "REGULAR", "CLUB_ADMIN");
    }

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM audit_log");
        jdbc.update("DELETE FROM part");
        jdbc.update("DELETE FROM membership");
        jdbc.update("DELETE FROM club");
        jdbc.update("DELETE FROM users");
        storage.clear();
    }

    // ---- 도우미 ----

    private long user(String name, String handle) {
        jdbc.update("INSERT INTO users (name, handle, status) VALUES (?, ?, 'ACTIVE')", name, handle);
        return jdbc.queryForObject("SELECT id FROM users WHERE handle = ?", Long.class, handle);
    }

    private long club(String name, String type, String status) {
        jdbc.update(
                "INSERT INTO club (name, club_type, status, is_searchable, created_by, join_code)"
                        + " VALUES (?, ?, ?, false, ?, ?)",
                name,
                type,
                status,
                other,
                "Crane Band".equals(name) ? "ABCD234XYZ" : null);
        return jdbc.queryForObject("SELECT id FROM club WHERE name = ?", Long.class, name);
    }

    private void membership(long club, long user, String status, String memberType, String role) {
        jdbc.update(
                "INSERT INTO membership (club_id, user_id, status, member_type, role, join_source, invited_at)"
                        + " VALUES (?, ?, ?, ?, ?, 'JOIN_CODE', CASE WHEN ? = 'INVITED' THEN UTC_TIMESTAMP(6) END)",
                club,
                user,
                status,
                memberType,
                role,
                status);
    }

    private Cookie access(long userId) {
        return new Cookie(
                cookies.accessName(),
                jwtProvider.createAccessToken(userId, "sid-1", 0).getToken());
    }

    private MockHttpServletRequestBuilder read(long actor, long id) {
        return get("/api/v1/clubs/" + id).cookie(access(actor));
    }

    private MockHttpServletRequestBuilder patchAs(long actor, long id, String body) {
        return patch("/api/v1/clubs/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("X-Requested-With", "XMLHttpRequest")
                .cookie(access(actor));
    }

    private MockHttpServletRequestBuilder patchMine(String body) {
        return patchAs(me, clubId, body);
    }

    private String column(String column) {
        return jdbc.queryForObject("SELECT " + column + " FROM club WHERE id = ?", String.class, clubId);
    }

    private int auditCount(String action) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action = ?", Integer.class, action);
    }

    private String tmpKey(long userId) {
        String key = "tmp/club-profile/" + userId + "/" + UUID.randomUUID() + ".png";
        storage.put(key, new byte[] {1, 2, 3}, "image/png");
        return key;
    }

    // ---- API 15 ----

    @Test
    @DisplayName("API 15: 정회원·졸업 소속은 200이고 joinCode 키가 없다")
    void detailForMembers() throws Exception {
        long grad = user("졸업", "grad_user_x");
        membership(clubId, grad, "GRADUATED", "REGULAR", "MEMBER");
        for (long actor : new long[] {me, grad}) {
            mockMvc.perform(read(actor, clubId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(String.valueOf(clubId)))
                    .andExpect(jsonPath("$.data.name").value("Crane Band"))
                    .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.data.isSearchable").value(false))
                    .andExpect(jsonPath("$.data.createdByUserId").value(String.valueOf(other)))
                    .andExpect(jsonPath("$.data.createdAt").exists())
                    .andExpect(jsonPath("$.data.joinCode").doesNotExist());
        }
    }

    @Test
    @DisplayName("API 15: 객원은 id·name·description·clubType·profileImageUrl·themeColor만 받는다")
    void detailForGuest() throws Exception {
        long guest = user("객원", "guest_user_x");
        membership(clubId, guest, "ACTIVE", "GUEST", "MEMBER");
        mockMvc.perform(read(guest, clubId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Crane Band"))
                .andExpect(jsonPath("$.data.clubType").value("BAND"))
                .andExpect(jsonPath("$.data.profileImageUrl").isEmpty())
                .andExpect(jsonPath("$.data.status").doesNotExist())
                .andExpect(jsonPath("$.data.isSearchable").doesNotExist())
                .andExpect(jsonPath("$.data.createdByUserId").doesNotExist())
                .andExpect(jsonPath("$.data.joinCode").doesNotExist());
    }

    @Test
    @DisplayName("G-04 API 15: PENDING·INVITED는 403, 비소속·없는 동아리는 404")
    void detailDenied() throws Exception {
        long pending = user("대기", "pending_user_x");
        long invited = user("초대", "invited_user_x");
        membership(clubId, pending, "PENDING", "REGULAR", "MEMBER");
        membership(clubId, invited, "INVITED", "REGULAR", "MEMBER");
        mockMvc.perform(read(pending, clubId)).andExpect(status().isForbidden());
        mockMvc.perform(read(invited, clubId)).andExpect(status().isForbidden());
        mockMvc.perform(read(other, clubId)).andExpect(status().isNotFound());
        mockMvc.perform(read(me, clubId + 999)).andExpect(status().isNotFound());
    }

    // ---- API 16 ----

    @Test
    @DisplayName("API 16: 보낸 필드만 바꾸고 isSearchable 키가 바인딩된다")
    void partialUpdate() throws Exception {
        mockMvc.perform(patchMine("{\"isSearchable\":true,\"description\":\"새 설명\",\"themeColor\":\"#1A2B3C\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isSearchable").value(true))
                .andExpect(jsonPath("$.data.description").value("새 설명"))
                .andExpect(jsonPath("$.data.themeColor").value("#1A2B3C"))
                .andExpect(jsonPath("$.data.name").value("Crane Band"))
                .andExpect(jsonPath("$.data.clubType").value("BAND"))
                .andExpect(jsonPath("$.data.joinCode").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT is_searchable FROM club WHERE id = ?", Boolean.class, clubId))
                .isTrue();
        assertThat(column("name")).isEqualTo("Crane Band");
        // status·joinCode 등 수정 불가 필드는 무시한다
        mockMvc.perform(patchMine("{\"status\":\"INACTIVE\",\"joinCode\":\"ZZZZZZZZZZ\"}"))
                .andExpect(status().isOk());
        assertThat(column("status")).isEqualTo("ACTIVE");
        assertThat(column("join_code")).isEqualTo("ABCD234XYZ");
    }

    @Test
    @DisplayName("C-15 API 16: 다른 동아리 이름과 공백·대소문자만 달라도 409이고, 자기 이름은 허용한다")
    void nameDuplicate() throws Exception {
        club("Taken Band", "BAND", "INACTIVE");
        mockMvc.perform(patchMine("{\"name\":\"  taken   BAND \"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE"))
                .andExpect(jsonPath("$.error.details[0].reason").value("CLUB_NAME_TAKEN"));
        assertThat(column("name")).isEqualTo("Crane Band");
        mockMvc.perform(patchMine("{\"name\":\" crane band \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("crane band"));
    }

    @Test
    @DisplayName("API 16: clubType을 바꾸면 사유가 필수이고 CLUB_TYPE_CHANGED가 기록된다")
    void clubTypeChange() throws Exception {
        mockMvc.perform(patchMine("{\"clubType\":\"CLUB\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0].field").value("clubTypeChangeReason"))
                .andExpect(jsonPath("$.error.details[0].reason").value("REASON_REQUIRED"));
        mockMvc.perform(patchMine("{\"clubType\":\"CLUB\",\"clubTypeChangeReason\":\"" + "a".repeat(501) + "\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patchMine("{\"clubType\":\"ORCHESTRA\",\"clubTypeChangeReason\":\"x\"}"))
                .andExpect(status().isBadRequest());
        assertThat(column("club_type")).isEqualTo("BAND");
        assertThat(auditCount("CLUB_TYPE_CHANGED")).isZero();

        mockMvc.perform(patchMine("{\"clubType\":\"CLUB\",\"clubTypeChangeReason\":\"  정식 동아리 전환  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.clubType").value("CLUB"));
        assertThat(column("club_type")).isEqualTo("CLUB");
        assertThat(jdbc.queryForObject(
                        "SELECT reason FROM audit_log WHERE action = 'CLUB_TYPE_CHANGED' AND club_id = ?"
                                + " AND actor_user_id = ? AND target_type = 'CLUB' AND target_id = ?",
                        String.class,
                        clubId,
                        me,
                        clubId))
                .isEqualTo("정식 동아리 전환");
        assertThat(jdbc.queryForObject(
                        "SELECT CONCAT(JSON_UNQUOTE(JSON_EXTRACT(detail, '$.from')), '>',"
                                + " JSON_UNQUOTE(JSON_EXTRACT(detail, '$.to'))) FROM audit_log",
                        String.class))
                .isEqualTo("BAND>CLUB");
    }

    @Test
    @DisplayName("API 16: 같은 clubType이면 사유 없이 200이고 감사 기록이 없다")
    void sameClubType() throws Exception {
        mockMvc.perform(patchMine("{\"clubType\":\"BAND\"}")).andExpect(status().isOk());
        assertThat(auditCount("CLUB_TYPE_CHANGED")).isZero();
    }

    @Test
    @DisplayName("API 16: themeColor 형식 오류는 400, null은 지운다. 공백 description은 null로 저장한다")
    void themeAndDescription() throws Exception {
        mockMvc.perform(patchMine("{\"themeColor\":\"#1a2b3c\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("themeColor"));
        mockMvc.perform(patchMine("{\"description\":\"" + "a".repeat(501) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("description"));
        mockMvc.perform(patchMine("{\"themeColor\":\"#1A2B3C\",\"description\":\"설명\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(patchMine("{\"themeColor\":null,\"description\":\"   \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.themeColor").isEmpty())
                .andExpect(jsonPath("$.data.description").isEmpty());
        assertThat(column("theme_color")).isNull();
        assertThat(column("description")).isNull();
    }

    @Test
    @DisplayName("API 16: 사진 키는 clubs/{id}/로 연결하고 이전 사진·임시 객체를 지우며, null이면 사진을 삭제한다")
    void profileImage() throws Exception {
        String oldFile = UUID.randomUUID() + ".png";
        storage.put("clubs/" + clubId + "/" + oldFile, new byte[] {9}, "image/png");
        jdbc.update(
                "UPDATE club SET profile_image_url = ? WHERE id = ?", CDN + "/clubs/" + clubId + "/" + oldFile, clubId);

        String key = tmpKey(me);
        String fileName = key.substring(key.lastIndexOf('/') + 1);
        mockMvc.perform(patchMine("{\"profileImageKey\":\"" + key + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileImageUrl").value(CDN + "/clubs/" + clubId + "/" + fileName));
        assertThat(storage.exists("clubs/" + clubId + "/" + fileName)).isTrue();
        assertThat(storage.exists("clubs/" + clubId + "/" + oldFile)).isFalse();
        assertThat(storage.exists(key)).isFalse();

        mockMvc.perform(patchMine("{\"profileImageKey\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileImageUrl").isEmpty());
        assertThat(column("profile_image_url")).isNull();
        assertThat(storage.exists("clubs/" + clubId + "/" + fileName)).isFalse();
    }

    @Test
    @DisplayName("API 16: 남의 키는 404 IMAGE_KEY_NOT_FOUND이고 다른 변경도 반영되지 않는다")
    void badImageKey() throws Exception {
        String othersKey = tmpKey(other);
        mockMvc.perform(patchMine("{\"description\":\"바뀌면 안 됨\",\"profileImageKey\":\"" + othersKey + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.details[0].reason").value("IMAGE_KEY_NOT_FOUND"));
        assertThat(column("description")).isNull();
        assertThat(storage.exists(othersKey)).isTrue();
    }

    @Test
    @DisplayName("API 16: 본인의 USER_PROFILE 임시 키도 404 IMAGE_KEY_NOT_FOUND")
    void userProfileKeyRejected() throws Exception {
        String key = "tmp/users/" + me + "/" + UUID.randomUUID() + ".png";
        storage.put(key, new byte[] {1, 2, 3}, "image/png");
        mockMvc.perform(patchMine("{\"profileImageKey\":\"" + key + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.details[0].reason").value("IMAGE_KEY_NOT_FOUND"));
        assertThat(storage.exists(key)).isTrue();
    }

    @Test
    @DisplayName("API 16: name·isSearchable·clubType을 명시적 null로 보내면 400")
    void explicitNull() throws Exception {
        for (String field : new String[] {"name", "isSearchable", "clubType"}) {
            mockMvc.perform(patchMine("{\"" + field + "\":null}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.error.details[0].field").value(field));
        }
        mockMvc.perform(patchMine("{\"name\":\"   \"}")).andExpect(status().isBadRequest());
        mockMvc.perform(patchMine("{\"name\":\"" + "a".repeat(101) + "\"}")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("G-04 API 16: 비임원 403, 비소속 404, 비활성 동아리 409 CLUB_INACTIVE")
    void accessRules() throws Exception {
        long member = user("부원", "member_user_x");
        membership(clubId, member, "ACTIVE", "REGULAR", "MEMBER");
        mockMvc.perform(patchAs(member, clubId, "{\"description\":\"x\"}")).andExpect(status().isForbidden());
        mockMvc.perform(patchAs(other, clubId, "{\"description\":\"x\"}")).andExpect(status().isNotFound());
        mockMvc.perform(patchAs(me, clubId + 999, "{\"description\":\"x\"}")).andExpect(status().isNotFound());
        assertThat(column("description")).isNull();

        jdbc.update("UPDATE club SET status = 'INACTIVE' WHERE id = ?", clubId);
        mockMvc.perform(patchMine("{\"description\":\"x\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("STATE_CONFLICT"))
                .andExpect(jsonPath("$.error.details[0].reason").value("CLUB_INACTIVE"));
        // 비활성 동아리도 조회는 허용한다
        mockMvc.perform(read(me, clubId)).andExpect(status().isOk());
    }
}
