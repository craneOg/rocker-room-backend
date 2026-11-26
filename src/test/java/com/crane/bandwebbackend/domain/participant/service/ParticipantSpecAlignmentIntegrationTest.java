package com.crane.bandwebbackend.domain.participant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crane.bandwebbackend.TestcontainersConfiguration;
import com.crane.bandwebbackend.domain.participant.dto.ParticipantBulkDecisionRequest;
import com.crane.bandwebbackend.domain.participant.dto.ParticipantRemovalRequest;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMemberSource;
import com.crane.bandwebbackend.global.error.BusinessException;
import com.crane.bandwebbackend.global.error.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
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
@Sql({"/performance-flow-cleanup.sql", "/performance-flow-seed.sql"})
@Sql(scripts = "/performance-flow-cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class ParticipantSpecAlignmentIntegrationTest {
    @Autowired
    private ParticipantListReader lists;

    @Autowired
    private ParticipantDecisionService decisions;

    @Autowired
    private ParticipantBulkDecisionService bulk;

    @Autowired
    private ParticipantRemovalService removal;

    @Autowired
    private PerformanceMemberService members;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void applied() {
        jdbc.update("INSERT INTO performance_member (id,performance_id,club_id,membership_id,status,source)"
                + " VALUES (3,1,1,3,'APPLIED','APPLICATION')");
    }

    private String url(Long userId, long id) {
        return lists.read(1L, 1L, userId, null, null, null, 0, 20).getItems().stream()
                .filter(item -> item.getId().equals(Long.toString(id)))
                .findFirst()
                .orElseThrow()
                .getProfileImageUrl();
    }

    @Test
    @DisplayName("U-13/API-053 공개 사용자의 사진은 다른 요청자에게도, 비공개는 본인에게만 보인다")
    void profileImageFollowsPublicFlag() {
        jdbc.update("UPDATE performance_member SET status='APPROVED' WHERE id=3");
        jdbc.update("UPDATE users SET profile_image_url='https://img/a.png' WHERE id=2");
        jdbc.update("UPDATE users SET profile_image_url='https://img/b.png', profile_image_public=FALSE WHERE id=3");

        assertThat(url(3L, 2)).isEqualTo("https://img/a.png");
        assertThat(url(1L, 2)).isEqualTo("https://img/a.png");
        assertThat(url(3L, 3)).isEqualTo("https://img/b.png");
        assertThat(url(1L, 3)).isNull();
        assertThat(url(2L, 3)).isNull();
    }

    @Test
    @DisplayName("G-08/API-057 비활성 동아리에서는 참여 승인이 409 CLUB_INACTIVE이고 저장되지 않는다")
    void approveRejectedInInactiveClub() {
        jdbc.update("UPDATE club SET status='INACTIVE' WHERE id=1");
        assertThatThrownBy(() -> decisions.approve(1L, 1L, 3L, 1L, version(), null))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.STATE_CONFLICT);
                    assertThat(e.getDetails().get(0).getReason()).isEqualTo("CLUB_INACTIVE");
                });
        assertThat(jdbc.queryForObject("SELECT status FROM performance_member WHERE id=3", String.class))
                .isEqualTo("APPLIED");
    }

    @Test
    @DisplayName("G-08/API-144 비활성 동아리의 일괄 결정은 항목별 실패가 아니라 요청 전체 409다")
    void bulkRejectedInInactiveClub() {
        jdbc.update("UPDATE club SET status='INACTIVE' WHERE id=1");
        var request =
                new ParticipantBulkDecisionRequest(List.of("3"), ParticipantBulkDecisionRequest.Decision.APPROVE, null);
        assertThatThrownBy(() -> bulk.decide(1L, 1L, 1L, request))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.STATE_CONFLICT);
                    assertThat(e.getDetails().get(0).getReason()).isEqualTo("CLUB_INACTIVE");
                });
        assertThat(jdbc.queryForObject("SELECT status FROM performance_member WHERE id=3", String.class))
                .isEqualTo("APPLIED");
    }

    @Test
    @DisplayName("API-144 신청 대기가 아닌 참여자는 NOT_APPLIED로 실패한다")
    void bulkNotApplied() {
        var request =
                new ParticipantBulkDecisionRequest(List.of("2"), ParticipantBulkDecisionRequest.Decision.APPROVE, null);
        var result = bulk.decide(1L, 1L, 1L, request);
        assertThat(result.getFailed().get(0).getReason()).isEqualTo("NOT_APPLIED");
    }

    @Test
    @DisplayName("X-07/API-058 LOCKED 공연 참여 거절의 감사 기록에 상태 전·후 값이 남는다")
    void lockedRejectAuditHasFromTo() throws Exception {
        jdbc.update("UPDATE performance SET status='LOCKED' WHERE id=1");
        removal.remove(1L, 1L, 3L, 1L, new ParticipantRemovalRequest(version(), "사유", List.of()), true);
        for (String action : List.of("LOCKED_EXCEPTION_EDIT", "PERFORMANCE_MEMBER_REJECTED")) {
            var json = mapper.readTree(jdbc.queryForObject(
                    "SELECT CAST(detail AS CHAR) FROM audit_log WHERE action=?", String.class, action));
            assertThat(json.get("from").asText()).isEqualTo("APPLIED");
            assertThat(json.get("to").asText()).isEqualTo("REJECTED");
        }
    }

    @Test
    @DisplayName(
            "participation-02/API-055 신청 실패 reason을 PERFORMANCE_NOT_ACTIVE·APPLICATION_CLOSED·DEADLINE_PASSED로 구분한다")
    void applicationReasons() {
        jdbc.update("DELETE FROM performance_member WHERE id=3");
        jdbc.update("UPDATE performance_policy SET application_state='CLOSED' WHERE performance_id=1");
        assertThat(applyReason()).isEqualTo("APPLICATION_CLOSED");
        jdbc.update("UPDATE performance_policy SET application_state='OPEN',"
                + " application_deadline='2000-01-01 00:00:00' WHERE performance_id=1");
        assertThat(applyReason()).isEqualTo("DEADLINE_PASSED");
        jdbc.update("UPDATE performance SET status='DRAFT' WHERE id=1");
        assertThat(applyReason()).isEqualTo("PERFORMANCE_NOT_ACTIVE");
    }

    private String applyReason() {
        try {
            members.register(1L, 1L, 3L, PerformanceMemberSource.APPLICATION, null);
        } catch (BusinessException e) {
            assertThat(e.getErrorCode()).isEqualTo(ErrorCode.STATE_CONFLICT);
            return e.getDetails().get(0).getReason();
        }
        throw new AssertionError("예외가 나야 합니다.");
    }

    private String version() {
        return jdbc.queryForObject("SELECT CAST(version AS CHAR) FROM performance WHERE id=1", String.class);
    }
}
