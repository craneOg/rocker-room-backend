package com.crane.bandwebbackend.domain.membership.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crane.bandwebbackend.TestcontainersConfiguration;
import com.crane.bandwebbackend.global.security.AuthPrincipal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
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

// #63(C-14): 가입 승인 시 BAND 공연의 기본 팀에도 편성. 시드: 사용자 1(임원), 동아리 1, 공연 1(CUSTOM)
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Sql({"/performance-flow-cleanup.sql", "/performance-flow-seed.sql"})
@Sql(scripts = "/performance-flow-cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class JoinApprovalDefaultTeamApiTest {
    private static final long BAND_TEAM = 100;
    private static final long CUSTOM_TEAM = 101;

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        jdbc.update("INSERT INTO users (id, name, handle) VALUES (51, '신청자A', 'member_51'), (52, '신청자B', 'member_52')");
        jdbc.update("INSERT INTO membership (id, club_id, user_id, role, status, join_source) VALUES"
                + " (51, 1, 51, 'MEMBER', 'PENDING', 'JOIN_CODE'), (52, 1, 52, 'MEMBER', 'PENDING', 'JOIN_CODE')");
        jdbc.update("INSERT INTO performance (id, club_id, title, preset, status, created_by_user_id)"
                + " VALUES (10, 1, '밴드공연', 'BAND', 'ACTIVE', 1)");
        jdbc.update("INSERT INTO team (id, performance_id, name, is_default, status, source, created_by_user_id)"
                + " VALUES (100, 10, '기본팀', TRUE, 'APPROVED', 'ADMIN', 1),"
                + " (101, 1, '기본팀', TRUE, 'APPROVED', 'ADMIN', 1)");
    }

    @Test
    @DisplayName("가입 신청을 승인하면 BAND 공연 기본 팀에 편성되고 팀 version이 오른다")
    void approveLinksDefaultTeam() throws Exception {
        long before = teamVersion(BAND_TEAM);

        send("/api/v1/clubs/1/memberships/51/approve", null).andExpect(status().isOk());

        assertThat(teamMemberCount(BAND_TEAM, 51)).isEqualTo(1);
        assertThat(teamVersion(BAND_TEAM)).isGreaterThan(before);
        // CUSTOM 공연은 자동 편성 대상이 아니다
        assertThat(teamMemberCount(CUSTOM_TEAM, 51)).isZero();
    }

    @Test
    @DisplayName("일괄 승인도 BAND 공연 기본 팀에 편성한다")
    void bulkApproveLinksDefaultTeam() throws Exception {
        long before = teamVersion(BAND_TEAM);

        send(
                        "/api/v1/clubs/1/join-requests/bulk-decisions",
                        "{\"membershipIds\":[\"51\",\"52\"],\"decision\":\"APPROVE\"}")
                .andExpect(status().isOk());

        assertThat(teamMemberCount(BAND_TEAM, 51)).isEqualTo(1);
        assertThat(teamMemberCount(BAND_TEAM, 52)).isEqualTo(1);
        assertThat(teamVersion(BAND_TEAM)).isGreaterThan(before);
    }

    @Test
    @DisplayName("이미 기본 팀원인 재참여자는 중복 편성하지 않는다")
    void alreadyTeamMemberNotDuplicated() throws Exception {
        jdbc.update("INSERT INTO performance_member (id, performance_id, club_id, membership_id, status, source)"
                + " VALUES (500, 10, 1, 51, 'WITHDRAWN', 'APPLICATION')");
        jdbc.update("INSERT INTO team_member (team_id, performance_id, performance_member_id) VALUES (100, 10, 500)");
        long before = teamVersion(BAND_TEAM);

        send("/api/v1/clubs/1/memberships/51/approve", null).andExpect(status().isOk());

        assertThat(teamMemberCount(BAND_TEAM, 51)).isEqualTo(1);
        assertThat(teamVersion(BAND_TEAM)).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT status FROM performance_member WHERE id = 500", String.class))
                .isEqualTo("APPROVED");
    }

    private ResultActions send(String url, String body) throws Exception {
        var request = MockMvcRequestBuilders.post(url)
                .with(authentication(new UsernamePasswordAuthenticationToken(
                        new AuthPrincipal(1, "sid", "jti", false), null, List.of())))
                .header("X-Requested-With", "XMLHttpRequest");
        return mvc.perform(
                body == null
                        ? request
                        : request.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long teamVersion(long teamId) {
        return jdbc.queryForObject("SELECT version FROM team WHERE id = ?", Long.class, teamId);
    }

    private int teamMemberCount(long teamId, long membershipId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM team_member tm JOIN performance_member pm ON pm.id = tm.performance_member_id"
                        + " WHERE tm.team_id = ? AND pm.membership_id = ?",
                Integer.class,
                teamId,
                membershipId);
    }
}
