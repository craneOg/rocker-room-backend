package com.crane.bandwebbackend.domain.membership.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

/** API-031 임원 역할 부여·해제. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Sql({"/performance-flow-cleanup.sql", "/performance-flow-seed.sql"})
@Sql(scripts = "/performance-flow-cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class MembershipRoleApiTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("API-031: 임원이 활동 정회원에게 부여하면 200이고 ADMIN_GRANTED를 사유와 함께 남긴다")
    void grant() throws Exception {
        change(1, 2, "CLUB_ADMIN", "\"  운영 인수  \"")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("CLUB_ADMIN"));
        assertThat(jdbc.queryForObject("SELECT role FROM membership WHERE id = 2", String.class))
                .isEqualTo("CLUB_ADMIN");
        assertThat(jdbc.queryForObject(
                        "SELECT reason FROM audit_log WHERE action = 'ADMIN_GRANTED' AND target_id = 2", String.class))
                .isEqualTo("운영 인수");
    }

    @Test
    @DisplayName("API-031: 다른 임원이 있으면 해제할 수 있고 ADMIN_REVOKED를 남긴다")
    void revoke() throws Exception {
        jdbc.update("UPDATE membership SET role = 'CLUB_ADMIN' WHERE id = 2");
        change(1, 2, "MEMBER", "null")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("MEMBER"));
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM audit_log WHERE action = 'ADMIN_REVOKED' AND target_id = 2",
                        Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("API-031: 다른 임원이 있으면 임원 본인도 자신의 역할을 해제할 수 있다")
    void revokeSelf() throws Exception {
        jdbc.update("UPDATE membership SET role = 'CLUB_ADMIN' WHERE id = 2");
        change(1, 1, "MEMBER", "null")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("MEMBER"));
        assertThat(jdbc.queryForObject("SELECT role FROM membership WHERE id = 1", String.class))
                .isEqualTo("MEMBER");
    }

    @Test
    @DisplayName("API-031: 객원·졸업·PENDING 대상은 422 ADMIN_TARGET_NOT_ELIGIBLE")
    void notEligible() throws Exception {
        jdbc.update("UPDATE membership SET member_type = 'GUEST' WHERE id = 2");
        jdbc.update("UPDATE membership SET status = 'GRADUATED' WHERE id = 3");
        jdbc.update("UPDATE membership SET status = 'PENDING' WHERE id = 4");
        for (long id : new long[] {2, 3, 4}) {
            change(1, id, "CLUB_ADMIN", "null")
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("RULE_VIOLATION"))
                    .andExpect(jsonPath("$.error.details[0].reason").value("ADMIN_TARGET_NOT_ELIGIBLE"));
        }
    }

    @Test
    @DisplayName("C-09: 마지막 활동·휴학 임원 해제는 422 LAST_ADMIN")
    void lastAdmin() throws Exception {
        change(1, 1, "MEMBER", "null")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.details[0].reason").value("LAST_ADMIN"));
    }

    @Test
    @DisplayName("API-031: 현재와 같은 역할이면 409 RELATION_CONFLICT")
    void sameRole() throws Exception {
        change(1, 2, "MEMBER", "null")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("STATE_CONFLICT"))
                .andExpect(jsonPath("$.error.details[0].reason").value("RELATION_CONFLICT"));
    }

    @Test
    @DisplayName("G-07: 비임원은 403, 비활성 동아리는 409 CLUB_INACTIVE, 공백 사유는 400")
    void guards() throws Exception {
        change(2, 3, "CLUB_ADMIN", "null").andExpect(status().isForbidden());
        change(1, 2, "CLUB_ADMIN", "\"   \"").andExpect(status().isBadRequest());
        jdbc.update("UPDATE club SET status = 'INACTIVE' WHERE id = 1");
        change(1, 2, "CLUB_ADMIN", "null")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.details[0].reason").value("CLUB_INACTIVE"));
    }

    private org.springframework.test.web.servlet.ResultActions change(
            long actorId, long membershipId, String role, String reason) throws Exception {
        return mvc.perform(put("/api/v1/clubs/1/memberships/" + membershipId + "/role")
                .with(authentication(actor(actorId)))
                .header("X-Requested-With", "XMLHttpRequest")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"" + role + "\",\"reason\":" + reason + "}"));
    }

    private static UsernamePasswordAuthenticationToken actor(long userId) {
        return new UsernamePasswordAuthenticationToken(new AuthPrincipal(userId, "sid", "jti", false), null, List.of());
    }
}
