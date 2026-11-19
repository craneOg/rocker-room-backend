package com.crane.bandwebbackend.domain.membership.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crane.bandwebbackend.TestcontainersConfiguration;
import com.crane.bandwebbackend.domain.membership.entity.MembershipStatus;
import com.crane.bandwebbackend.domain.membership.service.MembershipDecisionService;
import com.crane.bandwebbackend.global.error.BusinessException;
import com.crane.bandwebbackend.global.error.ErrorCode;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// #55: 탈퇴한 사용자의 PENDING·INVITED 소속은 ACTIVE로 승인·수락할 수 없다. 시드: 사용자 1(임원), 동아리 1
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Sql({"/performance-flow-cleanup.sql", "/performance-flow-seed.sql"})
@Sql(scripts = "/performance-flow-cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class WithdrawnUserDecisionApiTest {
    // 시드(사용자 1~4, 소속 1~4)와 겹치지 않는 탈퇴 사용자의 소속
    private static final long PENDING_MEMBERSHIP = 51;
    private static final long INVITED_MEMBERSHIP = 52;

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MembershipDecisionService decisions;

    @BeforeEach
    void withdrawnUsers() {
        jdbc.update(
                "INSERT INTO users (id, name, handle) VALUES (51, '탈퇴신청자', 'member_51'), (52, '탈퇴초대자', 'member_52')");
        jdbc.update("INSERT INTO membership (id, club_id, user_id, role, status, join_source) VALUES"
                + " (51, 1, 51, 'MEMBER', 'PENDING', 'JOIN_CODE')");
        jdbc.update("INSERT INTO membership (id, club_id, user_id, role, status, join_source, invited_at) VALUES"
                + " (52, 1, 52, 'MEMBER', 'INVITED', 'ADMIN_ADDED', UTC_TIMESTAMP(6))");
        jdbc.update("UPDATE users SET status = 'WITHDRAWN', withdrawn_at = UTC_TIMESTAMP(6) WHERE id IN (51, 52)");
    }

    @Test
    @DisplayName("탈퇴한 사용자의 가입 신청을 승인하면 404 NOT_FOUND이고 PENDING·감사 기록이 그대로다")
    void approveWithdrawnUser() throws Exception {
        asAdmin(PENDING_MEMBERSHIP, "approve", null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

        assertThat(statusOf(PENDING_MEMBERSHIP)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM audit_log WHERE action = 'MEMBERSHIP_APPROVED'", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("탈퇴한 사용자의 초대 수락은 NOT_FOUND이고 INVITED가 그대로다(HTTP는 인증 필터가 먼저 401로 막아 서비스를 직접 호출)")
    void acceptWithdrawnUser() {
        assertThatThrownBy(() -> decisions.decide(
                        1L, INVITED_MEMBERSHIP, 52L, MembershipStatus.INVITED, MembershipStatus.ACTIVE, null))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));

        assertThat(statusOf(INVITED_MEMBERSHIP)).isEqualTo("INVITED");
    }

    @Test
    @DisplayName("대조: 탈퇴한 사용자의 가입 신청 거절은 200 REJECTED")
    void rejectWithdrawnUser() throws Exception {
        asAdmin(PENDING_MEMBERSHIP, "reject", "{\"reason\":\"탈퇴한 사용자\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));

        assertThat(statusOf(PENDING_MEMBERSHIP)).isEqualTo("REJECTED");
    }

    private ResultActions asAdmin(long membershipId, String action, String body) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/v1/clubs/1/memberships/" + membershipId + "/" + action)
                .with(authentication(new UsernamePasswordAuthenticationToken(
                        new AuthPrincipal(1, "sid", "jti", false), null, List.of())))
                .header("X-Requested-With", "XMLHttpRequest");
        return mvc.perform(
                body == null
                        ? request
                        : request.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String statusOf(long membershipId) {
        return jdbc.queryForObject("SELECT status FROM membership WHERE id = ?", String.class, membershipId);
    }
}
