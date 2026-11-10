package com.crane.bandwebbackend.domain.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crane.bandwebbackend.TestcontainersConfiguration;
import com.crane.bandwebbackend.global.security.CookieFactory;
import com.crane.bandwebbackend.global.security.JwtProvider;
import com.crane.bandwebbackend.global.time.InvitationExpiry;
import com.crane.bandwebbackend.global.time.KstTime;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
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
import org.springframework.test.web.servlet.MockMvc;

@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class MyMembershipApiTest {

    private static final String URL = "/api/v1/me/memberships";

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
    int seq;

    @BeforeEach
    void setUp() {
        clean();
        me = user("나", "me_user");
        other = user("타인", "other_user");
    }

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM membership_part");
        jdbc.update("DELETE FROM part");
        jdbc.update("DELETE FROM membership");
        jdbc.update("DELETE FROM club");
        jdbc.update("DELETE FROM auth_identity");
        jdbc.update("DELETE FROM users");
    }

    private long user(String name, String handle) {
        jdbc.update("INSERT INTO users (name, handle) VALUES (?, ?)", name, handle);
        return jdbc.queryForObject("SELECT id FROM users WHERE handle = ?", Long.class, handle);
    }

    private long club(String name, String status) {
        jdbc.update(
                "INSERT INTO club (name, club_type, status, created_by, theme_color) VALUES (?, 'BAND', ?, ?,"
                        + " '#1A2B3C')",
                name,
                status,
                other);
        return jdbc.queryForObject("SELECT id FROM club WHERE name = ?", Long.class, name);
    }

    // 만료되지 않은 초대(10일 전). 초 단위로 맞춘다
    private final LocalDateTime invitedAt =
            LocalDateTime.now(ZoneOffset.UTC).minusDays(10).truncatedTo(ChronoUnit.SECONDS);

    // lastViewed는 UTC 문자열 또는 null, invitedAt은 INVITED일 때만 채운다
    private long member(long clubId, long userId, String status, String lastViewed) {
        jdbc.update(
                "INSERT INTO membership (club_id, user_id, status, join_source, invited_at, last_viewed_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                clubId,
                userId,
                status,
                "INVITED".equals(status) ? "ADMIN_ADDED" : "JOIN_CODE",
                "INVITED".equals(status) ? invitedAt : null,
                lastViewed);
        return jdbc.queryForObject(
                "SELECT id FROM membership WHERE club_id = ? AND user_id = ?", Long.class, clubId, userId);
    }

    private Cookie access(long userId) {
        return new Cookie(
                cookies.accessName(),
                jwtProvider.createAccessToken(userId, "sid-1", 0).getToken());
    }

    @Test
    @DisplayName("API 10: 소속이 없으면 빈 목록과 0건 카운트")
    void empty() throws Exception {
        mockMvc.perform(get(URL).cookie(access(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(0)))
                .andExpect(jsonPath("$.data.pendingCount").value(0))
                .andExpect(jsonPath("$.data.invitedCount").value(0))
                .andExpect(jsonPath("$.data.totalElements").value(0))
                .andExpect(jsonPath("$.data.size").value(100))
                .andExpect(jsonPath("$.meta.requestId").exists());
    }

    @Test
    @DisplayName("API 10: 모든 상태를 기본으로 보이고 동아리 요약·파트·건수를 담는다")
    void allStatusesWithSummary() throws Exception {
        long a = club("Alpha", "ACTIVE");
        long b = club("Beta", "ACTIVE");
        long c = club("Gamma", "ACTIVE");
        long d = club("Delta", "ACTIVE");
        long active = member(a, me, "ACTIVE", "2026-10-08 12:10:00");
        member(b, me, "PENDING", null);
        member(c, me, "INVITED", null);
        member(d, me, "REJECTED", null);
        jdbc.update("INSERT INTO part (club_id, name, display_order) VALUES (?, '드럼', 4)", a);
        jdbc.update("INSERT INTO part (club_id, name, display_order, is_active) VALUES (?, '건반', 2, FALSE)", a);
        jdbc.update(
                "INSERT INTO membership_part (membership_id, part_id) SELECT ?, id FROM part WHERE club_id = ?",
                active,
                a);

        mockMvc.perform(get(URL).cookie(access(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(4)))
                .andExpect(jsonPath("$.data.totalElements").value(4))
                .andExpect(jsonPath("$.data.pendingCount").value(1))
                .andExpect(jsonPath("$.data.invitedCount").value(1))
                // 최근 본 소속이 맨 앞, 값 없는 소속이 뒤
                .andExpect(jsonPath("$.data.items[0].id").value(String.valueOf(active)))
                .andExpect(jsonPath("$.data.items[0].clubId").value(String.valueOf(a)))
                .andExpect(jsonPath("$.data.items[0].club.name").value("Alpha"))
                .andExpect(jsonPath("$.data.items[0].club.clubType").value("BAND"))
                .andExpect(jsonPath("$.data.items[0].club.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.items[0].club.themeColor").value("#1A2B3C"))
                .andExpect(jsonPath("$.data.items[0].club.profileImageUrl").value((Object) null))
                .andExpect(jsonPath("$.data.items[0].displayName").value("나"))
                .andExpect(jsonPath("$.data.items[0].lastViewedAt").value("2026-10-08T21:10:00+09:00"))
                .andExpect(jsonPath("$.data.items[0].createdAt").value(endsWith("+09:00")))
                .andExpect(jsonPath("$.data.items[0].requestedAt").value((Object) null))
                .andExpect(jsonPath("$.data.items[0].invitationExpiresAt").value((Object) null))
                .andExpect(jsonPath("$.data.items[0].parts", hasSize(2)))
                .andExpect(jsonPath("$.data.items[0].parts[0].name").value("건반"))
                .andExpect(jsonPath("$.data.items[0].parts[0].isActive").value(false))
                .andExpect(jsonPath("$.data.items[0].parts[1].name").value("드럼"))
                .andExpect(jsonPath("$.data.items[0].parts[1].isActive").value(true));
    }

    @Test
    @DisplayName("API 10: INVITED만 invitationExpiresAt(invitedAt+1개월), PENDING만 requestedAt을 가진다")
    void invitationAndRequestedAt() throws Exception {
        long b = club("Beta", "ACTIVE");
        long c = club("Gamma", "ACTIVE");
        member(b, me, "PENDING", null);
        member(c, me, "INVITED", null);

        mockMvc.perform(get(URL).param("status", "INVITED").cookie(access(me)))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].invitationExpiresAt")
                        .value(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(InvitationExpiry.expiresAt(invitedAt))))
                .andExpect(jsonPath("$.data.items[0].requestedAt").value((Object) null));
        mockMvc.perform(get(URL).param("status", "PENDING").cookie(access(me)))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].invitationExpiresAt").value((Object) null))
                .andExpect(jsonPath("$.data.items[0].requestedAt").value(endsWith("+09:00")));
    }

    @Test
    @DisplayName("API 10: 만료된 초대(invited_at+1개월)는 배지 건수·목록·전체 건수에서 모두 빠진다")
    void expiredInvitationExcluded() throws Exception {
        long valid = member(club("A", "ACTIVE"), me, "INVITED", null);
        long expired = member(club("B", "ACTIVE"), me, "INVITED", null);
        jdbc.update(
                "UPDATE membership SET invited_at = DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 40 DAY) WHERE id = ?", expired);

        mockMvc.perform(get(URL).cookie(access(me)))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].id").value(String.valueOf(valid)))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.invitedCount").value(1));
        mockMvc.perform(get(URL).param("status", "INVITED").cookie(access(me)))
                .andExpect(jsonPath("$.data.items", hasSize(1)));
    }

    @Test
    @DisplayName("초대 만료는 KST 달력으로 센다: SQL 식과 Java 계산이 같고 UTC 달력과는 다르다")
    void expiryUsesKstCalendar() {
        // KST 01-31 01:00에 초대 → KST 달력으로 02-28 01:00 = UTC 02-27 16:00 (UTC 달력이면 02-28 16:00)
        LocalDateTime invited = LocalDateTime.of(2026, 1, 30, 16, 0);
        LocalDateTime sql = jdbc.queryForObject(
                "SELECT " + InvitationExpiry.expiresAtUtcSql("CAST(? AS DATETIME)"), LocalDateTime.class, invited);

        assertThat(sql).isEqualTo(LocalDateTime.of(2026, 2, 27, 16, 0));
        assertThat(KstTime.toUtc(InvitationExpiry.expiresAt(invited))).isEqualTo(sql);
    }

    @Test
    @DisplayName("API 10: status 쉼표 다중 필터는 건수를 줄여도 pendingCount·invitedCount는 전체 기준")
    void statusFilter() throws Exception {
        member(club("A", "ACTIVE"), me, "ACTIVE", null);
        member(club("B", "ACTIVE"), me, "ON_LEAVE", null);
        member(club("C", "ACTIVE"), me, "GRADUATED", null);
        member(club("D", "ACTIVE"), me, "PENDING", null);

        mockMvc.perform(get(URL).param("status", "ACTIVE,ON_LEAVE").cookie(access(me)))
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.pendingCount").value(1));
    }

    @Test
    @DisplayName("API 10: 비활성 동아리 소속도 club.status INACTIVE로 보인다")
    void inactiveClub() throws Exception {
        member(club("Old", "INACTIVE"), me, "ACTIVE", null);

        mockMvc.perform(get(URL).cookie(access(me)))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].club.status").value("INACTIVE"));
    }

    @Test
    @DisplayName("API 10: 타인의 소속은 보이지 않는다")
    void othersHidden() throws Exception {
        long a = club("Alpha", "ACTIVE");
        member(a, other, "ACTIVE", null);

        mockMvc.perform(get(URL).cookie(access(me)))
                .andExpect(jsonPath("$.data.items", hasSize(0)))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    @DisplayName("API 10: page·size 페이지네이션")
    void paging() throws Exception {
        member(club("A", "ACTIVE"), me, "ACTIVE", "2026-10-03 00:00:00");
        member(club("B", "ACTIVE"), me, "ACTIVE", "2026-10-02 00:00:00");
        member(club("C", "ACTIVE"), me, "ACTIVE", "2026-10-01 00:00:00");

        mockMvc.perform(get(URL).param("size", "2").param("page", "1").cookie(access(me)))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].club.name").value("C"))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.page").value(1));
    }

    @Test
    @DisplayName("API 10: last_viewed_at이 같거나 없으면 id 내림차순이라 페이지 경계에서 빠지거나 겹치지 않는다")
    void tieBreakAcrossPages() throws Exception {
        long viewed = member(club("A", "ACTIVE"), me, "ACTIVE", "2026-10-01 00:00:00");
        long n1 = member(club("B", "ACTIVE"), me, "ACTIVE", null);
        long n2 = member(club("C", "ACTIVE"), me, "ACTIVE", null);
        long n3 = member(club("D", "ACTIVE"), me, "ACTIVE", null);

        // 기대 순서: 값이 있는 소속, 그 뒤 값이 없는 소속은 id 내림차순
        mockMvc.perform(get(URL).param("size", "2").param("page", "0").cookie(access(me)))
                .andExpect(jsonPath("$.data.items[0].id").value(String.valueOf(viewed)))
                .andExpect(jsonPath("$.data.items[1].id").value(String.valueOf(n3)));
        mockMvc.perform(get(URL).param("size", "2").param("page", "1").cookie(access(me)))
                .andExpect(jsonPath("$.data.items[0].id").value(String.valueOf(n2)))
                .andExpect(jsonPath("$.data.items[1].id").value(String.valueOf(n1)));
    }

    @Test
    @DisplayName("API 10: 잘못된 status·size는 400 VALIDATION_ERROR")
    void invalidParams() throws Exception {
        mockMvc.perform(get(URL).param("status", "ACTIVE,NOPE").cookie(access(me)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0].field").value("status"));
        mockMvc.perform(get(URL).param("size", "101").cookie(access(me)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("API 10: 미인증은 401")
    void unauthenticated() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }
}
