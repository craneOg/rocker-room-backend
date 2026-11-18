package com.crane.bandwebbackend.domain.membership.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crane.bandwebbackend.TestcontainersConfiguration;
import com.crane.bandwebbackend.global.security.AuthPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

/** 임원 직접 추가(초대) 시 MEMBER_ADDED_BY_ADMIN 감사 기록. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Sql({"/performance-flow-cleanup.sql", "/performance-flow-seed.sql"})
@Sql(scripts = "/performance-flow-cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class MembershipInviteAuditApiTest {
    private static final String ACTION = "MEMBER_ADDED_BY_ADMIN";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    @DisplayName("새 초대는 to=INVITED 감사 기록 1건을 남긴다")
    void newInviteRecordsAudit() throws Exception {
        jdbc.update("INSERT INTO users (id, name, handle) VALUES (5, '신규', 'invitee_5')");
        invite("invitee_5").andExpect(status().isCreated());

        Long membershipId =
                jdbc.queryForObject("SELECT id FROM membership WHERE club_id = 1 AND user_id = 5", Long.class);
        Map<String, Object> row = onlyAuditRow();
        assertThat(row.get("target_type")).isEqualTo("MEMBERSHIP");
        assertThat(((Number) row.get("target_id")).longValue()).isEqualTo(membershipId);
        assertThat(((Number) row.get("actor_user_id")).longValue()).isEqualTo(1L);
        assertThat(((Number) row.get("club_id")).longValue()).isEqualTo(1L);
        assertThat(row.get("reason")).isNull();
        assertThat(detail(row)).isEqualTo(objectMapper.readTree("{\"to\":\"INVITED\"}"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"DECLINED", "WITHDRAWN"})
    @DisplayName("재초대는 기존 소속 행에 from=이전 상태, to=INVITED로 기록한다")
    void reinviteRecordsPreviousStatus(String oldStatus) throws Exception {
        jdbc.update("UPDATE membership SET status = ? WHERE id = 4", oldStatus);
        invite("member_c").andExpect(status().isOk());

        Map<String, Object> row = onlyAuditRow();
        assertThat(((Number) row.get("target_id")).longValue()).isEqualTo(4L);
        assertThat(detail(row)).isEqualTo(objectMapper.readTree("{\"from\":\"" + oldStatus + "\",\"to\":\"INVITED\"}"));
    }

    @Test
    @DisplayName("유효한 초대가 이미 있어 409면 감사 기록을 남기지 않는다")
    void failedInviteRecordsNothing() throws Exception {
        jdbc.update("UPDATE membership SET status = 'INVITED', invited_at = UTC_TIMESTAMP(6) WHERE id = 3");
        invite("member_b").andExpect(status().isConflict());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action = ?", Integer.class, ACTION))
                .isZero();
    }

    private Map<String, Object> onlyAuditRow() {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM audit_log WHERE action = ?", ACTION);
        assertThat(rows).hasSize(1);
        return rows.get(0);
    }

    private JsonNode detail(Map<String, Object> row) throws Exception {
        return objectMapper.readTree(row.get("detail").toString());
    }

    private ResultActions invite(String handle) throws Exception {
        return mvc.perform(post("/api/v1/clubs/1/memberships/invite")
                .with(authentication(new UsernamePasswordAuthenticationToken(
                        new AuthPrincipal(1L, "sid", "jti", false), null, List.of())))
                .header("X-Requested-With", "XMLHttpRequest")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"handle\":\"" + handle + "\"}"));
    }
}
