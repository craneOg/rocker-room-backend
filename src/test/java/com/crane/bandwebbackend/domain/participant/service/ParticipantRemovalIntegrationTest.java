package com.crane.bandwebbackend.domain.participant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crane.bandwebbackend.TestcontainersConfiguration;
import com.crane.bandwebbackend.domain.membership.dto.MembershipWithdrawalRequest;
import com.crane.bandwebbackend.domain.membership.repository.MembershipRepository;
import com.crane.bandwebbackend.domain.membership.service.MembershipWithdrawalService;
import com.crane.bandwebbackend.domain.participant.dto.ParticipantRemovalRequest;
import com.crane.bandwebbackend.domain.performance.repository.PerformanceRepository;
import com.crane.bandwebbackend.global.error.BusinessException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Sql({"/performance-flow-cleanup.sql", "/performance-flow-seed.sql"})
@Sql(scripts = "/performance-flow-cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class ParticipantRemovalIntegrationTest {
    @Autowired
    private ParticipantRemovalService removal;

    @Autowired
    private MembershipWithdrawalService withdrawal;

    @Autowired
    private ParticipationLookup participants;

    @Autowired
    private PerformanceRepository performances;

    @Autowired
    private MembershipRepository memberships;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TeamLeaderSuccession succession;

    @Autowired
    private ParticipantAssignmentService assignments;

    @BeforeEach
    void assignments() {
        jdbc.update(
                "INSERT INTO performance_member (id, performance_id, club_id, membership_id, status, source) VALUES (3,1,1,3,'APPROVED','ADMIN')");
        jdbc.update(
                "INSERT INTO team (id,performance_id,name,status,source,created_by_user_id) VALUES (1,1,'보존 팀','APPROVED','ADMIN',1)");
        jdbc.update("INSERT INTO team_member (team_id,performance_id,performance_member_id) VALUES (1,1,2),(1,1,3)");
        jdbc.update("UPDATE team SET leader_member_id=2 WHERE id=1");
        jdbc.update(
                "INSERT INTO song (id,performance_id,title,status,proposed_by_membership_id) VALUES (1,1,'보존 곡','PROPOSED',1)");
        jdbc.update(
                "INSERT INTO song_session_member (song_id,performance_id,performance_member_id,part_id) VALUES (1,1,2,1),(1,1,3,1)");
        jdbc.update(
                "INSERT INTO practice_schedule (id,performance_id,title,status,start_at,end_at,created_by_user_id) VALUES (1,1,'영향 일정','CONFIRMED','2099-01-01 00:00:00','2099-01-01 01:00:00',1)");
        jdbc.update("INSERT INTO practice_schedule_team (practice_schedule_id,team_id,performance_id) VALUES (1,1,1)");
    }

    @Test
    @DisplayName("P-07/API-060 승계 후 배정만 삭제하고 영향·참여 이력과 다른 사람의 배정은 보존한다")
    void withdrawAndReapply() {
        var result = removal.remove(1L, 1L, 2L, 1L, request(), false);
        assertRemoved("WITHDRAWN");
        assertThat(result.getEffects().getAffectedScheduleIds()).contains("1");
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> participants.ensureAdminParticipation(
                        performances.findById(1L).orElseThrow(),
                        memberships.findById(2L).orElseThrow(),
                        LocalDateTime.now()));
        assertThat(value("SELECT status FROM performance_member WHERE id=2")).isEqualTo("APPROVED");
        assertThat(count("SELECT COUNT(*) FROM team_member WHERE performance_member_id=2"))
                .isZero();
        assertThat(count("SELECT COUNT(*) FROM song_session_member WHERE performance_member_id=2"))
                .isZero();
    }

    @Test
    @DisplayName("P-07/API-058 거절도 참여 행을 보존하고 배정만 삭제한다")
    void reject() {
        jdbc.update("UPDATE performance_member SET status='APPLIED' WHERE id=2");
        removal.remove(1L, 1L, 2L, 1L, request(), true);
        assertRemoved("REJECTED");
    }

    @Test
    @DisplayName("P-07/API-030 소속 해제는 진행 중 공연만 정리하고 종료·취소 편성을 보존한다")
    void membershipWithdrawalPreservesHistory() {
        for (int id = 2; id <= 3; id++) {
            jdbc.update(
                    "INSERT INTO performance (id,club_id,title,preset,status,created_by_user_id) VALUES (?,1,?,'CUSTOM',?,1)",
                    id,
                    "과거 공연" + id,
                    id == 2 ? "FINISHED" : "CANCELED");
            jdbc.update(
                    "INSERT INTO performance_member (id,performance_id,club_id,membership_id,status,source) VALUES (?,?,1,2,'APPROVED','ADMIN')",
                    id + 10,
                    id);
            jdbc.update(
                    "INSERT INTO team (id,performance_id,name,status,source,created_by_user_id) VALUES (?,?,'과거 팀','APPROVED','ADMIN',1)",
                    id,
                    id);
            jdbc.update(
                    "INSERT INTO team_member (team_id,performance_id,performance_member_id) VALUES (?,?,?)",
                    id,
                    id,
                    id + 10);
            jdbc.update(
                    "INSERT INTO song (id,performance_id,title,status,proposed_by_membership_id) VALUES (?,?,'과거 곡','PROPOSED',1)",
                    id,
                    id);
            jdbc.update(
                    "INSERT INTO song_session_member (song_id,performance_id,performance_member_id,part_id) VALUES (?,?,?,1)",
                    id,
                    id,
                    id + 10);
        }
        var result = withdrawal.withdraw(1L, 2L, 1L, null);
        assertRemoved("WITHDRAWN");
        assertThat(result.getEffects().getAffectedScheduleIds()).contains("1");
        assertThat(count("SELECT COUNT(*) FROM team_member WHERE performance_member_id IN (12,13)"))
                .isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM song_session_member WHERE performance_member_id IN (12,13)"))
                .isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM performance_member WHERE id IN (12,13) AND status='APPROVED'"))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("P-07 승계·배정 삭제 후 검증 실패 시 전체 트랜잭션을 되돌린다")
    void rollsBack() {
        assertThatThrownBy(() -> withdrawal.withdraw(
                        1L,
                        2L,
                        1L,
                        new MembershipWithdrawalRequest(
                                null, null, List.of(new MembershipWithdrawalRequest.TeamSuccessor("99", "3")))))
                .isInstanceOf(BusinessException.class);
        assertThat(value("SELECT status FROM performance_member WHERE id=2")).isEqualTo("APPROVED");
        assertThat(count("SELECT leader_member_id FROM team WHERE id=1")).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM team_member WHERE performance_member_id=2"))
                .isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM song_session_member WHERE performance_member_id=2"))
                .isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM audit_log WHERE action='PERFORMANCE_MEMBER_WITHDRAWN'"))
                .isZero();
    }

    @Test
    @DisplayName("X-04/API-060 LOCKED 곡의 마지막 승인 연주자는 철회할 수 없다")
    void lockedLastPlayer() {
        jdbc.update("UPDATE performance SET status='LOCKED' WHERE id=1");
        jdbc.update("UPDATE song SET status='CONFIRMED',assigned_team_id=1 WHERE id=1");
        jdbc.update("DELETE FROM song_session_member WHERE performance_member_id=3");
        assertThatThrownBy(() -> removal.remove(1L, 1L, 2L, 1L, request(), false))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getDetails().get(0).getReason()).isEqualTo("SONG_WITHOUT_PLAYER"));
        assertThat(value("SELECT status FROM performance_member WHERE id=2")).isEqualTo("APPROVED");
        assertThat(count("SELECT leader_member_id FROM team WHERE id=1")).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM song_session_member WHERE performance_member_id=2"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("X-02/API-030 LOCKED 참여가 있으면 소속 해제 사유가 필수다")
    void membershipWithdrawalRequiresLockedReason() {
        lockConfirmedSong();
        assertThatThrownBy(() -> withdrawal.withdraw(1L, 2L, 1L, null))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getErrorCode())
                            .isEqualTo(com.crane.bandwebbackend.global.error.ErrorCode.VALIDATION_ERROR);
                    assertThat(e.getDetails().get(0).getReason()).isEqualTo("REASON_REQUIRED");
                });
        assertThat(value("SELECT status FROM membership WHERE id=2")).isEqualTo("ACTIVE");
        assertThat(count("SELECT COUNT(*) FROM team_member WHERE performance_member_id=2"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("X-04/API-030 마지막 승인 연주자를 제거하는 소속 해제는 422로 차단한다")
    void membershipWithdrawalBlocksLockedLastPlayer() {
        lockConfirmedSong();
        jdbc.update("DELETE FROM song_session_member WHERE performance_member_id=3");
        assertThatThrownBy(() ->
                        withdrawal.withdraw(1L, 2L, 1L, new MembershipWithdrawalRequest("소속 해제", null, List.of())))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getErrorCode())
                            .isEqualTo(com.crane.bandwebbackend.global.error.ErrorCode.RULE_VIOLATION);
                    assertThat(e.getDetails().get(0).getReason()).isEqualTo("SONG_WITHOUT_PLAYER");
                    assertThat(e.getDetails().get(0).getField()).isEqualTo("songId:1");
                });
        assertThat(value("SELECT status FROM membership WHERE id=2")).isEqualTo("ACTIVE");
        assertThat(value("SELECT status FROM performance_member WHERE id=2")).isEqualTo("APPROVED");
        assertThat(count("SELECT leader_member_id FROM team WHERE id=1")).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM song_session_member WHERE performance_member_id=2"))
                .isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM audit_log")).isZero();
    }

    @Test
    @DisplayName("X-07/API-030 다른 연주자가 남으면 소속 해제와 LOCKED 예외 감사를 함께 반영한다")
    void membershipWithdrawalRecordsLockedException() {
        lockConfirmedSong();
        withdrawal.withdraw(1L, 2L, 1L, new MembershipWithdrawalRequest("소속 해제", null, List.of()));
        assertRemoved("WITHDRAWN");
        assertThat(value("SELECT status FROM membership WHERE id=2")).isEqualTo("WITHDRAWN");
        assertThat(count("SELECT COUNT(*) FROM audit_log WHERE action='LOCKED_EXCEPTION_EDIT' AND reason='소속 해제'"))
                .isEqualTo(1);
    }

    private void lockConfirmedSong() {
        jdbc.update("UPDATE performance SET status='LOCKED' WHERE id=1");
        jdbc.update("UPDATE song SET status='CONFIRMED',assigned_team_id=1 WHERE id=1");
    }

    @Test
    @DisplayName("X-04/API-030 뒤의 LOCKED 공연 검사가 실패하면 먼저 정리한 ACTIVE 공연도 롤백한다")
    void laterLockedFailureRollsBackEarlierPerformance() {
        jdbc.update(
                "INSERT INTO performance (id,club_id,title,preset,status,created_by_user_id) VALUES (2,1,'확정 공연','CUSTOM','LOCKED',1)");
        jdbc.update(
                "INSERT INTO performance_member (id,performance_id,club_id,membership_id,status,source) VALUES (12,2,1,2,'APPROVED','ADMIN')");
        jdbc.update(
                "INSERT INTO team (id,performance_id,name,status,source,created_by_user_id) VALUES (2,2,'확정 팀','APPROVED','ADMIN',1)");
        jdbc.update(
                "INSERT INTO song (id,performance_id,title,status,assigned_team_id,proposed_by_membership_id) VALUES (2,2,'확정 곡','CONFIRMED',2,1)");
        jdbc.update(
                "INSERT INTO song_session_member (song_id,performance_id,performance_member_id,part_id) VALUES (2,2,12,1)");
        assertThatThrownBy(
                        () -> withdrawal.withdraw(1L, 2L, 1L, new MembershipWithdrawalRequest("해제", null, List.of())))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getDetails().get(0).getReason()).isEqualTo("SONG_WITHOUT_PLAYER"));
        assertThat(value("SELECT status FROM membership WHERE id=2")).isEqualTo("ACTIVE");
        assertThat(value("SELECT status FROM performance_member WHERE id=2")).isEqualTo("APPROVED");
        assertThat(count("SELECT leader_member_id FROM team WHERE id=1")).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM team_member WHERE performance_member_id=2"))
                .isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM song_session_member WHERE performance_member_id=2"))
                .isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM audit_log")).isZero();
    }

    @ParameterizedTest
    @CsvSource({
        "SELF_WITHDRAWAL,APPROVED,4", "SELF_WITHDRAWAL,SUBMITTED,4", "SELF_WITHDRAWAL,DRAFT,0",
        "ADMIN_REMOVAL,APPROVED,4", "ADMIN_REMOVAL,SUBMITTED,3", "ADMIN_REMOVAL,DRAFT,0",
        "MEMBERSHIP_WITHDRAWAL,APPROVED,4", "MEMBERSHIP_WITHDRAWAL,SUBMITTED,3", "MEMBERSHIP_WITHDRAWAL,DRAFT,0",
        "SERVICE_WITHDRAWAL,APPROVED,4", "SERVICE_WITHDRAWAL,SUBMITTED,3", "SERVICE_WITHDRAWAL,DRAFT,0"
    })
    @DisplayName("P-06·07/U-05④ 처리 사유·팀 상태별 승계와 DRAFT 삭제 표를 적용한다")
    void successionMatrix(TeamLeaderSuccession.Mode mode, String status, long expectedLeader) {
        jdbc.update("INSERT INTO users (id,name,handle) VALUES (4,'객원 졸업생','guest4')");
        jdbc.update(
                "INSERT INTO membership (id,club_id,user_id,member_type,role,status,join_source) VALUES (4,1,4,'GUEST','MEMBER','GRADUATED','ADMIN_ADDED')");
        jdbc.update(
                "INSERT INTO performance_member (id,performance_id,club_id,membership_id,status,source) VALUES (4,1,1,4,'APPROVED','ADMIN')");
        jdbc.update("INSERT INTO team_member (team_id,performance_id,performance_member_id) VALUES (1,1,4)");
        jdbc.update("UPDATE team_member SET created_at='2026-01-01 00:00:00' WHERE performance_member_id=3");
        jdbc.update("UPDATE team_member SET created_at='2026-01-02 00:00:00' WHERE performance_member_id=4");
        jdbc.update("UPDATE team SET status=? WHERE id=1", status);
        jdbc.update("UPDATE song SET candidate_team_id=1 WHERE id=1");
        Map<Long, Long> specified = new HashMap<>(Map.of(1L, 4L));
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            succession.apply(1L, 2L, specified, mode);
            assignments.removeAssignments(1L, 2L);
        });
        assertThat(specified).isEmpty();
        if (expectedLeader == 0) {
            assertThat(count("SELECT COUNT(*) FROM succession_notice")).isZero();
            assertThat(count("SELECT COUNT(*) FROM team WHERE id=1")).isZero();
            assertThat(count("SELECT COUNT(*) FROM team_member WHERE team_id=1"))
                    .isZero();
            assertThat(count("SELECT COUNT(*) FROM practice_schedule_team WHERE team_id=1"))
                    .isZero();
            assertThat(count("SELECT COUNT(*) FROM song WHERE id=1 AND candidate_team_id IS NULL"))
                    .isEqualTo(1);
        } else {
            assertThat(count("SELECT COUNT(*) FROM succession_notice WHERE notice_type='TEAM_LEADER' AND user_id="
                            + expectedLeader
                            + " AND club_id=1 AND performance_id=1 AND team_id=1 AND confirmed_at IS NULL"))
                    .isEqualTo(1);
            assertThat(count("SELECT leader_member_id FROM team WHERE id=1")).isEqualTo(expectedLeader);
            assertThat(count("SELECT COUNT(*) FROM team_member WHERE performance_member_id=3"))
                    .isEqualTo(1);
        }
        assertThat(count("SELECT COUNT(*) FROM song WHERE id=1")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM practice_schedule WHERE id=1")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM song_session_member WHERE performance_member_id=3"))
                .isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({"APPROVED", "SUBMITTED"})
    @DisplayName("P-06 본인 철회는 남은 팀원이 있으면 후임 지정을 요구한다")
    void selfWithdrawalRequiresSuccessor(String status) {
        jdbc.update("UPDATE team SET status=? WHERE id=1", status);
        assertThatThrownBy(() ->
                        removal.remove(1L, 1L, 2L, 2L, new ParticipantRemovalRequest(null, null, List.of()), false))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getDetails().get(0).getReason()).isEqualTo("SUCCESSOR_REQUIRED"));
        assertThat(count("SELECT leader_member_id FROM team WHERE id=1")).isEqualTo(2);
    }

    @ParameterizedTest
    @CsvSource({"APPROVED,false", "SUBMITTED,false", "APPROVED,true", "SUBMITTED,true"})
    @DisplayName("P-07 관리자 거절·참여 해제는 미지정 시 자동 승계한다")
    void adminAutomaticallySucceeds(String status, boolean reject) {
        jdbc.update("UPDATE team SET status=? WHERE id=1", status);
        if (reject) {
            jdbc.update("UPDATE performance_member SET status='APPLIED' WHERE id=2");
        }
        removal.remove(1L, 1L, 2L, 1L, new ParticipantRemovalRequest("0", null, List.of()), reject);
        assertRemoved(reject ? "REJECTED" : "WITHDRAWN");
    }

    @Test
    @DisplayName("U-06 임원 승계는 후임에게 ADMIN 안내와 감사 기록을 같은 트랜잭션에 남긴다")
    void adminSuccessionNotice() {
        withdrawal.withdraw(1L, 1L, 1L, new MembershipWithdrawalRequest("권한 승계", "2", List.of()));
        assertThat(value("SELECT role FROM membership WHERE id=2")).isEqualTo("CLUB_ADMIN");
        assertThat(
                        count(
                                "SELECT COUNT(*) FROM succession_notice WHERE user_id=2 AND club_id=1"
                                        + " AND notice_type='ADMIN' AND team_id IS NULL AND performance_id IS NULL AND confirmed_at IS NULL"))
                .isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM audit_log WHERE action='ADMIN_SUCCESSION' AND target_id=2"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("U-06 후임이 없어 동아리를 비활성화하면 승계 안내는 만들지 않는다")
    void noSuccessorNoNotice() {
        jdbc.update("UPDATE membership SET member_type='GUEST' WHERE id IN (2,3)");
        withdrawal.withdraw(1L, 1L, 1L, null);
        assertThat(value("SELECT status FROM club WHERE id=1")).isEqualTo("INACTIVE");
        assertThat(count("SELECT COUNT(*) FROM succession_notice")).isZero();
    }

    @Test
    @DisplayName("U-06 팀장 승계가 실패하면 앞서 생성한 승계 안내도 롤백한다")
    void successorNoticeRollsBack() {
        assertThatThrownBy(() -> withdrawal.withdraw(
                        1L,
                        2L,
                        1L,
                        new MembershipWithdrawalRequest(
                                null, null, List.of(new MembershipWithdrawalRequest.TeamSuccessor("99", "3")))))
                .isInstanceOf(BusinessException.class);
        assertThat(count("SELECT COUNT(*) FROM succession_notice")).isZero();
        assertThat(count("SELECT leader_member_id FROM team WHERE id=1")).isEqualTo(2);
        assertThat(value("SELECT status FROM membership WHERE id=2")).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("U-06 남는 팀원이 없어 팀장을 비우는 경우 안내는 만들지 않는다")
    void noTeamSuccessorNoNotice() {
        jdbc.update("DELETE FROM team_member WHERE performance_member_id=3");
        removal.remove(1L, 1L, 2L, 1L, new ParticipantRemovalRequest(null, null, List.of()), false);
        assertThat(count("SELECT COUNT(*) FROM team WHERE id=1 AND leader_member_id IS NULL"))
                .isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM succession_notice")).isZero();
    }

    @Test
    @DisplayName("U-06 임원 승계의 호출 트랜잭션이 롤백되면 역할·안내·감사도 함께 롤백된다")
    void adminNoticeSharesTransaction() {
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            withdrawal.withdraw(1L, 1L, 1L, new MembershipWithdrawalRequest("승계", "2", List.of()));
            assertThat(count("SELECT COUNT(*) FROM succession_notice WHERE notice_type='ADMIN'"))
                    .isEqualTo(1);
            tx.setRollbackOnly();
        });
        assertThat(value("SELECT role FROM membership WHERE id=2")).isEqualTo("MEMBER");
        assertThat(value("SELECT status FROM membership WHERE id=1")).isEqualTo("ACTIVE");
        assertThat(count("SELECT COUNT(*) FROM succession_notice")).isZero();
        assertThat(count("SELECT COUNT(*) FROM audit_log")).isZero();
    }

    @Test
    @DisplayName("감사 규칙 15장: 유형 변경은 MEMBER_TYPE_CHANGED로 변경 전후 값을 저장한다")
    void memberTypeChangeAuditContract() {
        jdbc.update("UPDATE membership SET member_type='GUEST' WHERE id=3");
        membershipStatus.changeType(
                1L, 3L, 1L, com.crane.bandwebbackend.domain.membership.entity.MemberType.REGULAR, "유형 변경");
        assertThat(value("SELECT member_type FROM membership WHERE id=3")).isEqualTo("REGULAR");
        assertThat(count("SELECT COUNT(*) FROM audit_log WHERE action='MEMBER_TYPE_CHANGED' AND target_id=3"
                        + " AND reason='유형 변경' AND JSON_UNQUOTE(JSON_EXTRACT(detail,'$.from'))='GUEST'"
                        + " AND JSON_UNQUOTE(JSON_EXTRACT(detail,'$.to'))='REGULAR'"))
                .isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM audit_log WHERE action='MEMBERSHIP_TYPE_CHANGED'"))
                .isZero();
    }

    @Autowired
    private com.crane.bandwebbackend.domain.membership.service.MembershipStatusService membershipStatus;

    private ParticipantRemovalRequest request() {
        return new ParticipantRemovalRequest("0", "처리 사유", List.of(new ParticipantRemovalRequest.Successor("1", "3")));
    }

    private void assertRemoved(String status) {
        assertThat(value("SELECT status FROM performance_member WHERE id=2")).isEqualTo(status);
        assertThat(count("SELECT leader_member_id FROM team WHERE id=1")).isEqualTo(3);
        assertThat(count("SELECT COUNT(*) FROM team WHERE id=1")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM song WHERE id=1")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM team_member WHERE performance_member_id=2"))
                .isZero();
        assertThat(count("SELECT COUNT(*) FROM song_session_member WHERE performance_member_id=2"))
                .isZero();
        assertThat(count("SELECT COUNT(*) FROM team_member WHERE performance_member_id=3"))
                .isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM song_session_member WHERE performance_member_id=3"))
                .isEqualTo(1);
    }

    private long count(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    private String value(String sql) {
        return jdbc.queryForObject(sql, String.class);
    }
}
