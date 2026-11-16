package com.crane.bandwebbackend.domain.audition.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crane.bandwebbackend.TestcontainersConfiguration;
import com.crane.bandwebbackend.domain.audition.dto.AuditionCreateRequest;
import com.crane.bandwebbackend.domain.audition.dto.AuditionReviewRequest;
import com.crane.bandwebbackend.domain.audition.entity.AuditionResult;
import com.crane.bandwebbackend.domain.performance.entity.PerformanceStatus;
import com.crane.bandwebbackend.global.error.BusinessException;
import com.crane.bandwebbackend.global.error.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
class AuditionCreateIntegrationTest {
    @Autowired
    private AuditionService auditions;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper mapper;

    @ParameterizedTest
    @EnumSource(
            value = PerformanceStatus.class,
            names = {"DRAFT", "ACTIVE", "LOCKED"})
    @DisplayName("API-148 DRAFT·ACTIVE·LOCKED 공연에는 사유 없이 심사 대기를 등록하고 감사를 기록한다")
    void createsPendingAuditionInAllowedState(PerformanceStatus status) {
        jdbc.update("UPDATE performance SET status=? WHERE id=1", status.name());
        jdbc.update(
                "INSERT INTO song (id,performance_id,title,status,proposed_by_membership_id) VALUES (1,1,'심사곡','PROPOSED',1)");
        assertThat(auditions.create(1L, 1L, 1L, request())).hasSize(1);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM audition_entry WHERE performance_id=1 AND result IS NULL", Long.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action='AUDITION_CREATED'", Long.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM audit_log WHERE action='LOCKED_EXCEPTION_EDIT'", Long.class))
                .isZero();
    }

