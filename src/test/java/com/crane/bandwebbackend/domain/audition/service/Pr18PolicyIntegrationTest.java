package com.crane.bandwebbackend.domain.audition.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crane.bandwebbackend.TestcontainersConfiguration;
import com.crane.bandwebbackend.domain.audition.dto.AuditionCreateRequest;
import com.crane.bandwebbackend.domain.audition.dto.AuditionReviewRequest;
import com.crane.bandwebbackend.domain.audition.entity.AuditionResult;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMemberSource;
import com.crane.bandwebbackend.domain.participant.service.PerformanceMemberService;
import com.crane.bandwebbackend.domain.schedule.dto.ScheduleCreateRequest;
import com.crane.bandwebbackend.domain.schedule.entity.ScheduleStatus;
import com.crane.bandwebbackend.domain.schedule.entity.ScheduleType;
import com.crane.bandwebbackend.domain.schedule.service.ScheduleCreateService;
import com.crane.bandwebbackend.domain.schedule.service.ScheduleEditService;
import com.crane.bandwebbackend.domain.schedule.service.ScheduleValidationService;
import com.crane.bandwebbackend.global.error.BusinessException;
import com.crane.bandwebbackend.global.error.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Sql({"/performance-flow-cleanup.sql", "/performance-flow-seed.sql"})
@Sql(scripts = "/performance-flow-cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class Pr18PolicyIntegrationTest {
    @Autowired
    private AuditionService auditions;

    @Autowired
    private PerformanceMemberService members;

    @Autowired
    private ScheduleCreateService creates;

    @Autowired
    private ScheduleEditService edits;

    @Autowired
    private ScheduleValidationService validations;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper mapper;

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "INVITED", "REJECTED", "DECLINED", "WITHDRAWN"})
    @DisplayName("API-150 접근 불가 소속·DRAFT·객원 공연만 제외하며 정상 항목과 심사 원본은 보존한다")
    void mineFiltersOnlyInaccessibleRecords(String status) {
        history(status);
        assertThat(auditions.mine(2L, null, null).items())
                .extracting(item -> item.id())
                .containsExactlyInAnyOrder("1", "5");
        assertThat(auditions.mine(2L, 2L, null).items()).isEmpty();
        assertThat(auditions.mine(2L, null, 3L).items()).isEmpty();
        assertThat(auditions.mine(2L, 1L, 1L).items()).hasSize(2);
        assertThat(count("SELECT COUNT(*) FROM audition_entry")).isEqualTo(5);
        // 객원의 유효 참여가 복구되면 기존 이력도 현재 공개 범위에 따라 다시 조회된다.
        jdbc.update("UPDATE performance_member SET status='APPLIED' WHERE id=6");
        assertThat(auditions.mine(2L, null, null).items())
                .extracting(item -> item.id())
                .containsExactlyInAnyOrder("1", "5", "4");
    }

    @Test
    @DisplayName("API-150 기존 휴학·졸업·임원 DRAFT 공개와 비활성 동아리 읽기 규칙을 유지한다")
    void mineRetainsExistingReadPermissions() {
        history("WITHDRAWN");
        for (String status : List.of("ACTIVE", "ON_LEAVE", "GRADUATED")) {
            jdbc.update("UPDATE membership SET status=? WHERE id=2", status);
            assertThat(auditions.mine(2L, null, null).items())
                    .extracting(item -> item.id())
                    .containsExactlyInAnyOrder("1", "5");
        }
        jdbc.update("UPDATE membership SET role='CLUB_ADMIN',status='ACTIVE' WHERE id=2");
        jdbc.update("UPDATE club SET status='INACTIVE' WHERE id=1");
        assertThat(auditions.mine(2L, null, null).items())
                .extracting(item -> item.id())
                .containsExactlyInAnyOrder("1", "3", "5");
    }

    @ParameterizedTest
    @CsvSource({"ACTIVE,false", "ACTIVE,true", "LOCKED,false", "LOCKED,true"})
    @DisplayName("G-08/API-149 비활성 동아리의 최초 심사·정정은 CLUB_INACTIVE이며 심사·감사를 바꾸지 않는다")
    void inactiveReview(String performanceStatus, boolean correction) {
        song(1, 1, 1);
        var created = auditions
                .create(1L, 1L, 1L, new AuditionCreateRequest("SONG", "1", null, List.of(), null, null))
                .get(0);
        if (correction)
            auditions.review(
                    1L,
                    1L,
                    Long.valueOf(created.id()),
                    1L,
                    new AuditionReviewRequest(AuditionResult.PASSED, "원본", null, created.version()));
        String version = auditions
                .pageAdmin(1L, 1L, 1L, null, null, null, null, 0, 20)
                .items()
                .get(0)
                .version();
        long audits = count("SELECT COUNT(*) FROM audit_log");
        jdbc.update("UPDATE performance SET status=? WHERE id=1", performanceStatus);
        jdbc.update("UPDATE club SET status='INACTIVE' WHERE id=1");
        error(
                () -> auditions.review(
                        1L,
                        1L,
                        Long.valueOf(created.id()),
                        1L,
                        new AuditionReviewRequest(AuditionResult.FAILED, "변경", null, version)),
                ErrorCode.STATE_CONFLICT,
                "CLUB_INACTIVE");
        assertThat(jdbc.queryForObject(
                        "SELECT result FROM audition_entry WHERE id=?", String.class, Long.valueOf(created.id())))
                .isEqualTo(correction ? "PASSED" : null);
        assertThat(jdbc.queryForObject(
                        "SELECT comment FROM audition_entry WHERE id=?", String.class, Long.valueOf(created.id())))
                .isEqualTo(correction ? "원본" : null);
        assertThat(count("SELECT COUNT(*) FROM audit_log")).isEqualTo(audits);
    }

    @ParameterizedTest
    @CsvSource({
        "APPLICATION,NEW",
        "APPLICATION,WITHDRAWN",
        "APPLICATION,REJECTED",
        "ADMIN,NEW",
        "ADMIN,WITHDRAWN",
        "ADMIN,REJECTED"
    })
    @DisplayName("G-08 참여 본인 신청·관리자 추가·재신청 모두 비활성 동아리에서는 동일하게 거부한다")
    void inactiveRegistration(PerformanceMemberSource source, String previous) {
        if (!previous.equals("NEW"))
            jdbc.update(
                    "INSERT INTO performance_member (id,performance_id,club_id,membership_id,status,source) VALUES (3,1,1,3,?,'APPLICATION')",
                    previous);
        jdbc.update("UPDATE performance_policy SET application_state='OPEN' WHERE performance_id=1");
        jdbc.update("UPDATE club SET status='INACTIVE' WHERE id=1");
        long rows = count("SELECT COUNT(*) FROM performance_member");
        error(
                () -> members.register(
                        1L,
                        1L,
                        source == PerformanceMemberSource.ADMIN ? 1L : 3L,
                        source,
                        source == PerformanceMemberSource.ADMIN ? 3L : null),
                ErrorCode.STATE_CONFLICT,
                "CLUB_INACTIVE");
        assertThat(count("SELECT COUNT(*) FROM performance_member")).isEqualTo(rows);
        if (!previous.equals("NEW"))
            assertThat(jdbc.queryForObject("SELECT status FROM performance_member WHERE id=3", String.class))
                    .isEqualTo(previous);
        assertThat(count("SELECT COUNT(*) FROM audit_log")).isZero();
    }

    @ParameterizedTest
    @CsvSource({"97,PRACTICE", "96,REHEARSAL"})
    @DisplayName("API-106·107·109·110 자동 생성 최종 제목 100자는 생성·수정·미리보기 모두 허용한다")
    void finalTitleBoundary(int teamLength, ScheduleType type) throws Exception {
        team(teamLength);
        var request = scheduleRequest(null, type);
        validations.create(1L, 1L, 1L, request);
        var result = creates.create(1L, 1L, 1L, request);
        assertThat(result.schedule().getTitle()).hasSize(100);
        var patch = mapper.readTree("{\"version\":\"0\",\"title\":null}");
        validations.edit(1L, 1L, result.schedule().getId(), 1L, patch);
        assertThat(edits.edit(1L, 1L, result.schedule().getId(), 1L, patch)
                        .schedule()
                        .getTitle())
                .hasSize(100);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("API-106·107·109·110 입력·자동 생성 최종 제목이 100자를 넘으면 저장 전에 거부한다")
    void rejectsLongFinalTitle(boolean automatic) throws Exception {
        team(100);
        var request = scheduleRequest(automatic ? null : "제".repeat(101), ScheduleType.PRACTICE);
        error(() -> validations.create(1L, 1L, 1L, request), ErrorCode.VALIDATION_ERROR, "INVALID_VALUE");
        error(() -> creates.create(1L, 1L, 1L, request), ErrorCode.VALIDATION_ERROR, "INVALID_VALUE");
        assertThat(count("SELECT COUNT(*) FROM practice_schedule")).isZero();
        schedule(10, 1, "CONFIRMED", "원본");
        jdbc.update("INSERT INTO practice_schedule_team (practice_schedule_id,team_id,performance_id) VALUES (10,1,1)");
        var patch = mapper.readTree(
                "{\"version\":\"0\",\"title\":" + (automatic ? "null" : "\"" + "제".repeat(101) + "\"") + "}");
        error(() -> validations.edit(1L, 1L, 10L, 1L, patch), ErrorCode.VALIDATION_ERROR, "INVALID_VALUE");
        error(() -> edits.edit(1L, 1L, 10L, 1L, patch), ErrorCode.VALIDATION_ERROR, "INVALID_VALUE");
        assertThat(jdbc.queryForObject("SELECT title FROM practice_schedule WHERE id=10", String.class))
                .isEqualTo("원본");
        assertThat(count("SELECT version FROM practice_schedule WHERE id=10")).isZero();
        assertThat(count("SELECT COUNT(*) FROM audit_log")).isZero();
    }

    @Test
    @DisplayName("G-04/API-113 대상과 충돌 후보의 공연 공개 범위를 적용하고 타 동아리는 익명 충돌을 유지한다")
    void conflictVisibility() throws Exception {
        performance(2, 1, "DRAFT");
        performance(3, 1, "ACTIVE");
        jdbc.update(
                "INSERT INTO performance_member (id,performance_id,club_id,membership_id,status,source) VALUES (20,2,1,2,'APPROVED','ADMIN'),(30,3,1,2,'APPROVED','ADMIN')");
        schedule(10, 1, "CONFIRMED", "대상");
        schedule(20, 2, "CONFIRMED", "비공개 초안 공연");
        schedule(21, 1, "TENTATIVE", "비공개 임시 일정");
        schedule(30, 3, "CONFIRMED", "공개 일정");
        jdbc.update("INSERT INTO club (id,name,created_by) VALUES (2,'다른 동아리',1)");
        jdbc.update(
                "INSERT INTO membership (id,club_id,user_id,role,status,join_source) VALUES (5,2,2,'MEMBER','ACTIVE','JOIN_CODE')");
        performance(4, 2, "ACTIVE");
        jdbc.update(
                "INSERT INTO performance_member (id,performance_id,club_id,membership_id,status,source) VALUES (40,4,2,5,'APPROVED','ADMIN')");
        schedule(40, 4, "CONFIRMED", "다른 동아리 비공개 제목");
        var regular = edits.warningsFor(1L, 1L, 10L, 2L);
        assertThat(regular.stream().filter(item -> item.get("type").equals("OVERLAP")))
                .extracting(item -> item.get("scheduleId"))
                .containsExactly("30");
        String json = mapper.writeValueAsString(regular);
        assertThat(json).doesNotContain("비공개", "\"scheduleId\":\"20\"", "\"scheduleId\":\"21\"");
        var anonymous = regular.stream()
                .filter(item -> item.get("type").equals("OVERLAP_OTHER_CLUB"))
                .findFirst()
                .orElseThrow();
        assertThat(anonymous).doesNotContainKeys("scheduleId", "title", "clubId", "performanceId");
        error(() -> edits.warningsFor(1L, 2L, 20L, 2L), ErrorCode.NOT_FOUND, null);
        assertThat(edits.warningsFor(1L, 1L, 10L, 1L).stream()
                        .filter(item -> item.get("type").equals("OVERLAP")))
                .extracting(item -> item.get("scheduleId"))
                .containsExactlyInAnyOrder("20", "21", "30");
        // 객원도 기존 공연 공개 규칙을 적용하며 본인이 볼 수 없는 초안의 충돌 정보는 받지 않는다.
        jdbc.update("UPDATE membership SET member_type='GUEST' WHERE id=2");
        assertThat(edits.warningsFor(1L, 1L, 10L, 2L).stream()
                        .filter(item -> item.get("type").equals("OVERLAP")))
                .extracting(item -> item.get("scheduleId"))
                .containsExactly("30");
        error(() -> edits.warningsFor(1L, 2L, 20L, 2L), ErrorCode.NOT_FOUND, null);
    }

    private void history(String status) {
        jdbc.update("INSERT INTO club (id,name,created_by) VALUES (2,'이전 동아리',1),(3,'객원 동아리',1)");
        jdbc.update(
                "INSERT INTO membership (id,club_id,user_id,role,status,join_source,member_type,invited_at) VALUES (5,2,2,'MEMBER',?,'JOIN_CODE','REGULAR',UTC_TIMESTAMP(6)),(6,3,2,'MEMBER','ACTIVE','ADMIN_ADDED','GUEST',NULL)",
                status);
        performance(2, 2, "ACTIVE");
        performance(3, 1, "DRAFT");
        performance(4, 3, "ACTIVE");
        song(1, 1, 2);
        song(2, 2, 5);
        song(3, 3, 2);
        song(4, 4, 6);
        jdbc.update(
                "INSERT INTO performance_member (id,performance_id,club_id,membership_id,status,source) VALUES (6,4,3,6,'WITHDRAWN','ADMIN')");
        for (int i = 1; i <= 4; i++)
            jdbc.update(
                    "INSERT INTO audition_entry (id,performance_id,song_id,created_by_user_id) VALUES (?,?,?,1)",
                    i,
                    i,
                    i);
        jdbc.update(
                "INSERT INTO audition_entry (id,performance_id,song_id,performance_member_id,part_id,created_by_user_id) VALUES (5,1,1,2,1,1)");
    }

    private void performance(long id, long clubId, String status) {
        jdbc.update(
                "INSERT INTO performance (id,club_id,title,preset,status,created_by_user_id) VALUES (?,?,'공연','CUSTOM',?,1)",
                id,
                clubId,
                status);
    }

    private void song(long id, long performanceId, long proposer) {
        jdbc.update(
                "INSERT INTO song (id,performance_id,title,status,proposed_by_membership_id) VALUES (?,?,'곡','PROPOSED',?)",
                id,
                performanceId,
                proposer);
    }

    private void team(int length) {
        jdbc.update(
                "INSERT INTO team (id,performance_id,name,status,source,created_by_user_id) VALUES (1,1,?,'APPROVED','ADMIN',1)",
                "팀".repeat(length));
    }

    private void schedule(long id, long performanceId, String status, String title) {
        jdbc.update(
                "INSERT INTO practice_schedule (id,performance_id,title,status,start_at,end_at,for_all_participants,created_by_user_id) VALUES (?,?,?,?,'2099-01-01 00:00:00','2099-01-01 01:00:00',true,1)",
                id,
                performanceId,
                title,
                status);
    }

    private ScheduleCreateRequest scheduleRequest(String title, ScheduleType type) {
        return new ScheduleCreateRequest(
                type,
                ScheduleStatus.TENTATIVE,
                OffsetDateTime.parse("2099-01-01T09:00:00+09:00"),
                OffsetDateTime.parse("2099-01-01T10:00:00+09:00"),
                title,
                null,
                null,
                false,
                List.of("1"),
                List.of());
    }

    private long count(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    private void error(Runnable action, ErrorCode code, String reason) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class, failure -> {
            assertThat(failure.getErrorCode()).isEqualTo(code);
            if (reason != null)
                assertThat(failure.getDetails())
                        .extracting(detail -> detail.getReason())
                        .contains(reason);
        });
    }
}
