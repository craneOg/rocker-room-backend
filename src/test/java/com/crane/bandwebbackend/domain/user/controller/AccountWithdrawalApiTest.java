package com.crane.bandwebbackend.domain.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crane.bandwebbackend.TestcontainersConfiguration;
import com.crane.bandwebbackend.global.security.CookieFactory;
import com.crane.bandwebbackend.global.security.JwtProvider;
import jakarta.servlet.http.Cookie;
import java.util.List;
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
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// API 134 탈퇴 미리보기, API 009 서비스 탈퇴. 시드: 사용자 1(임원)·2·3(부원), 동아리 1, 공연 1(ACTIVE), 참여 2
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Sql({"/performance-flow-cleanup.sql", "/performance-flow-seed.sql"})
@Sql(scripts = "/performance-flow-cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class AccountWithdrawalApiTest {

    private static final String PREVIEW = "/api/v1/me/withdrawal-preview";
    private static final String WITHDRAW = "/api/v1/me/withdrawal";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JwtProvider jwtProvider;

    @Autowired
    CookieFactory cookies;

    @BeforeEach
    void setUp() {
        clean();
        // 승인일 순서 확인용: 사용자 3이 먼저 승인됐다
        jdbc.update("UPDATE membership SET approved_at = '2026-01-01 00:00:00' WHERE id = 3");
        jdbc.update("UPDATE membership SET approved_at = '2026-02-01 00:00:00' WHERE id = 2");
        jdbc.update("UPDATE membership SET approved_at = '2025-12-01 00:00:00' WHERE id = 1");
    }

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM user_withdrawal_archive");
        jdbc.update("DELETE FROM refresh_token");
        jdbc.update("DELETE FROM one_time_token");
    }

    // ---- 도우미 ----

    private Cookie access(long userId) {
        return new Cookie(
                cookies.accessName(),
                jwtProvider.createAccessToken(userId, "sid-1", 0).getToken());
    }

    private ResultActions preview(long userId) throws Exception {
        return mockMvc.perform(get(PREVIEW).cookie(access(userId)));
    }

    private ResultActions withdraw(long userId, String body) throws Exception {
        return mockMvc.perform(post(WITHDRAW)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("X-Requested-With", "XMLHttpRequest")
                .cookie(access(userId)));
    }

    private ResultActions confirm(long userId, String extra) throws Exception {
        return withdraw(userId, "{\"confirmed\":true" + extra + "}");
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private String value(String sql, Object... args) {
        return jdbc.queryForObject(sql, String.class, args);
    }

    // 공연 1에 사용자 2가 팀장인 팀을 만든다. 팀원은 참여 3(사용자 3)
    private long team(String name, String status) {
        jdbc.update("INSERT IGNORE INTO performance_member (id, performance_id, club_id, membership_id, status, source)"
                + " VALUES (3, 1, 1, 3, 'APPROVED', 'ADMIN')");
        jdbc.update(
                "INSERT INTO team (performance_id, name, status, source, created_by_user_id) VALUES (1, ?, ?, 'ADMIN', 1)",
                name,
                status);
        long team = jdbc.queryForObject("SELECT MAX(id) FROM team", Long.class);
        jdbc.update(
                "INSERT INTO team_member (team_id, performance_id, performance_member_id) VALUES (?, 1, 2), (?, 1, 3)",
                team,
                team);
        jdbc.update("UPDATE team SET leader_member_id = 2 WHERE id = ?", team);
        return team;
    }

    // 임원 1과 부원 3이 있는 두 번째 동아리
    private void secondClub() {
        jdbc.update("INSERT INTO club (id, name, created_by) VALUES (2, '두번째밴드', 1)");
        jdbc.update("INSERT INTO membership (id, club_id, user_id, role, status, join_source, approved_at) VALUES"
                + " (11, 2, 1, 'CLUB_ADMIN', 'ACTIVE', 'CLUB_CREATOR', '2025-12-01 00:00:00'),"
                + " (13, 2, 3, 'MEMBER', 'ACTIVE', 'JOIN_CODE', '2026-01-01 00:00:00')");
    }

    // ---- API 134 ----

    @Test
    @DisplayName("API 134: 마지막 임원은 후보를 승인일 순으로 받고 ADMIN_SUCCESSION 경고를 받으며 데이터는 바뀌지 않는다")
    void previewAdminWithCandidates() throws Exception {
        preview(1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.clubs", hasSize(1)))
                .andExpect(jsonPath("$.data.clubs[0].clubId").value("1"))
                .andExpect(jsonPath("$.data.clubs[0].clubName").value("테스트밴드"))
                .andExpect(jsonPath("$.data.clubs[0].role").value("CLUB_ADMIN"))
                .andExpect(jsonPath("$.data.clubs[0].succession.type").value("DESIGNATED_OR_EARLIEST"))
                .andExpect(jsonPath("$.data.clubs[0].succession.candidateMembershipId")
                        .value("3"))
                .andExpect(
                        jsonPath("$.data.clubs[0].succession.candidateUserId").value("3"))
                .andExpect(jsonPath("$.data.clubs[0].succession.candidateName").value("부원B"))
                .andExpect(jsonPath("$.data.clubs[0].succession.candidates", hasSize(2)))
                .andExpect(jsonPath("$.data.clubs[0].succession.candidates[1].membershipId")
                        .value("2"))
                .andExpect(jsonPath("$.data.warnings", hasSize(1)))
                .andExpect(jsonPath("$.data.warnings[0].type").value("ADMIN_SUCCESSION"))
                .andExpect(jsonPath("$.data.warnings[0].clubId").value("1"))
                .andExpect(jsonPath("$.data.warnings[0].successor.membershipId").value("3"))
                .andExpect(jsonPath("$.data.warnings[0].successor.userId").value("3"))
                // 해당 없는 키도 null로 존재한다
                .andExpect(jsonPath("$.data.warnings[0].teamId").value(nullValue()))
                .andExpect(jsonPath("$.data.warnings[0].performanceName").value(nullValue()));

        assertThat(value("SELECT status FROM users WHERE id = 1")).isEqualTo("ACTIVE");
        assertThat(value("SELECT status FROM membership WHERE id = 1")).isEqualTo("ACTIVE");
        assertThat(count("SELECT COUNT(*) FROM audit_log")).isZero();
    }

    @Test
    @DisplayName("API 134: 후보가 없으면 NONE과 CLUB_DEACTIVATED, 다른 임원이 있거나 일반 부원이면 NOT_REQUIRED")
    void previewNoneAndNotRequired() throws Exception {
        jdbc.update("UPDATE membership SET member_type = 'GUEST' WHERE id IN (2, 3)");
        preview(1)
                .andExpect(jsonPath("$.data.clubs[0].succession.type").value("NONE"))
                .andExpect(jsonPath("$.data.clubs[0].succession.candidateMembershipId")
                        .value(nullValue()))
                .andExpect(jsonPath("$.data.clubs[0].succession.candidates", hasSize(0)))
                .andExpect(jsonPath("$.data.warnings[0].type").value("CLUB_DEACTIVATED"))
                .andExpect(jsonPath("$.data.warnings[0].successor").value(nullValue()));

        jdbc.update("UPDATE membership SET member_type = 'REGULAR' WHERE id IN (2, 3)");
        jdbc.update("UPDATE membership SET role = 'CLUB_ADMIN' WHERE id = 2");
        preview(1)
                .andExpect(jsonPath("$.data.clubs[0].succession.type").value("NOT_REQUIRED"))
                .andExpect(jsonPath("$.data.warnings", hasSize(0)));
        preview(3)
                .andExpect(jsonPath("$.data.clubs[0].role").value("MEMBER"))
                .andExpect(jsonPath("$.data.clubs[0].succession.type").value("NOT_REQUIRED"))
                .andExpect(jsonPath("$.data.clubs[0].teamLeaderTransfers", hasSize(0)));
    }

    @Test
    @DisplayName("API 134: 팀장인 APPROVED·SUBMITTED·DRAFT 팀은 경고 유형·기본 후임이 다르고 REJECTED 팀·종료 공연 팀은 뺀다")
    void previewTeams() throws Exception {
        team("승인팀", "APPROVED");
        team("제출팀", "SUBMITTED");
        team("초안팀", "DRAFT");
        team("반려팀", "REJECTED");
        jdbc.update(
                "INSERT INTO performance (id, club_id, title, preset, status, created_by_user_id) VALUES (2, 1, '지난공연', 'CUSTOM', 'FINISHED', 1)");
        jdbc.update(
                "INSERT INTO performance_member (id, performance_id, club_id, membership_id, status, source) VALUES (9, 2, 1, 2, 'APPROVED', 'ADMIN')");
        jdbc.update("INSERT INTO team (performance_id, name, status, source, created_by_user_id)"
                + " VALUES (2, '지난팀', 'APPROVED', 'ADMIN', 1)");
        long old = jdbc.queryForObject("SELECT MAX(id) FROM team", Long.class);
        jdbc.update("INSERT INTO team_member (team_id, performance_id, performance_member_id) VALUES (?, 2, 9)", old);
        jdbc.update("UPDATE team SET leader_member_id = 9 WHERE id = ?", old);

        preview(2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.clubs[0].teamLeaderTransfers", hasSize(3)))
                .andExpect(jsonPath("$.data.clubs[0].teamLeaderTransfers[0].teamName")
                        .value("승인팀"))
                .andExpect(jsonPath("$.data.clubs[0].teamLeaderTransfers[0].teamStatus")
                        .value("APPROVED"))
                .andExpect(jsonPath("$.data.clubs[0].teamLeaderTransfers[0].performanceName")
                        .value("정기공연"))
                .andExpect(jsonPath("$.data.clubs[0].teamLeaderTransfers[0].defaultMembershipId")
                        .value("3"))
                .andExpect(jsonPath("$.data.clubs[0].teamLeaderTransfers[0].candidates[0].membershipId")
                        .value("3"))
                .andExpect(jsonPath("$.data.clubs[0].teamLeaderTransfers[0].candidates[0].displayName")
                        .value("부원B"))
                .andExpect(jsonPath("$.data.clubs[0].teamLeaderTransfers[2].teamStatus")
                        .value("DRAFT"))
                .andExpect(jsonPath("$.data.clubs[0].teamLeaderTransfers[2].defaultMembershipId")
                        .value(nullValue()))
                .andExpect(jsonPath("$.data.clubs[0].teamLeaderTransfers[2].candidates", hasSize(0)))
                .andExpect(jsonPath("$.data.warnings[0].type").value("TEAM_LEADER_TRANSFER"))
                .andExpect(jsonPath("$.data.warnings[0].teamName").value("승인팀"))
                .andExpect(jsonPath("$.data.warnings[0].performanceId").value("1"))
                .andExpect(jsonPath("$.data.warnings[0].successor.membershipId").value("3"))
                .andExpect(jsonPath("$.data.warnings[1].type").value("TEAM_AUTO_SUCCESSION"))
                .andExpect(jsonPath("$.data.warnings[2].type").value("TEAM_DELETED"))
                .andExpect(jsonPath("$.data.warnings[2].successor").value(nullValue()))
                .andExpect(jsonPath("$.data.warnings", hasSize(3)));
    }

    @Test
    @DisplayName("API 134: 인증이 없으면 401")
    void previewUnauthenticated() throws Exception {
        mockMvc.perform(get(PREVIEW)).andExpect(status().isUnauthorized());
    }

    // ---- API 009 확인·검증 ----

    @Test
    @DisplayName("API 009: confirmed가 없거나 false면 409 CONFIRMATION_REQUIRED와 134와 같은 warnings를 돌려주고 아무것도 바꾸지 않는다")
    void confirmationRequired() throws Exception {
        for (String body : List.of("{}", "{\"confirmed\":false}")) {
            withdraw(1, body)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("CONFIRMATION_REQUIRED"))
                    .andExpect(jsonPath("$.error.details[0].reason").value("WITHDRAWAL_CONFIRMATION_REQUIRED"))
                    .andExpect(jsonPath("$.error.warnings[0].type").value("ADMIN_SUCCESSION"))
                    .andExpect(jsonPath("$.error.warnings[0].successor.membershipId")
                            .value("3"))
                    .andExpect(jsonPath("$.error.warnings[0].teamId").value(nullValue()));
        }
        assertThat(value("SELECT status FROM users WHERE id = 1")).isEqualTo("ACTIVE");
        assertThat(count("SELECT COUNT(*) FROM user_withdrawal_archive")).isZero();
    }

    @Test
    @DisplayName("API 009: 명시적 null·잘못된 값·중복은 400 VALIDATION_ERROR")
    void validation() throws Exception {
        withdraw(1, "{\"successors\":null}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("successors"));
        withdraw(1, "{\"teamLeaderSuccessors\":null}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("teamLeaderSuccessors"));
        withdraw(1, "{\"confirmed\":null}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("confirmed"));
        withdraw(1, "{\"reason\":\"  \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("reason"));
        withdraw(1, "{\"reason\":\"" + "가".repeat(501) + "\"}").andExpect(status().isBadRequest());
        withdraw(1, "{\"successors\":[{\"clubId\":\"1\",\"userId\":\"2\"},{\"clubId\":\"1\",\"userId\":\"3\"}]}")
                .andExpect(status().isBadRequest());
        withdraw(
                        1,
                        "{\"teamLeaderSuccessors\":[{\"teamId\":\"1\",\"membershipId\":\"2\"},{\"teamId\":\"1\",\"membershipId\":\"3\"}]}")
                .andExpect(status().isBadRequest());
        withdraw(1, "{\"successors\":[{\"clubId\":\"abc\",\"userId\":\"2\"}]}").andExpect(status().isBadRequest());
        assertThat(value("SELECT status FROM users WHERE id = 1")).isEqualTo("ACTIVE");
    }

    // ---- API 009 실행 ----

    @Test
    @DisplayName("API 009: 일반 부원은 계정·소속·보관·토큰·감사가 모두 정리되고 쿠키가 지워지며 같은 토큰은 401")
    void withdrawMember() throws Exception {
        jdbc.update("UPDATE users SET profile_image_url = 'https://cdn.example/p.png' WHERE id = 2");
        jdbc.update("UPDATE membership SET display_name = '별명' WHERE id = 2");
        // 다른 동아리의 PENDING·INVITED 소속
        jdbc.update("INSERT INTO club (id, name, created_by) VALUES (2, '두번째밴드', 1), (3, '세번째밴드', 1)");
        jdbc.update("INSERT INTO membership (id, club_id, user_id, role, status, join_source, invited_at) VALUES"
                + " (12, 2, 2, 'MEMBER', 'PENDING', 'JOIN_CODE', NULL),"
                + " (13, 3, 2, 'MEMBER', 'INVITED', 'ADMIN_ADDED', UTC_TIMESTAMP(6))");
        jdbc.update("INSERT INTO refresh_token (user_id, family_id, token_hash, expires_at, family_expires_at)"
                + " VALUES (2, UUID(), UNHEX(SHA2('r1', 256)), '2099-01-01', '2099-01-01')");
        jdbc.update("INSERT INTO one_time_token (token_hash, purpose, user_id, expires_at)"
                + " VALUES (UNHEX(SHA2('o1', 256)), 'LOGIN_EXCHANGE', 2, '2099-01-01')");

        confirm(2, ",\"reason\":\"그만둘게요\"")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.successions.clubs", hasSize(0)))
                .andExpect(jsonPath("$.data.successions.teams", hasSize(0)))
                .andExpect(header().stringValues(
                                "Set-Cookie",
                                org.hamcrest.Matchers.hasItems(
                                        org.hamcrest.Matchers.containsString(cookies.accessName() + "=;"),
                                        org.hamcrest.Matchers.containsString(cookies.refreshName() + "=;"))));

        var user = jdbc.queryForMap("SELECT name, handle, profile_image_url, status, withdrawn_at, token_version"
                + " FROM users WHERE id = 2");
        assertThat(user)
                .containsEntry("name", "탈퇴한 사용자")
                .containsEntry("handle", null)
                .containsEntry("profile_image_url", null)
                .containsEntry("status", "WITHDRAWN")
                .containsEntry("token_version", 1);
        assertThat(user.get("withdrawn_at")).isNotNull();
        assertThat(jdbc.queryForMap("SELECT name, profile_image_url FROM user_withdrawal_archive WHERE user_id = 2"))
                .containsEntry("name", "부원A")
                .containsEntry("profile_image_url", "https://cdn.example/p.png");
        assertThat(value("SELECT status FROM membership WHERE id = 2")).isEqualTo("WITHDRAWN");
        assertThat(value("SELECT role FROM membership WHERE id = 2")).isEqualTo("MEMBER");
        assertThat(count("SELECT COUNT(*) FROM membership WHERE user_id = 2 AND display_name IS NOT NULL"))
                .isZero();
        assertThat(value("SELECT status FROM membership WHERE id = 12")).isEqualTo("WITHDRAWN");
        assertThat(value("SELECT status FROM membership WHERE id = 13")).isEqualTo("DECLINED");
        assertThat(value("SELECT status FROM performance_member WHERE id = 2")).isEqualTo("WITHDRAWN");
        assertThat(value("SELECT revoke_reason FROM refresh_token WHERE user_id = 2"))
                .isEqualTo("WITHDRAWN");
        assertThat(count("SELECT COUNT(*) FROM one_time_token WHERE user_id = 2"))
                .isZero();
        assertThat(count("SELECT COUNT(*) FROM audit_log WHERE action = 'ACCOUNT_WITHDRAWN' AND target_id = 2"
                        + " AND reason = '그만둘게요' AND club_id IS NULL"))
                .isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM audit_log WHERE action = 'MEMBERSHIP_WITHDRAWN' AND target_id = 2"))
                .isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM audit_log WHERE action = 'PERFORMANCE_MEMBER_WITHDRAWN'"))
                .isEqualTo(1);
        // token_version이 올라가 같은 Access Token은 더 이상 쓸 수 없다
        mockMvc.perform(get(PREVIEW).cookie(access(2))).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("API 009: 마지막 임원은 기본 후임(승인일 순)에게 승계하고 안내·감사를 남기며 응답에 담는다")
    void withdrawAdminDefaultSuccessor() throws Exception {
        confirm(1, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.successions.clubs", hasSize(1)))
                .andExpect(jsonPath("$.data.successions.clubs[0].clubId").value("1"))
                .andExpect(
                        jsonPath("$.data.successions.clubs[0].successorUserId").value("3"));

        assertThat(value("SELECT role FROM membership WHERE id = 3")).isEqualTo("CLUB_ADMIN");
        assertThat(count("SELECT COUNT(*) FROM succession_notice WHERE user_id = 3 AND notice_type = 'ADMIN'"))
                .isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM audit_log WHERE action = 'ADMIN_SUCCESSION' AND target_id = 3"))
                .isEqualTo(1);
        assertThat(value("SELECT status FROM club WHERE id = 1")).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("API 009: successors로 지정한 사용자에게 승계한다. 자격 없는 사용자·모르는 사용자는 422이고 전부 롤백한다")
    void withdrawAdminDesignatedSuccessor() throws Exception {
        confirm(1, ",\"successors\":[{\"clubId\":\"1\",\"userId\":\"2\"}]")
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.data.successions.clubs[0].successorUserId").value("2"));
        assertThat(value("SELECT role FROM membership WHERE id = 2")).isEqualTo("CLUB_ADMIN");
        assertThat(value("SELECT role FROM membership WHERE id = 3")).isEqualTo("MEMBER");
    }

    @Test
    @DisplayName("API 009: 후임 지정이 자격 없음(탈퇴한 소속·타 동아리·불필요한 동아리)이면 422 SUCCESSOR_NOT_ELIGIBLE이고 사용자는 그대로")
    void successorNotEligible() throws Exception {
        // 4는 이미 탈퇴한 소속, 99는 소속 없음
        for (String userId : List.of("4", "99")) {
            confirm(1, ",\"successors\":[{\"clubId\":\"1\",\"userId\":\"" + userId + "\"}]")
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("RULE_VIOLATION"))
                    .andExpect(jsonPath("$.error.details[0].reason").value("SUCCESSOR_NOT_ELIGIBLE"));
        }
        // 소속이 없는 동아리 지정
        confirm(1, ",\"successors\":[{\"clubId\":\"77\",\"userId\":\"2\"}]")
                .andExpect(status().isUnprocessableEntity());
        // 승계가 필요 없는 동아리(임원이 아니라서) 지정
        confirm(2, ",\"successors\":[{\"clubId\":\"1\",\"userId\":\"3\"}]").andExpect(status().isUnprocessableEntity());

        assertThat(value("SELECT status FROM users WHERE id = 1")).isEqualTo("ACTIVE");
        assertThat(value("SELECT status FROM membership WHERE id = 1")).isEqualTo("ACTIVE");
        assertThat(value("SELECT status FROM users WHERE id = 2")).isEqualTo("ACTIVE");
        assertThat(count("SELECT COUNT(*) FROM audit_log")).isZero();
        assertThat(count("SELECT COUNT(*) FROM user_withdrawal_archive")).isZero();
    }

    @Test
    @DisplayName("API 009: 후임 후보가 없으면 동아리를 비활성화하고 CLUB_DEACTIVATED 감사를 남기며 응답 clubs에는 넣지 않는다")
    void withdrawAdminNoCandidate() throws Exception {
        jdbc.update("UPDATE membership SET member_type = 'GUEST' WHERE id IN (2, 3)");
        confirm(1, "").andExpect(status().isOk()).andExpect(jsonPath("$.data.successions.clubs", hasSize(0)));
        assertThat(value("SELECT status FROM club WHERE id = 1")).isEqualTo("INACTIVE");
        assertThat(count("SELECT COUNT(*) FROM audit_log WHERE action = 'CLUB_DEACTIVATED' AND target_id = 1"))
                .isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM succession_notice")).isZero();
    }

    @Test
    @DisplayName("API 009: 동아리가 둘이면 동아리마다 승계하고 팀장 지정도 동아리별로 나눠 적용한다")
    void withdrawTwoClubs() throws Exception {
        secondClub();
        long team = team("승인팀", "APPROVED");
        confirm(1, ",\"successors\":[{\"clubId\":\"2\",\"userId\":\"3\"}]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.successions.clubs", hasSize(2)))
                .andExpect(jsonPath("$.data.successions.clubs[0].clubId").value("1"))
                .andExpect(
                        jsonPath("$.data.successions.clubs[0].successorUserId").value("3"))
                .andExpect(jsonPath("$.data.successions.clubs[1].clubId").value("2"))
                .andExpect(
                        jsonPath("$.data.successions.clubs[1].successorUserId").value("3"));
        assertThat(value("SELECT role FROM membership WHERE id = 13")).isEqualTo("CLUB_ADMIN");
        assertThat(value("SELECT status FROM membership WHERE id IN (1, 11) GROUP BY status"))
                .isEqualTo("WITHDRAWN");
        // 사용자 1은 팀장이 아니라 팀(사용자 2가 팀장)은 그대로
        assertThat(count("SELECT leader_member_id FROM team WHERE id = ?", team))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("API 009: 팀장은 지정 후임·자동 후임으로 승계하고 DRAFT 팀은 삭제하며 응답 teams에는 후임이 정해진 팀만 담는다")
    void withdrawTeamLeader() throws Exception {
        long approved = team("승인팀", "APPROVED");
        long submitted = team("제출팀", "SUBMITTED");
        long draft = team("초안팀", "DRAFT");

        confirm(2, ",\"teamLeaderSuccessors\":[{\"teamId\":\"" + approved + "\",\"membershipId\":\"3\"}]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.successions.teams", hasSize(2)))
                .andExpect(jsonPath("$.data.successions.teams[0].teamId").value(String.valueOf(approved)))
                .andExpect(jsonPath("$.data.successions.teams[0].newLeaderMembershipId")
                        .value("3"))
                .andExpect(jsonPath("$.data.successions.teams[1].teamId").value(String.valueOf(submitted)));

        assertThat(count("SELECT leader_member_id FROM team WHERE id = ?", approved))
                .isEqualTo(3);
        assertThat(count("SELECT leader_member_id FROM team WHERE id = ?", submitted))
                .isEqualTo(3);
        assertThat(count("SELECT COUNT(*) FROM team WHERE id = ?", draft)).isZero();
        assertThat(count("SELECT COUNT(*) FROM succession_notice WHERE user_id = 3 AND notice_type = 'TEAM_LEADER'"))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("API 009: 본인이 팀장이 아닌 팀을 지정하면 422이고 전부 롤백한다")
    void teamNotLedByMe() throws Exception {
        long team = team("승인팀", "APPROVED");
        // 사용자 3은 팀원일 뿐 팀장이 아니다
        confirm(3, ",\"teamLeaderSuccessors\":[{\"teamId\":\"" + team + "\",\"membershipId\":\"2\"}]")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.details[0].reason").value("SUCCESSOR_NOT_ELIGIBLE"));
        assertThat(value("SELECT status FROM users WHERE id = 3")).isEqualTo("ACTIVE");
        assertThat(value("SELECT status FROM membership WHERE id = 3")).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("API 009: 비활성 동아리 소속도 탈퇴하고 공연 참여를 철회한다(임원 승계는 하지 않는다)")
    void inactiveClub() throws Exception {
        jdbc.update("UPDATE club SET status = 'INACTIVE' WHERE id = 1");
        confirm(2, "").andExpect(status().isOk());
        assertThat(value("SELECT status FROM membership WHERE id = 2")).isEqualTo("WITHDRAWN");
        assertThat(value("SELECT status FROM performance_member WHERE id = 2")).isEqualTo("WITHDRAWN");

        // 비활성 동아리의 임원이어도 승계 없이 탈퇴한다
        preview(1)
                .andExpect(jsonPath("$.data.clubs[0].succession.type").value("NOT_REQUIRED"))
                .andExpect(jsonPath("$.data.warnings", hasSize(0)));
        confirm(1, "").andExpect(status().isOk());
        assertThat(value("SELECT status FROM membership WHERE id = 1")).isEqualTo("WITHDRAWN");
        assertThat(count("SELECT COUNT(*) FROM audit_log WHERE action = 'ADMIN_SUCCESSION'"))
                .isZero();
    }

    @Test
    @DisplayName("API 009: LOCKED 공연 참여도 사유 없이 탈퇴할 수 있고 LOCKED_EXCEPTION_EDIT 감사만 남긴다")
    void lockedPerformance() throws Exception {
        jdbc.update("UPDATE performance SET status = 'LOCKED' WHERE id = 1");
        confirm(2, "").andExpect(status().isOk());
        assertThat(value("SELECT status FROM performance_member WHERE id = 2")).isEqualTo("WITHDRAWN");
        assertThat(count("SELECT COUNT(*) FROM audit_log WHERE action = 'LOCKED_EXCEPTION_EDIT'"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("API 009: 탈퇴한 사용자가 다시 호출하면 401이고 본문을 생략해도 확인 요청(409)으로 본다")
    void withdrawTwiceAndEmptyBody() throws Exception {
        mockMvc.perform(post(WITHDRAW)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(access(2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFIRMATION_REQUIRED"));
        confirm(2, "").andExpect(status().isOk());
        confirm(2, "").andExpect(status().isUnauthorized());
    }
}
