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
import org.springframework.test.web.servlet.ResultActions;

/** API-145 본인 나가기 영향 미리보기. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Sql({"/performance-flow-cleanup.sql", "/performance-flow-seed.sql"})
@Sql(scripts = "/performance-flow-cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class LeavePreviewApiTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("API-145: 일반 부원은 승계 없이 role만 돌려준다")
    void plainMember() throws Exception {
        preview(3)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("MEMBER"))
                .andExpect(jsonPath("$.data.target").doesNotExist())
                .andExpect(jsonPath("$.data.succession").isEmpty())
                .andExpect(jsonPath("$.data.performanceImpacts").isEmpty())
                .andExpect(jsonPath("$.data.teamLeaderCandidates").isEmpty())
                .andExpect(jsonPath("$.data.warnings").isEmpty());
    }

    @Test
    @DisplayName("API-145: 마지막 임원이면 승계 예정자를 돌려주고 경고는 없다")
    void soleAdminWithCandidate() throws Exception {
        preview(1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("CLUB_ADMIN"))
                .andExpect(jsonPath("$.data.succession.type").value("DESIGNATED_OR_EARLIEST"))
                .andExpect(jsonPath("$.data.succession.candidateMembershipId").value("2"))
                .andExpect(jsonPath("$.data.succession.candidateName").value("부원A"))
                .andExpect(jsonPath("$.data.warnings").isEmpty());
    }

    @Test
    @DisplayName("API-145: 마지막 임원이고 후임이 없으면 동아리 비활성화 경고")
    void soleAdminWithoutCandidate() throws Exception {
        jdbc.update("UPDATE membership SET status = 'GRADUATED' WHERE id IN (2, 3)");
        preview(1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.succession.candidateMembershipId").isEmpty())
                .andExpect(jsonPath("$.data.warnings[0]").value("동아리가 비활성화됩니다."));
    }

    @Test
    @DisplayName("API-145: 다른 임원이 있으면 승계는 null")
    void otherAdminExists() throws Exception {
        jdbc.update("UPDATE membership SET role = 'CLUB_ADMIN' WHERE id = 2");
        preview(1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.succession").isEmpty());
    }

    @Test
    @DisplayName("API-145: 팀장이면 공연 영향·팀장 후보·철회 경고를 돌려준다")
    void teamLeader() throws Exception {
        jdbc.update("INSERT INTO performance_member (id, performance_id, club_id, membership_id, status, source)"
                + " VALUES (3, 1, 1, 3, 'APPROVED', 'APPLICATION')");
        jdbc.update("INSERT INTO team (id,performance_id,name,status,source,created_by_user_id)"
                + " VALUES (1,1,'A팀','APPROVED','ADMIN',1)");
        jdbc.update("INSERT INTO team_member (team_id,performance_id,performance_member_id) VALUES (1,1,2),(1,1,3)");
        jdbc.update("UPDATE team SET leader_member_id = 2 WHERE id = 1");
        preview(2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.performanceImpacts.length()").value(1))
                .andExpect(jsonPath("$.data.performanceImpacts[0].isTeamLeader").value(true))
                .andExpect(jsonPath("$.data.performanceImpacts[0].teamName").value("A팀"))
                .andExpect(jsonPath("$.data.teamLeaderCandidates[0].candidates[0].membershipId")
                        .value("3"))
                .andExpect(jsonPath("$.data.warnings[0]").value("진행 중 공연 1건에서 참여가 철회됩니다."));
    }

    @Test
    @DisplayName("API-145: 팀장 후보는 실제 승계 조건과 같다(졸업·객원 포함, 참여 철회자 제외)")
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
        preview(2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.teamLeaderCandidates[0].candidates.length()")
                        .value(2))
                .andExpect(jsonPath("$.data.teamLeaderCandidates[0].candidates[0].membershipId")
                        .value("3"))
                .andExpect(jsonPath("$.data.teamLeaderCandidates[0].candidates[1].membershipId")
                        .value("5"));
    }

    @Test
    @DisplayName("API-145: PENDING 신청자는 활동 가능한 소속이 아니므로 403")
    void pendingUser() throws Exception {
        jdbc.update("UPDATE membership SET status = 'PENDING' WHERE id = 3");
        preview(3).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("G-04: 이 동아리 소속이 아니면 404")
    void notMember() throws Exception {
        jdbc.update("INSERT INTO users (id, name, handle) VALUES (5, '외부인', 'outsider_5')");
        preview(5).andExpect(status().isNotFound());
    }

    private ResultActions preview(long userId) throws Exception {
        return mvc.perform(get("/api/v1/clubs/1/memberships/me/leave-preview").with(authentication(actor(userId))));
    }

    private static UsernamePasswordAuthenticationToken actor(long userId) {
        return new UsernamePasswordAuthenticationToken(new AuthPrincipal(userId, "sid", "jti", false), null, List.of());
    }
}
