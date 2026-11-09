package com.crane.bandwebbackend.domain.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crane.bandwebbackend.TestcontainersConfiguration;
import com.crane.bandwebbackend.global.security.CookieFactory;
import com.crane.bandwebbackend.global.security.JwtProvider;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
class AdminClubApiTest {

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
    int seq;

    @BeforeEach
    void setUp() {
        clean();
        admin = user("운영자", "admin_user", true, "ACTIVE", ts(-1));
        normal = user("일반", "normal_user", false, "ACTIVE", ts(-1));
    }

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM audit_log");
        jdbc.update("DELETE FROM practice_schedule");
        jdbc.update("DELETE FROM performance");
        jdbc.update("DELETE FROM membership");
        jdbc.update("DELETE FROM club");
        jdbc.update("DELETE FROM auth_identity");
        jdbc.update("DELETE FROM users");
    }

    // ---- 도우미 ----

    // 현재(UTC)에서 daysAgo일 전. 음수면 미래가 아니라 "오늘 직전"이 되도록 1일 전으로 쓴다
    private static String ts(int daysAgo) {
        return LocalDateTime.now(ZoneOffset.UTC)
                .minusDays(Math.abs(daysAgo))
                .toString()
                .replace('T', ' ');
    }

    private long user(String name, String handle, boolean superAdmin, String status, String lastActive) {
        jdbc.update(
                "INSERT INTO users (name, handle, is_super_admin, status, last_active_at) VALUES (?, ?, ?, ?, ?)",
                name,
                handle,
                superAdmin,
                status,
                lastActive);
        return jdbc.queryForObject("SELECT id FROM users WHERE handle = ?", Long.class, handle);
    }

    private long club(String name, String type, String status, long creator, String joinCode) {
        jdbc.update(
                "INSERT INTO club (name, club_type, status, created_by, join_code) VALUES (?, ?, ?, ?, ?)",
                name,
                type,
                status,
                creator,
                joinCode);
        return jdbc.queryForObject("SELECT id FROM club WHERE name = ?", Long.class, name);
    }

    private long member(long clubId, long userId, String role, String status, String type, String source) {
        jdbc.update(
                "INSERT INTO membership (club_id, user_id, role, status, member_type, join_source, approved_at,"
                        + " invited_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                clubId,
                userId,
                role,
                status,
                type,
                source,
                // 확정(ACTIVE)된 소속만 승인 시각을 가진다. 초대 경로는 상태와 무관하게 초대 시각을 남긴다
                "ACTIVE".equals(status) ? ts(0) : null,
                "ADMIN_ADDED".equals(source) ? ts(1) : null);
        return jdbc.queryForObject(
                "SELECT id FROM membership WHERE club_id = ? AND user_id = ?", Long.class, clubId, userId);
    }

    private long extraUser(String lastActive) {
        int n = ++seq;
        return user("멤버" + n, "member_" + n + "_x", false, "ACTIVE", lastActive);
    }

    private void performance(long clubId, String status, long creator) {
        jdbc.update(
                "INSERT INTO performance (club_id, title, preset, status, created_by_user_id) VALUES (?, ?, 'BAND', ?,"
                        + " ?)",
                clubId,
                "공연" + (++seq),
                status,
                creator);
    }

    private Cookie access(long userId) {
        return new Cookie(
                cookies.accessName(),
                jwtProvider.createAccessToken(userId, "sid-1", 0).getToken());
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    // 기본 시나리오: Alpha(임원 1, 활동 3, 휴학 1, 졸업 1, 객원 1, 진행 공연 1) 하나
    private long alpha() {
        long creator = user("생성자", "alpha_creator", false, "ACTIVE", ts(2));
        long id = club("Alpha Band", "BAND", "ACTIVE", creator, "ABCDEFGH23");
        member(id, creator, "CLUB_ADMIN", "ACTIVE", "REGULAR", "CLUB_CREATOR");
        member(id, extraUser(ts(5)), "MEMBER", "ACTIVE", "REGULAR", "JOIN_CODE");
        member(id, extraUser(ts(9)), "MEMBER", "ACTIVE", "GUEST", "ADMIN_ADDED");
        member(id, extraUser(ts(30)), "MEMBER", "ON_LEAVE", "REGULAR", "JOIN_CODE");
        member(id, extraUser(ts(40)), "MEMBER", "GRADUATED", "REGULAR", "JOIN_CODE");
        member(id, extraUser(ts(40)), "MEMBER", "PENDING", "REGULAR", "SEARCH");
        performance(id, "ACTIVE", creator);
        performance(id, "FINISHED", creator);
        performance(id, "DRAFT", creator);
        return id;
    }

    // ---- 권한 ----

    @Test
    @DisplayName("118·119·141은 Super Admin만: 일반 사용자 403, 비로그인 401")
    void onlySuperAdmin() throws Exception {
        for (String path : new String[] {"/api/v1/admin/clubs", "/api/v1/admin/clubs/1", "/api/v1/admin/overview"}) {
            mockMvc.perform(get(path).cookie(access(normal))).andExpect(status().isForbidden());
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
    }

    // ---- API 118 ----

    @Test
    @DisplayName("API 118: 활동 인원·임원 수·진행 공연 수·마지막 활동을 집계한다(가입 코드는 내려주지 않는다)")
    void listAggregates() throws Exception {
        long id = alpha();
        mockMvc.perform(get("/api/v1/admin/clubs").cookie(access(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].id").value(String.valueOf(id)))
                .andExpect(jsonPath("$.data.items[0].name").value("Alpha Band"))
                .andExpect(jsonPath("$.data.items[0].clubType").value("BAND"))
                .andExpect(jsonPath("$.data.items[0].activeMemberCount").value(3))
                .andExpect(jsonPath("$.data.items[0].adminCount").value(1))
                .andExpect(jsonPath("$.data.items[0].ongoingPerformanceCount").value(1))
                .andExpect(jsonPath("$.data.items[0].createdByUserId").exists())
                .andExpect(jsonPath("$.data.items[0].isSearchable").value(false))
                .andExpect(jsonPath("$.data.items[0].lastActiveAt").value(org.hamcrest.Matchers.endsWith("+09:00")))
                .andExpect(jsonPath("$.data.items[0].joinCode").doesNotExist())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.meta.requestId").exists());
    }

    @Test
    @DisplayName("API 118: status·clubType 다중 값, 이름 부분 일치(대소문자 무시), 숫자 q는 ID 완전 일치")
    void listFilters() throws Exception {
        long a = alpha();
        long creator = user("c2", "other_creator", false, "ACTIVE", ts(1));
        long b = club("beta club", "CLUB", "INACTIVE", creator, null);
        long c = club("100%_Gamma", "CLUB", "ACTIVE", creator, null);

        mockMvc.perform(get("/api/v1/admin/clubs").param("status", "INACTIVE").cookie(access(admin)))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].id").value(String.valueOf(b)));
        mockMvc.perform(get("/api/v1/admin/clubs")
                        .param("status", "ACTIVE,INACTIVE")
                        .cookie(access(admin)))
                .andExpect(jsonPath("$.data.totalElements").value(3));
        mockMvc.perform(get("/api/v1/admin/clubs").param("clubType", "BAND").cookie(access(admin)))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].id").value(String.valueOf(a)));
        mockMvc.perform(get("/api/v1/admin/clubs").param("q", "BETA").cookie(access(admin)))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].id").value(String.valueOf(b)));
        // %·_는 와일드카드가 아니라 글자로 비교한다
        mockMvc.perform(get("/api/v1/admin/clubs").param("q", "%").cookie(access(admin)))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].id").value(String.valueOf(c)));
        mockMvc.perform(get("/api/v1/admin/clubs").param("q", "_").cookie(access(admin)))
                .andExpect(jsonPath("$.data.items", hasSize(1)));
        mockMvc.perform(get("/api/v1/admin/clubs").param("q", String.valueOf(a)).cookie(access(admin)))
                .andExpect(jsonPath("$.data.items[*].id").value(org.hamcrest.Matchers.hasItem(String.valueOf(a))));
    }

    @Test
    @DisplayName("API 118: noAdmin은 임원 없는 동아리만, inactiveOver180Days는 임원이 모두 180일 넘게 비활동인 동아리만")
    void listAdminFilters() throws Exception {
        alpha(); // 임원이 2일 전에 활동 → 제외
        long staleAdmin = user("오래된임원", "stale_admin", false, "ACTIVE", ts(200));
        long stale = club("Stale Club", "CLUB", "ACTIVE", staleAdmin, null);
        member(stale, staleAdmin, "CLUB_ADMIN", "ACTIVE", "REGULAR", "CLUB_CREATOR");
        long noAdminOwner = user("전임", "no_admin_owner", false, "ACTIVE", ts(1));
        long none = club("No Admin Club", "CLUB", "ACTIVE", noAdminOwner, null);
        member(none, noAdminOwner, "MEMBER", "ACTIVE", "REGULAR", "JOIN_CODE");

        mockMvc.perform(get("/api/v1/admin/clubs").param("noAdmin", "true").cookie(access(admin)))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].id").value(String.valueOf(none)))
                .andExpect(jsonPath("$.data.items[0].adminCount").value(0));
        mockMvc.perform(get("/api/v1/admin/clubs")
                        .param("inactiveOver180Days", "true")
                        .cookie(access(admin)))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].id").value(String.valueOf(stale)));
    }

    @Test
    @DisplayName("API 118: 아주 큰 page도 오프셋 오버플로 없이 빈 페이지로 응답한다")
    void hugePage() throws Exception {
        long creator = user("c", "huge_creator", false, "ACTIVE", ts(1));
        club("Only Club", "CLUB", "ACTIVE", creator, null);
        mockMvc.perform(get("/api/v1/admin/clubs")
                        .param("page", String.valueOf(Integer.MAX_VALUE))
                        .param("size", "100")
                        .cookie(access(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(0)))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("API 118: 정렬(기본 createdAt,desc)과 페이지, 잘못된 sort·status는 400")
    void listSortPageAndValidation() throws Exception {
        long creator = user("c", "sort_creator", false, "ACTIVE", ts(1));
        club("B club", "CLUB", "ACTIVE", creator, null);
        club("A club", "CLUB", "ACTIVE", creator, null);
        club("C club", "CLUB", "ACTIVE", creator, null);

        mockMvc.perform(get("/api/v1/admin/clubs").param("sort", "name,asc").cookie(access(admin)))
                .andExpect(jsonPath("$.data.items[0].name").value("A club"))
                .andExpect(jsonPath("$.data.items[2].name").value("C club"));
        mockMvc.perform(get("/api/v1/admin/clubs")
                        .param("sort", "name,desc")
                        .param("page", "1")
                        .param("size", "2")
                        .cookie(access(admin)))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].name").value("A club"))
                .andExpect(jsonPath("$.data.totalPages").value(2));
        for (String[] bad : new String[][] {
            {"sort", "password,asc"}, {"sort", "name,sideways"}, {"status", "ZOMBIE"}, {"clubType", "x"}
        }) {
            mockMvc.perform(get("/api/v1/admin/clubs").param(bad[0], bad[1]).cookie(access(admin)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
    }

    // ---- API 119 ----

    @Test
    @DisplayName("API 119: 임원·집계를 돌려주고 가입 코드 값은 없이 joinCodeEnabled만, CLUB_DETAIL_VIEWED를 기록한다")
    void detail() throws Exception {
        long id = alpha();
        mockMvc.perform(get("/api/v1/admin/clubs/" + id).cookie(access(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.club.name").value("Alpha Band"))
                .andExpect(jsonPath("$.data.creator.name").value("생성자"))
                .andExpect(jsonPath("$.data.joinCodeEnabled").value(true))
                .andExpect(jsonPath("$.data.club.joinCode").doesNotExist())
                .andExpect(jsonPath("$.data.admins", hasSize(1)))
                .andExpect(jsonPath("$.data.admins[0].handle").value("alpha_creator"))
                .andExpect(jsonPath("$.data.admins[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.counts.members").value(3))
                .andExpect(jsonPath("$.data.counts.performances").value(3))
                .andExpect(jsonPath("$.data.counts.memberStatus.active").value(3))
                .andExpect(jsonPath("$.data.counts.memberStatus.onLeave").value(1))
                .andExpect(jsonPath("$.data.counts.memberStatus.graduated").value(1))
                .andExpect(jsonPath("$.data.counts.memberStatus.guests").value(1))
                .andExpect(jsonPath("$.data.counts.performanceStatus.draft").value(1))
                .andExpect(jsonPath("$.data.counts.performanceStatus.active").value(1))
                .andExpect(jsonPath("$.data.counts.performanceStatus.finished").value(1))
                .andExpect(jsonPath("$.data.counts.performanceStatus.locked").value(0))
                .andExpect(jsonPath("$.data.lastActiveAt").exists());
        assertThat(count(
                        "SELECT COUNT(*) FROM audit_log WHERE action = 'CLUB_DETAIL_VIEWED' AND club_id = ? AND"
                                + " actor_user_id = ? AND target_type = 'CLUB'",
                        id,
                        admin))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("API 119: 없는 동아리 404(감사 기록 없음), 생성자가 탈퇴했으면 '탈퇴한 사용자', 가입 코드 미사용이면 false")
    void detailEdges() throws Exception {
        mockMvc.perform(get("/api/v1/admin/clubs/999999").cookie(access(admin)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        assertThat(count("SELECT COUNT(*) FROM audit_log")).isZero();
        mockMvc.perform(get("/api/v1/admin/clubs/abc").cookie(access(admin))).andExpect(status().isBadRequest());

        long gone = user("탈퇴한 사용자", "gone_creator_x", false, "WITHDRAWN", ts(3));
        long id = club("Orphan", "CLUB", "ACTIVE", gone, null);
        mockMvc.perform(get("/api/v1/admin/clubs/" + id).cookie(access(admin)))
                .andExpect(jsonPath("$.data.creator.name").value("탈퇴한 사용자"))
                .andExpect(jsonPath("$.data.joinCodeEnabled").value(false))
                .andExpect(jsonPath("$.data.admins", hasSize(0)))
                .andExpect(jsonPath("$.data.lastActiveAt").doesNotExist());
    }

    // ---- API 141 ----

    @Test
    @DisplayName("API 141: 핵심 지표(탈퇴 제외 사용자·진행 공연·활성 동아리)와 가입 현황·주의 대상을 집계한다")
    void overviewMetrics() throws Exception {
        alpha();
        long staleAdmin = user("오래된임원", "stale_admin", false, "ACTIVE", ts(200));
        long stale = club("Stale Club", "CLUB", "ACTIVE", staleAdmin, null);
        member(stale, staleAdmin, "CLUB_ADMIN", "ACTIVE", "REGULAR", "CLUB_CREATOR");
        long ownerless = user("전임", "ownerless_x", false, "ACTIVE", ts(1));
        long none = club("No Admin Club", "CLUB", "ACTIVE", ownerless, null);
        member(none, ownerless, "MEMBER", "ACTIVE", "REGULAR", "JOIN_CODE");
        long creator = user("c", "inactive_creator", false, "ACTIVE", ts(1));
        club("Closed", "CLUB", "INACTIVE", creator, null); // 비활성은 지표·주의 대상에서 제외
        user("탈퇴자", "gone_user_x", false, "WITHDRAWN", ts(2));

        mockMvc.perform(get("/api/v1/admin/overview").cookie(access(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.periodDays").value(30))
                .andExpect(jsonPath("$.data.metrics.activeClubs").value(3))
                .andExpect(jsonPath("$.data.metrics.ongoingPerformances").value(1))
                .andExpect(jsonPath("$.data.metrics.totalUsers")
                        .value(count("SELECT COUNT(*) FROM users WHERE status <> 'WITHDRAWN'")))
                .andExpect(jsonPath("$.data.attention", hasSize(2)))
                .andExpect(jsonPath("$.data.attention[?(@.reason=='NO_ADMIN')].clubId")
                        .value(String.valueOf(none)))
                .andExpect(jsonPath("$.data.attention[?(@.reason=='ADMIN_INACTIVE_180D')].clubName")
                        .value("Stale Club"))
                .andExpect(jsonPath("$.meta.requestId").exists());
    }

    @Test
    @DisplayName("API 141: 추이는 오늘 포함 periodDays개의 KST 날짜를 0으로 채워 돌려주고 오늘 생성분을 센다")
    void overviewTrend() throws Exception {
        long creator = user("c", "trend_creator", false, "ACTIVE", ts(1));
        long id = club("Trend Club", "CLUB", "ACTIVE", creator, null);
        performance(id, "ACTIVE", creator);
        mockMvc.perform(get("/api/v1/admin/overview").param("periodDays", "7").cookie(access(admin)))
                .andExpect(jsonPath("$.data.periodDays").value(7))
                .andExpect(jsonPath("$.data.trend", hasSize(7)))
                .andExpect(jsonPath("$.data.trend[0].newUsers").value(0));
        // 오늘(KST) 생성분은 마지막 항목에 잡힌다. UTC·KST 날짜 경계에 걸려도 7일 합계로 확인한다
        String body = mockMvc.perform(
                        get("/api/v1/admin/overview").param("periodDays", "7").cookie(access(admin)))
                .andReturn()
                .getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(body).contains("\"newClubs\":1").contains("\"newPerformances\":1");
    }

    @Test
    @DisplayName("API 141: 가입 현황은 membership 행의 현재 상태·시각으로 센다")
    void overviewJoinStatus() throws Exception {
        long creator = user("c", "join_creator", false, "ACTIVE", ts(1));
        long id = club("Join Club", "CLUB", "ACTIVE", creator, null);
        member(id, creator, "CLUB_ADMIN", "ACTIVE", "REGULAR", "CLUB_CREATOR");
        member(id, extraUser(ts(1)), "MEMBER", "ACTIVE", "REGULAR", "JOIN_CODE"); // 신청 + 승인
        member(id, extraUser(ts(1)), "MEMBER", "REJECTED", "REGULAR", "SEARCH"); // 신청 + 거절
        member(id, extraUser(ts(1)), "MEMBER", "INVITED", "REGULAR", "ADMIN_ADDED"); // 초대(미수락, 만료 전)
        member(id, extraUser(ts(1)), "MEMBER", "ACTIVE", "REGULAR", "ADMIN_ADDED"); // 초대 + 수락
        // 초대 후 40일 지난 미수락 건: 유효기간(1개월)이 지난 만료
        long expiredUser = extraUser(ts(1));
        member(id, expiredUser, "MEMBER", "INVITED", "REGULAR", "ADMIN_ADDED");
        jdbc.update(
                "UPDATE membership SET invited_at = DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 40 DAY) WHERE user_id = ?",
                expiredUser);

        mockMvc.perform(get("/api/v1/admin/overview").param("periodDays", "90").cookie(access(admin)))
                .andExpect(jsonPath("$.data.joinStatus.requested").value(2))
                .andExpect(jsonPath("$.data.joinStatus.approved").value(1))
                .andExpect(jsonPath("$.data.joinStatus.rejected").value(1))
                .andExpect(jsonPath("$.data.joinStatus.invited").value(3))
                .andExpect(jsonPath("$.data.joinStatus.invitationAccepted").value(1))
                .andExpect(jsonPath("$.data.joinStatus.invitationExpired").value(1));
    }

    @Test
    @DisplayName("API 141: periodDays는 7·30·90만, 그 밖의 값은 400")
    void overviewPeriodValidation() throws Exception {
        for (String bad : new String[] {"1", "10", "91", "abc"}) {
            mockMvc.perform(get("/api/v1/admin/overview")
                            .param("periodDays", bad)
                            .cookie(access(admin)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
    }

    // ---- API 120·159 ----

    private MockHttpServletRequestBuilder clubStatus(long caller, String path, String body, boolean withHeader) {
        MockHttpServletRequestBuilder req =
                post(path).contentType(MediaType.APPLICATION_JSON).content(body).cookie(access(caller));
        return withHeader ? req.header("X-Requested-With", "XMLHttpRequest") : req;
    }

    private String clubStatusColumn(long clubId) {
        return jdbc.queryForObject("SELECT status FROM club WHERE id = ?", String.class, clubId);
    }

    @Test
    @DisplayName("API 120·159: 비활성·재활성으로 상태를 바꾸고 Club을 돌려주며 사유와 전·후 상태를 감사 기록한다")
    void deactivateAndReactivate() throws Exception {
        long id = alpha();
        String base = "/api/v1/admin/clubs/" + id;

        mockMvc.perform(clubStatus(admin, base + "/deactivate", "{\"reason\":\"  운영 정책 위반  \"}", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(String.valueOf(id)))
                .andExpect(jsonPath("$.data.status").value("INACTIVE"))
                .andExpect(jsonPath("$.data.name").value("Alpha Band"))
                .andExpect(jsonPath("$.data.isSearchable").value(false))
                .andExpect(jsonPath("$.data.joinCode").doesNotExist())
                .andExpect(jsonPath("$.meta.requestId").exists());
        assertThat(clubStatusColumn(id)).isEqualTo("INACTIVE");
        assertThat(jdbc.queryForObject(
                        "SELECT reason FROM audit_log WHERE action = 'CLUB_DEACTIVATED' AND club_id = ? AND"
                                + " actor_user_id = ? AND target_type = 'CLUB' AND target_id = ?",
                        String.class,
                        id,
                        admin,
                        id))
                .isEqualTo("운영 정책 위반");
        assertThat(jdbc.queryForObject(
                        "SELECT JSON_UNQUOTE(JSON_EXTRACT(detail, '$.to')) FROM audit_log WHERE action ="
                                + " 'CLUB_DEACTIVATED'",
                        String.class))
                .isEqualTo("INACTIVE");

        mockMvc.perform(clubStatus(admin, base + "/reactivate", "{\"reason\":\"소명 확인\"}", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        assertThat(clubStatusColumn(id)).isEqualTo("ACTIVE");
        assertThat(count("SELECT COUNT(*) FROM audit_log WHERE action = 'CLUB_REACTIVATED' AND club_id = ?", id))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("API 159: 관리자 0명인 비활성 동아리는 confirmed 없이 409 CONFIRMATION_REQUIRED, confirmed=true면 재활성")
    void reactivateWithoutAdminNeedsConfirmation() throws Exception {
        long creator = user("생성자", "noadmin_creator", false, "ACTIVE", ts(2));
        long id = club("Orphan Band", "BAND", "INACTIVE", creator, "ZYXWVUTS23");
        member(id, creator, "MEMBER", "ACTIVE", "REGULAR", "CLUB_CREATOR");
        String path = "/api/v1/admin/clubs/" + id + "/reactivate";

        mockMvc.perform(clubStatus(admin, path, "{\"reason\":\"복구\"}", true))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFIRMATION_REQUIRED"))
                .andExpect(jsonPath("$.error.warnings[0].type").value("NO_CLUB_ADMIN"));
        assertThat(clubStatusColumn(id)).isEqualTo("INACTIVE");
        assertThat(count("SELECT COUNT(*) FROM audit_log")).isZero();

        mockMvc.perform(clubStatus(admin, path, "{\"reason\":\"복구\",\"confirmed\":true}", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        assertThat(clubStatusColumn(id)).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("API 120·159: 이미 그 상태면 200으로 그대로 돌려주고 감사 기록은 남기지 않는다")
    void alreadyInState() throws Exception {
        long id = alpha();
        String base = "/api/v1/admin/clubs/" + id;
        mockMvc.perform(clubStatus(admin, base + "/reactivate", "{\"reason\":\"확인\"}", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        mockMvc.perform(clubStatus(admin, base + "/deactivate", "{\"reason\":\"1\"}", true))
                .andExpect(status().isOk());
        mockMvc.perform(clubStatus(admin, base + "/deactivate", "{\"reason\":\"2\"}", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INACTIVE"));

        assertThat(count("SELECT COUNT(*) FROM audit_log WHERE action = 'CLUB_REACTIVATED'"))
                .isZero();
        assertThat(count("SELECT COUNT(*) FROM audit_log WHERE action = 'CLUB_DEACTIVATED'"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("API 120·159: 사유가 없거나 비었거나 500자를 넘으면 400, 없는 동아리 404, 변경은 일어나지 않는다")
    void statusChangeValidation() throws Exception {
        long id = alpha();
        String path = "/api/v1/admin/clubs/" + id + "/deactivate";
        for (String body : new String[] {
            "{}",
            "{\"reason\":null}",
            "{\"reason\":\"   \"}",
            "{\"reason\":\"\u2003\u2003\"}",
            "{\"reason\":\"" + "가".repeat(501) + "\"}"
        }) {
            mockMvc.perform(clubStatus(admin, path, body, true))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
        mockMvc.perform(clubStatus(admin, "/api/v1/admin/clubs/999999/deactivate", "{\"reason\":\"x\"}", true))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        mockMvc.perform(clubStatus(admin, "/api/v1/admin/clubs/abc/deactivate", "{\"reason\":\"x\"}", true))
                .andExpect(status().isBadRequest());
        assertThat(clubStatusColumn(id)).isEqualTo("ACTIVE");
        assertThat(count("SELECT COUNT(*) FROM audit_log")).isZero();
    }

    @Test
    @DisplayName("API 120·159: Super Admin만, X-Requested-With 필수(없으면 403), 비로그인 401")
    void statusChangeGuards() throws Exception {
        long id = alpha();
        String path = "/api/v1/admin/clubs/" + id + "/deactivate";
        mockMvc.perform(clubStatus(normal, path, "{\"reason\":\"x\"}", true)).andExpect(status().isForbidden());
        mockMvc.perform(clubStatus(admin, path, "{\"reason\":\"x\"}", false)).andExpect(status().isForbidden());
        mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"x\"}")
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized());
        assertThat(clubStatusColumn(id)).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("API 120: 비활성 전환은 118 목록의 status 필터에 바로 반영된다")
    void deactivateShowsInList() throws Exception {
        long id = alpha();
        mockMvc.perform(clubStatus(admin, "/api/v1/admin/clubs/" + id + "/deactivate", "{\"reason\":\"x\"}", true))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/clubs").param("status", "INACTIVE").cookie(access(admin)))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].id").value(String.valueOf(id)));
    }
}
