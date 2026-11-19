package com.crane.bandwebbackend.domain.membership.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

/** API-146 응답 모양을 고정한다(145 리팩터링 전후 동일 확인용). */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Sql({"/performance-flow-cleanup.sql", "/performance-flow-seed.sql"})
@Sql(scripts = "/performance-flow-cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class WithdrawalPreviewApiTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("API-146: 마지막 임원 대상이면 승계 예정자를 돌려주고 영향은 비어 있다")
    void soleAdminShowsSuccession() throws Exception {
        mvc.perform(get("/api/v1/clubs/1/memberships/1/withdraw-preview").with(authentication(actor(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.target.membershipId").value("1"))
                .andExpect(jsonPath("$.data.target.displayName").value("임원"))
                .andExpect(jsonPath("$.data.target.role").value("CLUB_ADMIN"))
                .andExpect(jsonPath("$.data.succession.type").value("DESIGNATED_OR_EARLIEST"))
                .andExpect(jsonPath("$.data.succession.candidateMembershipId").value("2"))
                .andExpect(jsonPath("$.data.succession.candidateName").value("부원A"))
                .andExpect(jsonPath("$.data.performanceImpacts").isEmpty())
                .andExpect(jsonPath("$.data.teamLeaderCandidates").isEmpty())
                .andExpect(jsonPath("$.data.warnings").isEmpty());
    }

    @Test
    @DisplayName("API-146: 팀장인 부원이면 공연 영향·팀장 후보·경고를 돌려주고 승계는 null이다")
    void teamLeaderShowsImpactAndCandidates() throws Exception {
        jdbc.update("INSERT INTO performance_member (id, performance_id, club_id, membership_id, status, source)"
                + " VALUES (3, 1, 1, 3, 'APPROVED', 'APPLICATION')");
        jdbc.update("INSERT INTO team (id,performance_id,name,status,source,created_by_user_id)"
                + " VALUES (1,1,'A팀','APPROVED','ADMIN',1)");
        jdbc.update("INSERT INTO team_member (team_id,performance_id,performance_member_id) VALUES (1,1,2),(1,1,3)");
        jdbc.update("UPDATE team SET leader_member_id = 2 WHERE id = 1");

        mvc.perform(get("/api/v1/clubs/1/memberships/2/withdraw-preview").with(authentication(actor(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.target.role").value("MEMBER"))
                .andExpect(jsonPath("$.data.succession").isEmpty())
                .andExpect(jsonPath("$.data.performanceImpacts.length()").value(1))
                .andExpect(
                        jsonPath("$.data.performanceImpacts[0].performanceId").value("1"))
                .andExpect(
                        jsonPath("$.data.performanceImpacts[0].performanceName").value("정기공연"))
                .andExpect(jsonPath("$.data.performanceImpacts[0].participationStatus")
                        .value("APPROVED"))
                .andExpect(jsonPath("$.data.performanceImpacts[0].isTeamLeader").value(true))
                .andExpect(jsonPath("$.data.performanceImpacts[0].teamName").value("A팀"))
                .andExpect(jsonPath("$.data.teamLeaderCandidates.length()").value(1))
                .andExpect(jsonPath("$.data.teamLeaderCandidates[0].teamId").value("1"))
                .andExpect(jsonPath("$.data.teamLeaderCandidates[0].teamName").value("A팀"))
                .andExpect(jsonPath("$.data.teamLeaderCandidates[0].candidates.length()")
                        .value(1))
                .andExpect(jsonPath("$.data.teamLeaderCandidates[0].candidates[0].membershipId")
                        .value("3"))
                .andExpect(jsonPath("$.data.teamLeaderCandidates[0].candidates[0].displayName")
                        .value("부원B"))
                .andExpect(jsonPath("$.data.warnings.length()").value(1))
                .andExpect(jsonPath("$.data.warnings[0]").value("진행 중 공연 1건에서 참여가 철회됩니다."));
    }

    @Test
    @DisplayName("API-146: 팀장 후보는 실제 승계 조건과 같다(졸업·객원 포함, 참여 철회자 제외)")
    void teamLeaderCandidatesMatchSuccession() throws Exception {
        jdbc.update("INSERT INTO users (id, name, handle) VALUES (5, '객원', 'guest_5'), (6, '철회자', 'quit_6')");
        jdbc.update("INSERT INTO membership (id, club_id, user_id, role, status, join_source, member_type) VALUES"
                + " (5, 1, 5, 'MEMBER', 'ACTIVE', 'JOIN_CODE', 'GUEST'),"
                + " (6, 1, 6, 'MEMBER', 'ACTIVE', 'JOIN_CODE', 'REGULAR')");
        jdbc.update("UPDATE membership SET status = 'GRADUATED' WHERE id = 3");
        jdbc.update("INSERT INTO performance_member (id, performance_id, club_id, membership_id, status, source) VALUES"
                + " (3, 1, 1, 3, 'APPROVED', 'APPLICATION'),"
                + " (5, 1, 1, 5, 'APPROVED', 'APPLICATION'),"
                + " (6, 1, 1, 6, 'WITHDRAWN', 'APPLICATION')");
        jdbc.update("INSERT INTO team (id,performance_id,name,status,source,created_by_user_id)"
                + " VALUES (1,1,'A팀','APPROVED','ADMIN',1)");
        jdbc.update("INSERT INTO team_member (team_id,performance_id,performance_member_id)"
                + " VALUES (1,1,2),(1,1,3),(1,1,5),(1,1,6)");
        jdbc.update("UPDATE team SET leader_member_id = 2 WHERE id = 1");
        mvc.perform(get("/api/v1/clubs/1/memberships/2/withdraw-preview").with(authentication(actor(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.teamLeaderCandidates[0].candidates.length()")
                        .value(2))
                .andExpect(jsonPath("$.data.teamLeaderCandidates[0].candidates[0].membershipId")
                        .value("3"))
                .andExpect(jsonPath("$.data.teamLeaderCandidates[0].candidates[1].membershipId")
                        .value("5"));
    }

    private static UsernamePasswordAuthenticationToken actor(long userId) {
        return new UsernamePasswordAuthenticationToken(new AuthPrincipal(userId, "sid", "jti", false), null, List.of());
    }
}
