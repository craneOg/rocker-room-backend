package com.crane.bandwebbackend.domain.performance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crane.bandwebbackend.TestcontainersConfiguration;
import com.crane.bandwebbackend.domain.audition.dto.AuditionCreateRequest;
import com.crane.bandwebbackend.domain.audition.dto.AuditionReviewRequest;
import com.crane.bandwebbackend.domain.audition.entity.AuditionResult;
import com.crane.bandwebbackend.domain.audition.service.AuditionService;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMemberSource;
import com.crane.bandwebbackend.domain.participant.service.PerformanceMemberService;
import com.crane.bandwebbackend.global.error.BusinessException;
import com.crane.bandwebbackend.global.error.ErrorCode;
import java.sql.DriverManager;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Sql({"/performance-flow-cleanup.sql", "/performance-flow-seed.sql"})
@Sql(scripts = "/performance-flow-cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class Pr18ConcurrencyIntegrationTest {
    @Autowired
    private AuditionService auditions;

    @Autowired
    private MySQLContainer<?> mysql;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactions;

    @Autowired
    private PerformanceMemberService members;

    @Autowired
    private PerformanceLockService locks;

    @BeforeEach
    void readyToLock() {
        jdbc.update("UPDATE performance_policy SET application_state='OPEN' WHERE performance_id=1");
        jdbc.update(
                "INSERT INTO team (id,performance_id,name,status,source,created_by_user_id) VALUES (1,1,'팀','APPROVED','ADMIN',1)");
        jdbc.update(
                "INSERT INTO song (id,performance_id,title,status,setlist_order,assigned_team_id,proposed_by_membership_id) VALUES (1,1,'곡','CONFIRMED',1,1,1)");
        jdbc.update(
                "INSERT INTO song_session_member (song_id,performance_id,performance_member_id,part_id) VALUES (1,1,2,1)");
    }

    @ParameterizedTest
    @ValueSource(strings = {"NEW", "WITHDRAWN", "REJECTED"})
    @DisplayName("0.4/L-05 REPEATABLE READ의 이전 스냅샷이 있어도 신규·재신청 참여자를 확정 검사에서 놓치지 않는다")
    void lockSeesParticipantCommittedAfterSnapshot(String previous) throws Exception {
        if (!previous.equals("NEW")) {
            jdbc.update(
                    "INSERT INTO performance_member (id,performance_id,club_id,membership_id,status,source) VALUES (3,1,1,3,?,'APPLICATION')",
                    previous);
        }
        CountDownLatch snapshotReady = new CountDownLatch(1);
        CountDownLatch registrationCommitted = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        try {
            var locking = executor.submit(() -> tx().executeWithoutResult(status -> {
                assertThat(jdbc.queryForObject("SELECT @@transaction_isolation", String.class))
                        .isEqualTo("REPEATABLE-READ");
                jdbc.queryForObject("SELECT COUNT(*) FROM performance_member", Long.class);
                snapshotReady.countDown();
                await(registrationCommitted);
                // 일반 SELECT는 여전히 신규·재신청 전 스냅샷을 읽는다는 것을 함께 재현한다.
                assertThat(jdbc.queryForObject(
                                "SELECT COUNT(*) FROM performance_member WHERE status='APPLIED'", Long.class))
                        .isZero();
                assertThatThrownBy(() -> locks.lock(1L, 1L, 1L, "0", true))
                        .isInstanceOfSatisfying(BusinessException.class, error -> {
                            assertThat(error.getErrorCode()).isEqualTo(ErrorCode.RULE_VIOLATION);
                            assertThat(error.getDetails())
                                    .extracting(detail -> detail.getReason())
                                    .contains("APPLIED_PARTICIPANT");
                        });
                status.setRollbackOnly();
            }));
            await(snapshotReady);
            tx().executeWithoutResult(
                            status -> members.register(1L, 1L, 3L, PerformanceMemberSource.APPLICATION, null));
            registrationCommitted.countDown();
            locking.get(15, TimeUnit.SECONDS);
            assertThat(jdbc.queryForObject("SELECT status FROM performance WHERE id=1", String.class))
                    .isEqualTo("ACTIVE");
        } finally {
            registrationCommitted.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(15, TimeUnit.SECONDS)).isTrue();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"SONG", "SESSION"})
    @DisplayName("A-06 같은 대상의 동시 등록은 이전 스냅샷에서도 최신 잠금 읽기로 한 건만 생성한다")
    void duplicateRegistrationUsesCurrentRead(String type) throws Exception {
        var request = new AuditionCreateRequest(
                type,
                "1",
                type.equals("SESSION") ? "1" : null,
                type.equals("SESSION") ? List.of("2") : List.of(),
                null,
                null);
        BusinessException failure =
                blocked(() -> auditions.create(1L, 1L, 1L, request), () -> auditions.create(1L, 1L, 1L, request));
        assertThat(failure).isNotNull();
        assertThat(failure.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE);
        assertThat(failure.getDetails())
                .extracting(detail -> detail.getReason())
                .containsExactly("AUDITION_ALREADY_PENDING");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audition_entry", Long.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action='AUDITION_CREATED'", Long.class))
                .isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"NEW", "WITHDRAWN", "REJECTED"})
    @DisplayName("0.4/L-05 신청·재신청이 먼저면 확정이 같은 공연 잠금을 기다린 뒤 APPLIED를 발견한다")
    void registrationBeforeLock(String previous) throws Exception {
        previousParticipation(previous);
        BusinessException failure = blocked(
                () -> members.register(1L, 1L, 3L, PerformanceMemberSource.APPLICATION, null),
                () -> locks.lock(1L, 1L, 1L, "0", true));
        assertThat(failure).isNotNull();
        assertThat(failure.getErrorCode()).isEqualTo(ErrorCode.RULE_VIOLATION);
        assertThat(failure.getDetails())
                .extracting(detail -> detail.getReason())
                .contains("APPLIED_PARTICIPANT");
        assertThat(jdbc.queryForObject("SELECT status FROM performance WHERE id=1", String.class))
                .isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log", Long.class))
                .isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"NEW", "WITHDRAWN", "REJECTED"})
    @DisplayName("0.4/L-05 확정이 먼저면 신규·재신청이 기다린 뒤 LOCKED 상태를 보고 거부된다")
    void lockBeforeRegistration(String previous) throws Exception {
        previousParticipation(previous);
        BusinessException failure = blocked(
                () -> locks.lock(1L, 1L, 1L, "0", true),
                () -> members.register(1L, 1L, 3L, PerformanceMemberSource.APPLICATION, null));
        assertThat(failure).isNotNull();
        assertThat(failure.getErrorCode()).isEqualTo(ErrorCode.STATE_CONFLICT);
        assertThat(jdbc.queryForObject("SELECT status FROM performance WHERE id=1", String.class))
                .isEqualTo("LOCKED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM performance_member WHERE status='APPLIED'", Long.class))
                .isZero();
    }

    @Test
    @DisplayName("0.4/L-06 심사가 먼저면 확정이 기다린 뒤 최신 심사 결과를 읽고 대기 오류 없이 확정한다")
    void reviewBeforeLock() throws Exception {
        var created = auditions.create(1L, 1L, 1L, songRequest()).get(0);
        BusinessException failure = blocked(
                () -> auditions.review(
                        1L,
                        1L,
                        Long.valueOf(created.getId()),
                        1L,
                        new AuditionReviewRequest(AuditionResult.PASSED, null, null, created.getVersion())),
                () -> locks.lock(1L, 1L, 1L, "0", true));
        assertThat(failure).isNull();
        assertThat(jdbc.queryForObject("SELECT status FROM performance WHERE id=1", String.class))
                .isEqualTo("LOCKED");
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM audit_log WHERE action='LOCKED_EXCEPTION_EDIT'", Long.class))
                .isZero();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("0.4/X-02·07 확정이 먼저면 심사 정정이 기다린 뒤 LOCKED 사유와 예외 감사를 적용한다")
    void lockBeforeCorrection(boolean reason) throws Exception {
        var created = auditions.create(1L, 1L, 1L, songRequest()).get(0);
        var reviewed = auditions.review(
                1L,
                1L,
                Long.valueOf(created.getId()),
                1L,
                new AuditionReviewRequest(AuditionResult.PASSED, null, null, created.getVersion()));
        BusinessException failure = blocked(
                () -> locks.lock(1L, 1L, 1L, "0", true),
                () -> auditions.review(
                        1L,
                        1L,
                        Long.valueOf(created.getId()),
                        1L,
                        new AuditionReviewRequest(
                                AuditionResult.FAILED, null, reason ? "정정" : null, reviewed.getVersion())));
        if (reason) {
            assertThat(failure).isNull();
        } else {
            assertThat(failure).isNotNull();
            assertThat(failure.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
            assertThat(failure.getDetails())
                    .extracting(detail -> detail.getReason())
                    .containsExactly("REASON_REQUIRED");
        }
        assertThat(jdbc.queryForObject(
                        "SELECT result FROM audition_entry WHERE id=?", String.class, Long.valueOf(created.getId())))
                .isEqualTo(reason ? "FAILED" : "PASSED");
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM audit_log WHERE action='LOCKED_EXCEPTION_EDIT'", Long.class))
                .isEqualTo(reason ? 1 : 0);
    }

    private AuditionCreateRequest songRequest() {
        return new AuditionCreateRequest("SONG", "1", null, List.of(), null, null);
    }

    private void previousParticipation(String previous) {
        if (!previous.equals("NEW")) {
            jdbc.update(
                    "INSERT INTO performance_member (id,performance_id,club_id,membership_id,status,source) VALUES (3,1,1,3,?,'APPLICATION')",
                    previous);
        }
    }

    /** 두 실제 RR 트랜잭션을 열고 MySQL이 두 번째 연결의 잠금 대기를 관측한 뒤 첫 커밋을 허용한다. */
    private BusinessException blocked(Runnable firstAction, Runnable secondAction) throws Exception {
        CountDownLatch firstWritten = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicLong firstConnection = new AtomicLong();
        AtomicLong secondConnection = new AtomicLong();
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> tx().executeWithoutResult(status -> {
                firstConnection.set(jdbc.queryForObject("SELECT CONNECTION_ID()", Long.class));
                firstAction.run();
                firstWritten.countDown();
                await(release);
            }));
            await(firstWritten);
            var second = executor.submit(() -> tx().execute(status -> {
                secondConnection.set(jdbc.queryForObject("SELECT CONNECTION_ID()", Long.class));
                assertThat(jdbc.queryForObject("SELECT @@transaction_isolation", String.class))
                        .isEqualTo("REPEATABLE-READ");
                jdbc.queryForObject("SELECT COUNT(*) FROM audition_entry", Long.class);
                jdbc.queryForObject("SELECT COUNT(*) FROM performance_member", Long.class);
                secondStarted.countDown();
                try {
                    secondAction.run();
                    return null;
                } catch (BusinessException exception) {
                    status.setRollbackOnly();
                    return exception;
                }
            }));
            await(secondStarted);
            assertThat(secondConnection.get()).isNotEqualTo(firstConnection.get());
            awaitDatabaseLock(secondConnection.get());
            release.countDown();
            first.get(15, TimeUnit.SECONDS);
            return second.get(15, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(15, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void awaitDatabaseLock(long connectionId) throws Exception {
        // root는 테스트 컨테이너의 잠금 관측에만 사용한다. 업무 호출은 실제 애플리케이션 연결을 사용한다.
        try (var connection = DriverManager.getConnection(mysql.getJdbcUrl(), "root", mysql.getPassword());
                var statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM information_schema.innodb_trx WHERE trx_mysql_thread_id=? AND trx_state='LOCK WAIT'")) {
            statement.setLong(1, connectionId);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
            while (System.nanoTime() < deadline) {
                try (var rows = statement.executeQuery()) {
                    rows.next();
                    if (rows.getInt(1) > 0) return;
                }
                Thread.sleep(10);
            }
            throw new AssertionError("두 번째 MySQL 연결의 실제 잠금 대기를 관측하지 못했습니다.");
        }
    }

    private TransactionTemplate tx() {
        TransactionTemplate tx = new TransactionTemplate(transactions);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        tx.setTimeout(20);
        return tx;
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
