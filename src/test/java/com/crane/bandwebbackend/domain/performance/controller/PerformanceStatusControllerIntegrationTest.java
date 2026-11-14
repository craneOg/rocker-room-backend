package com.crane.bandwebbackend.domain.performance.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crane.bandwebbackend.TestcontainersConfiguration;
import com.crane.bandwebbackend.global.security.AuthPrincipal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

@SpringBootTest(properties = "rocker.api-v1.enabled=true")
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Sql({"/performance-flow-cleanup.sql", "/performance-flow-seed.sql"})
@Sql(scripts = "/performance-flow-cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class PerformanceStatusControllerIntegrationTest {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @ParameterizedTest
    @CsvSource({"revert-to-locked,FINISHED", "revert-to-draft,ACTIVE"})
    @DisplayName("0.4 되돌리기 두 API 모두 본문·version 누락을 400으로 거부한다")
    void requiresVersion(String endpoint, String source) throws Exception {
        prepare(source);
        send(endpoint, "{\"reason\":\"되돌리기\"}").andExpect(status().isBadRequest());
        send(endpoint, "").andExpect(status().isBadRequest());
        send(endpoint, "{\"version\":\"-1\"}").andExpect(status().isBadRequest());
        unchanged(source);
    }

    @ParameterizedTest
    @CsvSource({"revert-to-locked,FINISHED", "revert-to-draft,ACTIVE"})
    @DisplayName("0.4 오래된 version의 되돌리기는 409이며 상태·감사를 변경하지 않는다")
    void rejectsStaleVersion(String endpoint, String source) throws Exception {
        prepare(source);
        send(endpoint, "{\"version\":\"6\",\"reason\":\"되돌리기\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("VERSION_CONFLICT"));
        unchanged(source);
    }

    @ParameterizedTest
    @CsvSource({"revert-to-locked,FINISHED,LOCKED", "revert-to-draft,ACTIVE,DRAFT"})
    @DisplayName("0.4 현재 version이면 되돌리기 상태·새 version·상태 전이 감사를 반영한다")
    void acceptsCurrentVersion(String endpoint, String source, String target) throws Exception {
        prepare(source);
        send(endpoint, "{\"version\":\"7\",\"reason\":\"되돌리기\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(target))
                .andExpect(jsonPath("$.data.version").value("8"));
        assertThat(jdbc.queryForObject("SELECT status FROM performance WHERE id=1", String.class))
                .isEqualTo(target);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM audit_log WHERE action='PERFORMANCE_STATUS_CHANGED'", Long.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("되돌리기 사유 필수와 MEMBER_INPUT_EXISTS 보호는 유지한다")
    void retainsExistingGuards() throws Exception {
        prepare("FINISHED");
        send("revert-to-locked", "{\"version\":\"7\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].reason").value("REASON_REQUIRED"));
        unchanged("FINISHED");
        prepare("ACTIVE");
        jdbc.update("UPDATE performance_member SET source='APPLICATION' WHERE id=2");
        send("revert-to-draft", "{\"version\":\"7\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.details[0].reason").value("MEMBER_INPUT_EXISTS"));
        unchanged("ACTIVE");
    }

    private void prepare(String source) {
        jdbc.update("UPDATE performance SET status=?,version=7 WHERE id=1", source);
        jdbc.update("UPDATE performance_member SET source='ADMIN' WHERE id=2");
    }

    private ResultActions send(String endpoint, String body) throws Exception {
        return mvc.perform(post("/api/v1/clubs/1/performances/1/" + endpoint)
                .with(authentication(new UsernamePasswordAuthenticationToken(
                        new AuthPrincipal(1L, "sid", "jti", false), null, List.of())))
                .header("X-Requested-With", "test")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private void unchanged(String source) {
        assertThat(jdbc.queryForObject("SELECT status FROM performance WHERE id=1", String.class))
                .isEqualTo(source);
        assertThat(jdbc.queryForObject("SELECT version FROM performance WHERE id=1", Long.class))
                .isEqualTo(7);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log", Long.class))
                .isZero();
    }
}
