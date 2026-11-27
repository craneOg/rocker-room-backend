package com.crane.bandwebbackend.domain.performance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crane.bandwebbackend.TestcontainersConfiguration;
import com.crane.bandwebbackend.domain.participant.service.PerformanceAccessService;
import com.crane.bandwebbackend.domain.performance.dto.PerformanceCreateRequest;
import com.crane.bandwebbackend.domain.performance.dto.PerformanceListItem;
import com.crane.bandwebbackend.domain.performance.dto.VoteMethodChangeRequest;
import com.crane.bandwebbackend.domain.performance.entity.PerformancePreset;
import com.crane.bandwebbackend.domain.performance.entity.VoteMethod;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Sql({"/performance-flow-cleanup.sql", "/performance-flow-seed.sql"})
@Sql(scripts = "/performance-flow-cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class PerformanceSpecAlignmentIntegrationTest {
    @Autowired
    private PerformanceListReader lists;

    @Autowired
    private PerformanceDetailReader details;

    @Autowired
    private PerformanceAccessService access;

    @Autowired
    private PerformanceCreateService creates;

    @Autowired
    private LockReadinessReader readiness;

    @Autowired
    private PerformanceBasicEditService basicEdits;

    @Autowired
    private PerformancePolicyEditService policyEdits;

    @Autowired
    private VoteMethodChangeService votes;

    @Autowired
    private PerformanceLockService locks;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void extraUsers() {
        jdbc.update("INSERT INTO users (id,name,handle) VALUES (21,'객원갑','guest_21'),(22,'휴학생','leave_22')");
        jdbc.update("INSERT INTO membership (id,club_id,user_id,member_type,role,status,join_source) VALUES"
                + " (21,1,21,'GUEST','MEMBER','ACTIVE','ADMIN_ADDED'),"
                + " (22,1,22,'REGULAR','MEMBER','ON_LEAVE','JOIN_CODE')");
        jdbc.update("UPDATE performance_policy SET application_state='OPEN' WHERE performance_id=1");
    }

    private List<String> features(String status) {
        jdbc.update("UPDATE performance SET status=? WHERE id=1", status);
        return lists
                .page(1L, 1L, null, null, null, null, "createdAt,desc", PageRequest.of(0, 10))
                .getContent()
                .get(0)
                .getOpenFeatures()
                .stream()
                .map(PerformanceListItem.OpenFeature::getFeature)
                .toList();
    }

    @Test
    @DisplayName("performance-03/API-037 ACTIVE 공연만 열린 기능을 최대 2개, 명세 순서로 낸다")
    void openFeaturesOnlyActiveAndLimitedToTwo() {
        assertThat(features("ACTIVE")).containsExactly("APPLICATION", "TEAM_APPLICATION");
        assertThat(features("DRAFT")).isEmpty();
        assertThat(features("LOCKED")).isEmpty();
        assertThat(features("FINISHED")).isEmpty();
    }

    @Test
    @DisplayName("performance-04/API-039 GUEST 상세에는 counts·nextSchedule이 없다")
    void guestDetailOmitsComputedValues() {
        jdbc.update("INSERT INTO performance_member (id,performance_id,club_id,membership_id,status,source)"
                + " VALUES (21,1,1,21,'APPROVED','ADMIN')");
        jdbc.update(
                "INSERT INTO practice_schedule (id,performance_id,title,status,start_at,end_at,for_all_participants,created_by_user_id)"
                        + " VALUES (31,1,'전체','CONFIRMED','2099-01-02 00:00:00','2099-01-02 01:00:00',TRUE,1)");

        var guest = details.read(1L, 1L, 21L);
        assertThat(guest.getCounts()).isNull();
        assertThat(guest.getNextSchedule()).isNull();
        assertThat(details.read(1L, 1L, 2L).getCounts()).isNotNull();
    }

    @Test
    @DisplayName("performance-04/E-07 임원 상세의 nextSchedule은 임시 일정까지, 부원은 확정만 본다")
    void nextScheduleIncludesTentativeForAdminOnly() {
        jdbc.update(
                "INSERT INTO practice_schedule (id,performance_id,title,status,start_at,end_at,for_all_participants,created_by_user_id)"
                        + " VALUES (31,1,'임시','TENTATIVE','2099-01-01 00:00:00','2099-01-01 01:00:00',TRUE,1),"
                        + " (32,1,'취소','CANCELED','2098-12-31 00:00:00','2098-12-31 01:00:00',TRUE,1),"
                        + " (33,1,'확정','CONFIRMED','2099-01-02 00:00:00','2099-01-02 01:00:00',TRUE,1)");

        assertThat(details.read(1L, 1L, 1L).getNextSchedule().getId()).isEqualTo("31");
        assertThat(details.read(1L, 1L, 2L).getNextSchedule().getId()).isEqualTo("33");
    }

    @Test
    @DisplayName("performance-09 DRAFT 공연의 하위 조회는 부원 404, 임원 통과")
    void draftIsHiddenFromMembers() {
        jdbc.update("UPDATE performance SET status='DRAFT' WHERE id=1");
        assertThatThrownBy(() -> access.get(1L, 1L, 2L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
        assertThat(access.get(1L, 1L, 1L)).isNotNull();
    }

    @Test
    @DisplayName("G-06 철회·거절된 객원은 404, 신청·승인 객원은 통과")
    void guestNeedsAppliedOrApproved() {
        jdbc.update("INSERT INTO performance_member (id,performance_id,club_id,membership_id,status,source)"
                + " VALUES (21,1,1,21,'WITHDRAWN','APPLICATION')");
        assertThatThrownBy(() -> access.get(1L, 1L, 21L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
        jdbc.update("UPDATE performance_member SET status='APPLIED' WHERE id=21");
        assertThat(access.get(1L, 1L, 21L)).isNotNull();
    }

    @Test
    @DisplayName("G-05 소속 행이 있는데 비활성(PENDING)이면 403, 소속 행이 없으면 404")
    void inactiveMembershipIsForbiddenNotNotFound() {
        jdbc.update("UPDATE membership SET status='PENDING' WHERE id=3");
        assertThatThrownBy(() -> access.get(1L, 1L, 3L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        jdbc.update("INSERT INTO users (id,name,handle) VALUES (23,'외부인','out_23')");
        assertThatThrownBy(() -> access.get(1L, 1L, 23L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    @DisplayName("F-02/API-038 BAND 공연 생성 시 휴학 정회원도 APPROVED 참여자·기본 팀원이 된다")
    void bandPresetRegistersOnLeaveMembers() {
        var created =
                creates.create(1L, 1L, new PerformanceCreateRequest("밴드 공연", null, null, PerformancePreset.BAND, null));
        long performanceId = Long.parseLong(created.getId());
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM performance_member WHERE performance_id=? AND membership_id=22"
                                + " AND status='APPROVED'",
                        Long.class,
                        performanceId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM team_member tm JOIN performance_member pm ON pm.id=tm.performance_member_id"
                                + " WHERE tm.performance_id=? AND pm.membership_id=22",
                        Long.class,
                        performanceId))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("F-02 휴학 임원이 BAND 공연을 만들면 팀장으로 지정된다")
    void onLeaveAdminBecomesLeader() {
        jdbc.update("UPDATE membership SET status='ON_LEAVE' WHERE id=1");
        var created =
                creates.create(1L, 1L, new PerformanceCreateRequest("밴드 공연", null, null, PerformancePreset.BAND, null));
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM team WHERE performance_id=? AND leader_member_id IS NOT NULL",
                        Long.class,
                        Long.parseLong(created.getId())))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("performance-12/L-01·04·05·10 점검 message에는 곡명·팀명·이름이 있고 내부 ID는 없다")
    void checklistMessagesCarryNames() {
        jdbc.update("INSERT INTO team (id,performance_id,name,status,source,created_by_user_id)"
                + " VALUES (71,1,'제출팀','SUBMITTED','ADMIN',1)");
        jdbc.update("INSERT INTO performance_member (id,performance_id,club_id,membership_id,status,source)"
                + " VALUES (72,1,1,3,'APPLIED','APPLICATION')");
        jdbc.update("INSERT INTO song (id,performance_id,title,status,setlist_order,proposed_by_membership_id) VALUES"
                + " (73,1,'첫째곡','CONFIRMED',1,1),(74,1,'둘째곡','CONFIRMED',2,1),(75,1,'순서없는곡','CONFIRMED',NULL,1)");
        jdbc.update("INSERT INTO song_session_member (song_id,performance_id,performance_member_id,part_id)"
                + " VALUES (73,1,2,1),(74,1,2,1)");

        var items = LockChecklistEvaluator.evaluate(readiness.read(1L)).getItems();
        String l01 = message(items, "L-01");
        String l04 = message(items, "L-04");
        String l05 = message(items, "L-05");
        String l10 = message(items, "L-10");

        assertThat(l01).contains("순서없는곡");
        assertThat(l04).contains("제출팀");
        assertThat(l05).contains("부원B");
        assertThat(l10).contains("부원A").contains("1·2번 연속").doesNotContainPattern("ID");
        assertThat(l10).doesNotContainPattern("\\b2\\b(?!·)");
    }

    private static String message(
            List<com.crane.bandwebbackend.domain.performance.dto.LockChecklistItem> items, String id) {
        return items.stream()
                .filter(item -> item.getCheckId().equals(id))
                .findFirst()
                .orElseThrow()
                .getMessage();
    }

    @Test
    @DisplayName("performance-10/X-07 LOCKED 기본 정보 수정 감사에 제목·장소 전·후 값이 남는다")
    void basicEditAuditHasFromTo() throws Exception {
        jdbc.update("UPDATE performance SET status='LOCKED', location='구장소' WHERE id=1");
        basicEdits.update(
                1L,
                1L,
                1L,
                mapper.readTree("{\"version\":\"" + version() + "\",\"title\":\"새제목\",\"location\":\"새장소\","
                        + "\"reason\":\"사유\"}"));
        String detail = jdbc.queryForObject(
                "SELECT CAST(detail AS CHAR) FROM audit_log WHERE action='LOCKED_EXCEPTION_EDIT'", String.class);
        var json = mapper.readTree(detail);
        assertThat(json.at("/from/title").asText()).isEqualTo("정기공연");
        assertThat(json.at("/to/title").asText()).isEqualTo("새제목");
        assertThat(json.at("/from/location").asText()).isEqualTo("구장소");
        assertThat(json.at("/to/location").asText()).isEqualTo("새장소");
        assertThat(json.at("/from").has("performDate")).isFalse();
    }

    @Test
    @DisplayName("performance-10/API-042 정책 수정 감사에 바뀐 항목의 전·후 값이 남는다")
    void policyEditAuditHasFromTo() throws Exception {
        jdbc.update("UPDATE performance_policy SET practice_season_start='2026-12-01', practice_season_end='2026-12-31'"
                + " WHERE performance_id=1");
        policyEdits.update(1L, 1L, 1L, mapper.readTree("{\"version\":\"" + version() + "\",\"voteMaxChoices\":3}"));
        var json = mapper.readTree(jdbc.queryForObject(
                "SELECT CAST(detail AS CHAR) FROM audit_log WHERE action='PERFORMANCE_POLICY_UPDATED'", String.class));
        assertThat(json.at("/from/voteMaxChoices").asText()).isEqualTo("1");
        assertThat(json.at("/to/voteMaxChoices").asText()).isEqualTo("3");
        assertThat(json.at("/to").size()).isEqualTo(1);
    }

    @Test
    @DisplayName("performance-10·05/API-044 투표 방식 감사에 전·후 방식이 남고 경고 type은 VOTES_DELETED다")
    void voteMethodAuditAndWarningType() {
        jdbc.update("INSERT INTO song (id,performance_id,title,status,proposed_by_membership_id)"
                + " VALUES (81,1,'곡','PROPOSED',1)");
        jdbc.update("INSERT INTO song_vote (song_id,performance_id,performance_member_id,score,submitted_by_user_id)"
                + " VALUES (81,1,2,1,1)");
        var request = new VoteMethodChangeRequest(version(), VoteMethod.SCORE, null, "방식 변경");
        assertThatThrownBy(() -> votes.change(1L, 1L, 1L, request))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CONFIRMATION_REQUIRED);
                    assertThat(e.getWarnings().get(0)).containsEntry("type", "VOTES_DELETED");
                });
        votes.change(1L, 1L, 1L, new VoteMethodChangeRequest(version(), VoteMethod.SCORE, true, "방식 변경"));
        String detail = jdbc.queryForObject(
                "SELECT CAST(detail AS CHAR) FROM audit_log WHERE action='VOTE_METHOD_CHANGED'", String.class);
        assertThat(detail).contains("\"from\": \"APPROVAL\"").contains("\"to\": \"SCORE\"");
    }

    @Test
    @DisplayName("performance-10/API-051 셋리스트 확정 감사에 곡 ID·순서가 남는다")
    void lockAuditHasOrders() throws Exception {
        jdbc.update("INSERT INTO team (id,performance_id,name,status,source,created_by_user_id)"
                + " VALUES (1,1,'팀','APPROVED','ADMIN',1)");
        jdbc.update(
                "INSERT INTO song (id,performance_id,title,status,setlist_order,assigned_team_id,proposed_by_membership_id)"
                        + " VALUES (1,1,'선정곡','CONFIRMED',1,1,1)");
        jdbc.update("INSERT INTO song_session_member (song_id,performance_id,performance_member_id,part_id)"
                + " VALUES (1,1,2,1)");
        locks.lock(1L, 1L, 1L, version(), true);
        var json = mapper.readTree(jdbc.queryForObject(
                "SELECT CAST(detail AS CHAR) FROM audit_log WHERE action='SETLIST_CONFIRMED'", String.class));
        assertThat(json.at("/orders/0/songId").asText()).isEqualTo("1");
        assertThat(json.at("/orders/0/order").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("performance-06/API-152 FINISHED가 아닌 공연을 확정으로 되돌리면 reason NOT_FINISHED")
    void revertToLockedRequiresFinished() {
        assertThatThrownBy(() -> transitions.revertToLocked(1L, 1L, 1L, "사유"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getDetails().get(0).getReason()).isEqualTo("NOT_FINISHED"));
    }

    @Autowired
    private PerformanceTransitionService transitions;

    private String version() {
        return jdbc.queryForObject("SELECT CAST(version AS CHAR) FROM performance WHERE id=1", String.class);
    }
}
