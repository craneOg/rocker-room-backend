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

@SpringBootTest
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
    @CsvSource({
        "revert-to-locked,FINISHED,LOCKED,{\"reason\":\"되돌리기\"}",
        "revert-to-draft,ACTIVE,DRAFT,{}",
        "revert-to-draft,ACTIVE,DRAFT,{\"reason\":\"되돌리기\"}"
    })
    @DisplayName("API-151·152 명세 예시 본문(version 없음)으로 되돌리기가 성공하고 감사를 남긴다")
    void revertsWithoutVersion(String endpoint, String source, String target, String body) throws Exception {
        prepare(source);
        send(endpoint, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(target))
                .andExpect(jsonPath("$.data.version").value("8"));
        assertThat(jdbc.queryForObject("SELECT status FROM performance WHERE id=1", String.class))
                .isEqualTo(target);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM audit_log WHERE action='PERFORMANCE_STATUS_CHANGED'", Long.class))
                .isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({"{}", "{\"reason\":\" \"}"})
    @DisplayName("API-152 reason 누락·공백은 400이다")
    void revertToLockedRequiresReason(String body) throws Exception {
        prepare("FINISHED");
        send("revert-to-locked", body).andExpect(status().isBadRequest());
        unchanged("FINISHED");
    }

    @ParameterizedTest
    @CsvSource({"ACTIVE", "LOCKED"})
    @DisplayName("API-047 ACTIVE·LOCKED 공연은 종료할 수 있다")
    void finishesActiveAndLocked(String source) throws Exception {
        prepare(source);
        send("finish", "{\"version\":\"7\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FINISHED"));
    }

    @ParameterizedTest
    @CsvSource({"ACTIVE", "LOCKED"})
    @DisplayName("API-152 종료를 되돌리면 종료 직전 상태로 돌아간다(진행 중에서 종료됐으면 확정을 거치지 않고 진행 중)")
    void revertsFinishToPreviousState(String source) throws Exception {
        prepare(source);
        send("finish", "{\"version\":\"7\"}").andExpect(status().isOk());
        send("revert-to-locked", "{\"reason\":\"종료 취소\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(source));
        assertThat(jdbc.queryForObject("SELECT status FROM performance WHERE id=1", String.class))
                .isEqualTo(source);
    }

    @ParameterizedTest
    @CsvSource({"DRAFT", "FINISHED", "CANCELED"})
    @DisplayName("API-047 ACTIVE·LOCKED 외 상태의 종료는 409 INVALID_STATE")
    void rejectsFinishFromOtherStates(String source) throws Exception {
        prepare(source);
        send("finish", "{\"version\":\"7\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.details[0].reason").value("INVALID_STATE"));
        unchanged(source);
    }

    @Test
    @DisplayName("되돌리기 사유 필수와 MEMBER_INPUT_EXISTS 보호는 유지한다")
    void retainsExistingGuards() throws Exception {
        prepare("FINISHED");
        send("revert-to-locked", "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].reason").value("REASON_REQUIRED"));
        unchanged("FINISHED");
        prepare("ACTIVE");
        jdbc.update("UPDATE performance_member SET source='APPLICATION' WHERE id=2");
        send("revert-to-draft", "{}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.details[0].reason").value("MEMBER_INPUT_EXISTS"));
        unchanged("ACTIVE");
        // 빈 목록만 저장한 가능 시간 제출도 부원 입력으로 본다
        jdbc.update("UPDATE performance_member SET source='ADMIN', availability_submitted_at=NOW() WHERE id=2");
        send("revert-to-draft", "{}")
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
