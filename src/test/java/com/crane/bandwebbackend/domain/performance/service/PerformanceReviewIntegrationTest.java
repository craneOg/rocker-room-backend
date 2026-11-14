package com.crane.bandwebbackend.domain.performance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crane.bandwebbackend.TestcontainersConfiguration;
import com.crane.bandwebbackend.domain.performance.dto.VoteMethodChangeRequest;
import com.crane.bandwebbackend.domain.performance.entity.VoteMethod;
import com.crane.bandwebbackend.global.error.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Sql({"/performance-flow-cleanup.sql", "/performance-flow-seed.sql", "/performance-review-seed.sql"})
@Sql(scripts = "/performance-flow-cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class PerformanceReviewIntegrationTest {
    @Autowired
    private PerformanceDetailReader details;

    @Autowired
    private PerformancePolicyEditService policies;

    @Autowired
    private VoteMethodChangeService votes;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private PerformanceLockService locks;

    @Test
    @DisplayName("검증 규칙 2.2/API-051 공연 확정 시 상태 전이와 셋리스트 확정 감사를 함께 기록한다")
    void lockRecordsBothAuditActions() {
        jdbc.update(
                "INSERT INTO team (id,performance_id,name,status,source,created_by_user_id) VALUES (1,1,'팀','APPROVED','ADMIN',1)");
        jdbc.update(
                "INSERT INTO song (id,performance_id,title,status,setlist_order,assigned_team_id,proposed_by_membership_id) VALUES (1,1,'선정곡','CONFIRMED',1,1,1)");
        jdbc.update(
                "INSERT INTO song_session_member (song_id,performance_id,performance_member_id,part_id) VALUES (1,1,2,1)");
        locks.lock(1L, 1L, 1L, "0", true);
        assertThat(jdbc.queryForObject("SELECT status FROM performance WHERE id=1", String.class))
                .isEqualTo("LOCKED");
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM audit_log WHERE action='PERFORMANCE_STATUS_CHANGED'"
                                + " AND target_id=1 AND JSON_UNQUOTE(JSON_EXTRACT(detail,'$.from'))='ACTIVE'"
                                + " AND JSON_UNQUOTE(JSON_EXTRACT(detail,'$.to'))='LOCKED'",
                        Long.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM audit_log WHERE action='SETLIST_CONFIRMED' AND target_id=1", Long.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("L-01·02·03/API-051 필수 오류는 대상별 details를 반환하고 상태·감사를 변경하지 않는다")
    void lockFailureReturnsDetailsWithoutMutation() {
        jdbc.update(
                "INSERT INTO song (id,performance_id,title,status,proposed_by_membership_id) VALUES (1,1,'미완성 선정곡','CONFIRMED',1)");
        assertThatThrownBy(() -> locks.lock(1L, 1L, 1L, "0", true))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getDetails())
                            .extracting(detail -> detail.getReason())
                            .contains(
                                    "LOCK_REQUIREMENTS_FAILED",
                                    "SETLIST_ORDER_MISSING",
                                    "TEAM_NOT_APPROVED",
                                    "PLAYER_MISSING");
                    assertThat(e.getDetails())
                            .extracting(detail -> detail.getField())
                            .contains("songId:1");
                });
        assertThat(jdbc.queryForObject("SELECT status FROM performance WHERE id=1", String.class))
                .isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log", Long.class))
                .isZero();
    }

    @Test
    @DisplayName("API-41 객원 공연 상세에는 본인이 참석하는 다음 확정 일정만 표시한다")
    void guestSeesOnlyAttendedNextSchedule() {
        var detail = details.read(1L, 1L, 4L);

        assertThat(detail.getNextSchedule()).isNotNull();
        assertThat(detail.getNextSchedule().getId()).isEqualTo("11");
    }

    @Test
    @DisplayName("API-42 정책 변경 응답 버전으로 다음 PATCH가 성공한다")
    void policyUpdateReturnsCommittedVersion() throws Exception {
        var response = policies.update(
                1L,
                1L,
                1L,
                mapper.readTree("{\"version\":\"0\",\"voteMaxChoices\":2,"
                        + "\"practiceSeasonStart\":\"2026-12-01\",\"practiceSeasonEnd\":\"2026-12-31\"}"));

        assertThat(Long.parseLong(response.getPolicy().getPerformanceVersion()))
                .isEqualTo(versionInDatabase())
                .isGreaterThan(0);
        var next = policies.update(
                1L,
                1L,
                1L,
                mapper.readTree(
                        "{\"version\":\"" + response.getPolicy().getPerformanceVersion() + "\",\"voteMaxChoices\":3}"));
        assertThat(Long.parseLong(next.getPolicy().getPerformanceVersion()))
                .isEqualTo(versionInDatabase())
                .isGreaterThan(Long.parseLong(response.getPolicy().getPerformanceVersion()));
    }

    @Test
    @DisplayName("API-44 투표 방식 변경 응답 버전으로 다음 변경이 성공한다")
    void voteMethodChangeReturnsCommittedVersion() {
        var response = votes.change(1L, 1L, 1L, new VoteMethodChangeRequest("0", VoteMethod.SCORE, true, "방식 변경"));

        assertThat(Long.parseLong(response.getPolicy().getPerformanceVersion()))
                .isEqualTo(versionInDatabase())
                .isGreaterThan(0);
        var next = votes.change(
                1L,
                1L,
                1L,
                new VoteMethodChangeRequest(
                        response.getPolicy().getPerformanceVersion(), VoteMethod.APPROVAL, true, "방식 재변경"));
        assertThat(Long.parseLong(next.getPolicy().getPerformanceVersion()))
                .isEqualTo(versionInDatabase())
                .isGreaterThan(Long.parseLong(response.getPolicy().getPerformanceVersion()));
    }

    private long versionInDatabase() {
        return jdbc.queryForObject("SELECT version FROM performance WHERE id = 1", Long.class);
    }
}
