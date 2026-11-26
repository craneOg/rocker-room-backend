package com.crane.bandwebbackend.domain.membership.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crane.bandwebbackend.TestcontainersConfiguration;
import com.crane.bandwebbackend.global.security.AuthPrincipal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * 명세 정합성 회귀(#97 API-021, #110 API-029, #113 API-020, #132 API-142·143, #129·#137 API-142·023).
 * 시드: 1 = 임원, 2·3 = 부원(2는 공연 1 승인), 4 = 탈퇴자. 추가 데이터는 5 이상 id를 쓴다.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Sql({"/performance-flow-cleanup.sql", "/performance-flow-seed.sql"})
@Sql(scripts = "/performance-flow-cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class MembershipSpecAlignmentApiTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("member-01 API-021: 임원·본인·부원 응답 모두 performances가 있고 DRAFT 공연은 임원에게만 보인다")
    void detailHasPerformances() throws Exception {
        draftPerformanceWithMember2();
        // 임원: 공연 1(ACTIVE) + 20(DRAFT)
        read(1, "/memberships/2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.performances.length()").value(2))
                .andExpect(jsonPath("$.data.performances[0].id").value("1"))
                .andExpect(jsonPath("$.data.performances[0].title").value("정기공연"))
                .andExpect(jsonPath("$.data.performances[0].status").value("ACTIVE"))
                .andExpect(
                        jsonPath("$.data.performances[0].participationStatus").value("APPROVED"))
                .andExpect(jsonPath("$.data.performances[1].status").value("DRAFT"));
        // 본인(부원): DRAFT 제외
        read(2, "/memberships/2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.performances.length()").value(1))
                .andExpect(jsonPath("$.data.performances[0].id").value("1"));
        // 다른 부원(공개 응답): DRAFT 제외, 참여가 없으면 빈 배열
        read(3, "/memberships/2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.performances.length()").value(1));
        read(3, "/memberships/3")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.performances.length()").value(0));
        read(1, "/memberships/3")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.performances.length()").value(0));
    }

    @Test
    @DisplayName("member-01 API-021: 객원 본인 상세는 REJECTED·WITHDRAWN 참여를 제외하고 신청·승인 공연만 담는다")
    void guestSelfDetailExcludesRejectedAndWithdrawn() throws Exception {
        jdbc.update("INSERT INTO users (id, name, handle) VALUES (5, '객원', 'guest_5')");
        jdbc.update("INSERT INTO membership (id, club_id, user_id, member_type, role, status, join_source)"
                + " VALUES (5, 1, 5, 'GUEST', 'MEMBER', 'ACTIVE', 'JOIN_CODE')");
        for (int id : new int[] {21, 22}) {
            jdbc.update(
                    "INSERT INTO performance (id, club_id, title, preset, status, created_by_user_id)"
                            + " VALUES (?, 1, '공연', 'CUSTOM', 'ACTIVE', 1)",
                    id);
            jdbc.update(
                    "INSERT INTO performance_policy (performance_id, candidate_state, playability_state,"
                            + " vote_state, vote_method, vote_max_choices, team_submission_state, availability_state)"
                            + " VALUES (?, 'OPEN', 'OPEN', 'OPEN', 'APPROVAL', 1, 'OPEN', 'OPEN')",
                    id);
        }
        jdbc.update("INSERT INTO performance_member (id, performance_id, club_id, membership_id, status, source) VALUES"
                + " (31, 1, 1, 5, 'REJECTED', 'APPLICATION'),"
                + " (32, 21, 1, 5, 'WITHDRAWN', 'APPLICATION'),"
                + " (33, 22, 1, 5, 'APPROVED', 'APPLICATION')");
        read(5, "/memberships/5")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.performances.length()").value(1))
                .andExpect(jsonPath("$.data.performances[0].id").value("22"));
    }

    @Test
    @DisplayName("G-05 API-021: 탈퇴한 본인의 상세는 프로필만 주고 performances는 빈 배열이다")
    void withdrawnSelfDetailHasNoPerformances() throws Exception {
        jdbc.update("INSERT INTO performance_member (id, performance_id, club_id, membership_id, status, source)"
                + " VALUES (34, 1, 1, 4, 'APPROVED', 'APPLICATION')");
        read(4, "/memberships/4")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.performances.length()").value(0));
    }

    @Test
    @DisplayName("G-05 API-029: 탈퇴·가입 대기·초대 대기 본인의 프로필 수정은 403, 활동 중이면 200")
    void displayNameRequiresValidMembership() throws Exception {
        jdbc.update("INSERT INTO users (id, name, handle) VALUES (5, '대기', 'pending_5'), (6, '초대', 'invited_6')");
        jdbc.update("INSERT INTO membership (id, club_id, user_id, role, status, join_source, invited_at) VALUES"
                + " (5, 1, 5, 'MEMBER', 'PENDING', 'JOIN_CODE', NULL),"
                + " (6, 1, 6, 'MEMBER', 'INVITED', 'JOIN_CODE', UTC_TIMESTAMP(6))");
        for (long membershipId : new long[] {4, 5, 6}) {
            patchName(membershipId, "새 이름").andExpect(status().isForbidden());
        }
        patchName(2, "새 이름").andExpect(status().isOk());
    }

    @Test
    @DisplayName("member-02 API-020: 탈퇴 필터는 가입 확정일이 있는 소속만 포함하고 totalElements도 같다")
    void withdrawnFilterExcludesCanceledApplicants() throws Exception {
        jdbc.update("UPDATE membership SET approved_at = '2026-10-01 00:00:00' WHERE id = 4");
        jdbc.update("INSERT INTO users (id, name, handle) VALUES (5, '취소자', 'canceled_5')");
        jdbc.update("INSERT INTO membership (id, club_id, user_id, role, status, join_source)"
                + " VALUES (5, 1, 5, 'MEMBER', 'WITHDRAWN', 'JOIN_CODE')");
        mvc.perform(MockMvcRequestBuilders.get("/api/v1/clubs/1/memberships?status=WITHDRAWN")
                        .with(authentication(actor(1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value("4"));
    }

    @Test
    @DisplayName("G-08 API-143: 비활성 동아리의 일괄 승인·거절은 항목별 실패가 아니라 요청 전체가 409 CLUB_INACTIVE")
    void joinBulkOnInactiveClub() throws Exception {
        jdbc.update("INSERT INTO users (id, name, handle) VALUES (5, '대기', 'pending_5')");
        jdbc.update("INSERT INTO membership (id, club_id, user_id, role, status, join_source)"
                + " VALUES (5, 1, 5, 'MEMBER', 'PENDING', 'JOIN_CODE')");
        jdbc.update("UPDATE club SET status = 'INACTIVE' WHERE id = 1");
        send(1, "/join-requests/bulk-decisions", "{\"membershipIds\":[\"5\"],\"decision\":\"APPROVE\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("STATE_CONFLICT"))
                .andExpect(jsonPath("$.error.details[0].reason").value("CLUB_INACTIVE"));
        assertThat(jdbc.queryForObject("SELECT status FROM membership WHERE id = 5", String.class))
                .isEqualTo("PENDING");
    }

    @Test
    @DisplayName("G-08 API-142: 비활성 동아리의 일괄 파트 변경은 409 CLUB_INACTIVE이고 저장되지 않는다")
    void memberBulkOnInactiveClub() throws Exception {
        jdbc.update("UPDATE club SET status = 'INACTIVE' WHERE id = 1");
        send(1, "/memberships/bulk", partsBody("ADD", "2"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.details[0].reason").value("CLUB_INACTIVE"));
        assertThat(heldParts(2)).containsExactly(1L);
    }

    @Test
    @DisplayName("member-07 API-142: CHANGE_PARTS는 바뀐 경우에만 from·to 파트 ID를 MEMBERSHIP_PARTS_CHANGED로 남긴다")
    void bulkPartsAuditOnlyWhenChanged() throws Exception {
        // 이미 가진 파트 추가, 없는 파트 제거 → 바뀐 것이 없어 기록하지 않는다
        send(1, "/memberships/bulk", partsBody("ADD", "1")).andExpect(status().isOk());
        send(1, "/memberships/bulk", partsBody("REMOVE", "2")).andExpect(status().isOk());
        assertThat(auditCount()).isZero();
        // 실제로 바뀌면 from·to를 남긴다
        send(1, "/memberships/bulk", partsBody("ADD", "2")).andExpect(status().isOk());
        assertThat(auditCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT JSON_EXTRACT(detail, '$.from') FROM audit_log WHERE action = 'MEMBERSHIP_PARTS_CHANGED'",
                        String.class))
                .isEqualTo("[\"1\"]");
        assertThat(jdbc.queryForObject(
                        "SELECT JSON_EXTRACT(detail, '$.to') FROM audit_log WHERE action = 'MEMBERSHIP_PARTS_CHANGED'",
                        String.class))
                .isEqualTo("[\"1\", \"2\"]");
    }

    @Test
    @DisplayName("member-04 C-06 API-023: 이미 소속·유효한 초대가 있으면 409 DUPLICATE, reason DUPLICATE_MEMBERSHIP")
    void inviteDuplicateReason() throws Exception {
        send(1, "/memberships/invite", "{\"handle\":\"member_b\",\"memberType\":\"REGULAR\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE"))
                .andExpect(jsonPath("$.error.details[0].reason").value("DUPLICATE_MEMBERSHIP"));
        jdbc.update("UPDATE membership SET status = 'INVITED', invited_at = UTC_TIMESTAMP(6) WHERE id = 3");
        send(1, "/memberships/invite", "{\"handle\":\"member_b\",\"memberType\":\"REGULAR\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.details[0].reason").value("DUPLICATE_MEMBERSHIP"));
    }

    private void draftPerformanceWithMember2() {
        jdbc.update("INSERT INTO performance (id, club_id, title, preset, status, created_by_user_id)"
                + " VALUES (20, 1, '초안공연', 'CUSTOM', 'DRAFT', 1)");
        jdbc.update("INSERT INTO performance_policy (performance_id, candidate_state, playability_state, vote_state,"
                + " vote_method, vote_max_choices, team_submission_state, availability_state)"
                + " VALUES (20, 'OPEN', 'OPEN', 'OPEN', 'APPROVAL', 1, 'OPEN', 'OPEN')");
        jdbc.update("INSERT INTO performance_member (id, performance_id, club_id, membership_id, status, source)"
                + " VALUES (20, 20, 1, 2, 'APPLIED', 'ADMIN')");
    }

    private List<Long> heldParts(long membershipId) {
        return jdbc.queryForList(
                "SELECT part_id FROM membership_part WHERE membership_id = ?", Long.class, membershipId);
    }

    private int auditCount() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE action = 'MEMBERSHIP_PARTS_CHANGED'", Integer.class);
    }

    private static String partsBody(String mode, String partId) {
        return "{\"action\":\"CHANGE_PARTS\",\"membershipIds\":[\"2\"]," + "\"params\":{\"mode\":\"" + mode
                + "\",\"partIds\":[\"" + partId + "\"]}}";
    }

    private ResultActions read(long actorId, String path) throws Exception {
        return mvc.perform(get("/api/v1/clubs/1" + path).with(authentication(actor(actorId))));
    }

    private ResultActions send(long actorId, String path, String body) throws Exception {
        return mvc.perform(post("/api/v1/clubs/1" + path)
                .with(authentication(actor(actorId)))
                .header("X-Requested-With", "XMLHttpRequest")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions patchName(long membershipId, String displayName) throws Exception {
        // 시드에서 소속 id = 사용자 id
        return mvc.perform(patch("/api/v1/clubs/1/memberships/" + membershipId)
                .with(authentication(actor(membershipId)))
                .header("X-Requested-With", "XMLHttpRequest")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"" + displayName + "\"}"));
    }

    private static UsernamePasswordAuthenticationToken actor(long userId) {
        return new UsernamePasswordAuthenticationToken(new AuthPrincipal(userId, "sid", "jti", false), null, List.of());
    }
}