    @ParameterizedTest
    @EnumSource(
            value = PerformanceStatus.class,
            names = {"FINISHED", "CANCELED"})
    @DisplayName("API-148 FINISHED·CANCELED 공연의 오디션 등록은 409이며 부수 효과가 없다")
    void rejectsCreationInTerminalState(PerformanceStatus status) {
        jdbc.update("UPDATE performance SET status=? WHERE id=1", status.name());
        assertThatThrownBy(() -> auditions.create(1L, 1L, 1L, request()))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.STATE_CONFLICT);
                    assertThat(e.getDetails().get(0).getReason()).isEqualTo("INVALID_STATE");
                });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audition_entry", Long.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log", Long.class))
                .isZero();
    }

    @Test
    @DisplayName("API-148 부적격 지원자는 원래 인덱스 전체를 반환하고 유효 지원자도 생성하지 않는다")
    void reportsAllIneligibleApplicants() {
        song();
        applicant(3, 3, "WITHDRAWN");
        assertThatThrownBy(() -> auditions.create(1L, 1L, 1L, session("1", List.of("2", "3", "999"), null)))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RULE_VIOLATION);
                    assertThat(e.getDetails())
                            .extracting("field", "reason")
                            .containsExactly(
                                    Tuple.tuple("applicantPerformanceMemberIds[1]", "APPLICANT_NOT_ELIGIBLE"),
                                    Tuple.tuple("applicantPerformanceMemberIds[2]", "APPLICANT_NOT_ELIGIBLE"));
                });
        unchanged(0);
    }

    @Test
    @DisplayName("API-148 파트·일정·지원자 업무 오류를 함께 반환하며 생성하지 않는다")
    void reportsAllEligibilityFailures() {
        song();
        jdbc.update("UPDATE part SET is_active=false WHERE id=1");
        assertThatThrownBy(() -> auditions.create(1L, 1L, 1L, session("1", List.of("999"), "999")))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RULE_VIOLATION);
                    assertThat(e.getDetails())
                            .extracting("field", "reason")
                            .containsExactly(
                                    Tuple.tuple("linkedScheduleId", "SCHEDULE_NOT_AUDITION"),
                                    Tuple.tuple("partId", "PART_NOT_ACTIVE"),
                                    Tuple.tuple("applicantPerformanceMemberIds[0]", "APPLICANT_NOT_ELIGIBLE"));
                });
        unchanged(0);
    }

    @Test
    @DisplayName("API-148 중복 지원자 전체를 원래 인덱스로 반환하고 나머지 지원자도 생성하지 않는다")
    void reportsAllPendingApplicants() {
        song();
        applicant(3, 3, "APPROVED");
        applicant(4, 1, "APPLIED");
        auditions.create(1L, 1L, 1L, session("1", List.of("2", "3"), null));
        assertThatThrownBy(() -> auditions.create(1L, 1L, 1L, session("1", List.of("3", "4", "2"), null)))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE);
                    assertThat(e.getDetails())
                            .extracting("field", "reason")
                            .containsExactly(
                                    Tuple.tuple("applicantPerformanceMemberIds[0]", "AUDITION_ALREADY_PENDING"),
                                    Tuple.tuple("applicantPerformanceMemberIds[2]", "AUDITION_ALREADY_PENDING"));
                });
        unchanged(2);
    }

    @Test
    @DisplayName("API-148 곡 심사 중복은 songId details를 반환하며 재심은 허용한다")
    void reportsPendingSongAndAllowsRejudging() {
        song();
        var created = auditions.create(1L, 1L, 1L, request()).get(0);
        assertThatThrownBy(() -> auditions.create(1L, 1L, 1L, request()))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE);
                    assertThat(e.getDetails())
                            .extracting("field", "reason")
                            .containsExactly(Tuple.tuple("songId", "AUDITION_ALREADY_PENDING"));
                });
        unchanged(1);
        auditions.review(
                1L,
                1L,
                Long.valueOf(created.getId()),
                1L,
                new AuditionReviewRequest(AuditionResult.PASSED, null, null, created.getVersion()));
        assertThat(auditions.create(1L, 1L, 1L, request())).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audition_entry", Long.class))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("API-147·148·149 version으로 최초 입력·정정을 하고 이전 version은 409로 거부한다")
    void versionRoundTrip() throws Exception {
        song();
        var created = auditions.create(1L, 1L, 1L, request()).get(0);
        var listed = auditions
                .pageAdmin(1L, 1L, 1L, null, null, null, null, 0, 20)
                .getItems()
                .get(0);
        assertThat(listed.getVersion()).isEqualTo(created.getVersion());
        assertThat(mapper.valueToTree(created).has("version")).isTrue();
        assertThat(mapper.valueToTree(created).has("revision")).isFalse();
        assertThat(mapper.valueToTree(listed).has("version")).isTrue();
        assertThat(mapper.valueToTree(listed).has("revision")).isFalse();
        AuditionReviewRequest body = mapper.readValue(
                "{\"result\":\"PASSED\",\"version\":\"" + created.getVersion() + "\"}", AuditionReviewRequest.class);
        var reviewed = auditions.review(1L, 1L, Long.valueOf(created.getId()), 1L, body);
        assertThat(reviewed.getVersion()).isNotEqualTo(created.getVersion());
        assertThat(mapper.valueToTree(reviewed).has("version")).isTrue();
        assertThat(mapper.valueToTree(reviewed).has("revision")).isFalse();
        assertThatThrownBy(() -> auditions.review(1L, 1L, Long.valueOf(created.getId()), 1L, body))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VERSION_CONFLICT));
        var corrected = auditions.review(
                1L,
                1L,
                Long.valueOf(created.getId()),
                1L,
                new AuditionReviewRequest(AuditionResult.FAILED, "정정", null, reviewed.getVersion()));
        assertThat(corrected.getResult()).isEqualTo(AuditionResult.FAILED);
        assertThat(jdbc.queryForObject("SELECT status FROM song WHERE id=1", String.class))
                .isEqualTo("PROPOSED");
    }

    @Test
    @DisplayName("API-149 LOCKED 사유 누락은 field=reason이며 결과·감사를 변경하지 않는다")
    void reasonRequiredField() {
        song();
        jdbc.update("UPDATE performance SET status='LOCKED' WHERE id=1");
        var created = auditions.create(1L, 1L, 1L, request()).get(0);
        var body = new AuditionReviewRequest(AuditionResult.PASSED, null, null, created.getVersion());
        assertThatThrownBy(() -> auditions.review(1L, 1L, Long.valueOf(created.getId()), 1L, body))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(e.getDetails())
                            .extracting("field", "reason")
                            .containsExactly(Tuple.tuple("reason", "REASON_REQUIRED"));
                });
        unchanged(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audition_entry WHERE result IS NOT NULL", Long.class))
                .isZero();
        auditions.review(
                1L,
                1L,
                Long.valueOf(created.getId()),
                1L,
                new AuditionReviewRequest(AuditionResult.PASSED, null, "결과 입력", created.getVersion()));
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM audit_log WHERE action='LOCKED_EXCEPTION_EDIT'", Long.class))
                .isEqualTo(1);
    }

    private void song() {
        jdbc.update(
                "INSERT INTO song (id,performance_id,title,status,proposed_by_membership_id) VALUES (1,1,'심사곡','PROPOSED',1)");
    }

    private void applicant(long id, long membershipId, String status) {
        jdbc.update(
                "INSERT INTO performance_member (id,performance_id,club_id,membership_id,status,source) VALUES (?,1,1,?,?,'ADMIN')",
                id,
                membershipId,
                status);
    }

    private void unchanged(long count) {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audition_entry", Long.class))
                .isEqualTo(count);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log", Long.class))
                .isEqualTo(count);
    }

    private AuditionCreateRequest session(String partId, List<String> applicants, String scheduleId) {
        return new AuditionCreateRequest("SESSION", "1", partId, applicants, scheduleId, null);
    }

    private AuditionCreateRequest request() {
        return new AuditionCreateRequest("SONG", "1", null, List.of(), null, null);
    }
}
