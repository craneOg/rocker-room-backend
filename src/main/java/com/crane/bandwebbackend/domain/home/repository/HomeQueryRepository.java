package com.crane.bandwebbackend.domain.home.repository;

import com.crane.bandwebbackend.global.time.InvitationExpiry;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

// 홈(API 117·137·138·139) 집계 전용 조회 저장소. 다른 도메인 테이블을 네이티브 SQL로 읽기만 한다
@Repository
@RequiredArgsConstructor
public class HomeQueryRepository {

    // 소속이 확정된 활동 상태(C-19). 객원·임원 집계의 기준
    private static final String LIVE = "('ACTIVE','ON_LEAVE')";
    // 진행 중 공연 범위: 초안·진행·잠금
    private static final String IN_PROGRESS = "('DRAFT','ACTIVE','LOCKED')";

    // 내(pm) 기준 가불·투표 대상 곡 조건(별칭 s, p, pm 사용)
    private static final String ANSWERABLE = "s.performance_id = p.id AND s.status = 'PROPOSED'"
            + " AND (s.candidate_team_id IS NULL OR EXISTS (SELECT 1 FROM team_member tm"
            + " WHERE tm.team_id = s.candidate_team_id AND tm.performance_member_id = pm.id))"
            + " AND (:guest = FALSE OR s.proposed_by_membership_id = :mid OR s.candidate_team_id IS NOT NULL)";

    private final NamedParameterJdbcTemplate jdbc;

    @Getter
    @Builder
    public static class MemberCounts {
        private final long pending;
        private final long invited;
        private final long active;
        private final long live;
        private final long admins;
        private final long graduated;
        private final long guests;
    }

    @Getter
    @Builder
    public static class WorkCounts {
        private final long submittedTeams;
        private final long appliedMembers;
        private final long pendingAuditions;
    }

    public MemberCounts memberCounts(long clubId, LocalDateTime utcNow) {
        String sql = "SELECT"
                // 가입 신청 대기
                + " COALESCE(SUM(status = 'PENDING'), 0) AS pending,"
                // 초대 대기: 유효기간(API-010과 같은 InvitationExpiry) 안의 INVITED만
                + " COALESCE(SUM(status = 'INVITED' AND " + InvitationExpiry.expiresAtUtcSql("invited_at")
                + " > :now), 0) AS invited,"
                // 활동 중(휴학 제외)
                + " COALESCE(SUM(status = 'ACTIVE'), 0) AS active,"
                // 활동 + 휴학(객원 포함)
                + " COALESCE(SUM(status IN " + LIVE + "), 0) AS live,"
                // 임원: CLUB_ADMIN이면서 활동·휴학
                + " COALESCE(SUM(role = 'CLUB_ADMIN' AND status IN " + LIVE + "), 0) AS admins,"
                + " COALESCE(SUM(status = 'GRADUATED'), 0) AS graduated,"
                // 객원: 활동·휴학 중인 GUEST(live의 부분집합)
                + " COALESCE(SUM(member_type = 'GUEST' AND status IN " + LIVE + "), 0) AS guests"
                + " FROM membership WHERE club_id = :id";
        return jdbc.queryForObject(
                sql,
                new MapSqlParameterSource("id", clubId).addValue("now", utcNow),
                (rs, i) -> MemberCounts.builder()
                        .pending(rs.getLong("pending"))
                        .invited(rs.getLong("invited"))
                        .active(rs.getLong("active"))
                        .live(rs.getLong("live"))
                        .admins(rs.getLong("admins"))
                        .graduated(rs.getLong("graduated"))
                        .guests(rs.getLong("guests"))
                        .build());
    }

    // 상태별 공연 수. 0건인 상태는 행이 없다
    public Map<String, Long> performanceStatusCounts(long clubId) {
        Map<String, Long> out = new HashMap<>();
        jdbc.query(
                "SELECT status, COUNT(*) AS n FROM performance WHERE club_id = :id GROUP BY status",
                new MapSqlParameterSource("id", clubId),
                rs -> {
                    out.put(rs.getString("status"), rs.getLong("n"));
                });
        return out;
    }

    // 진행 중 공연(DRAFT·ACTIVE·LOCKED)의 처리 대기 건수
    public WorkCounts workCounts(long clubId) {
        String sql = "SELECT"
                // 팀 검토 대기: 제출(SUBMITTED)된 팀
                + " (SELECT COUNT(*) FROM team t JOIN performance p ON p.id = t.performance_id"
                + " WHERE p.club_id = :id AND p.status IN " + IN_PROGRESS + " AND t.status = 'SUBMITTED')"
                + " AS submitted_teams,"
                // 참여 승인 대기: 참여 희망(APPLIED)
                + " (SELECT COUNT(*) FROM performance_member pm JOIN performance p ON p.id = pm.performance_id"
                + " WHERE p.club_id = :id AND p.status IN " + IN_PROGRESS + " AND pm.status = 'APPLIED')"
                + " AS applied_members,"
                // 오디션 심사 대기: 결과가 비어 있는 심사
                + " (SELECT COUNT(*) FROM audition_entry a JOIN performance p ON p.id = a.performance_id"
                + " WHERE p.club_id = :id AND p.status IN " + IN_PROGRESS + " AND a.result IS NULL)"
                + " AS pending_auditions";
        return jdbc.queryForObject(
                sql,
                new MapSqlParameterSource("id", clubId),
                (rs, i) -> WorkCounts.builder()
                        .submittedTeams(rs.getLong("submitted_teams"))
                        .appliedMembers(rs.getLong("applied_members"))
                        .pendingAuditions(rs.getLong("pending_auditions"))
                        .build());
    }

    // 기능 하나의 상태와 마감(UTC)
    @Getter
    @AllArgsConstructor
    public static class Feature {
        private final String state;
        private final LocalDateTime deadline;

        // PerformanceAccess.requireOpen과 같은 식: OPEN이고 마감 전
        public boolean open(LocalDateTime utcNow) {
            return "OPEN".equals(state) && (deadline == null || utcNow.isBefore(deadline));
        }
    }

    // ACTIVE 공연의 기능 상태와 응답 인원(API 138)
    @Getter
    @Builder
    public static class RateRow {
        private final long performanceId;
        private final String title;
        private final Feature playability;
        private final Feature vote;
        private final Feature availability;
        private final long playabilityTotal;
        private final long playabilityAnswered;
        private final long approvedTotal;
        private final long voteAnswered;
        private final long availabilityAnswered;
    }

    // 내 소속 기준 공연 하나의 할 일 판정 재료(API 137·139)
    @Getter
    @Builder
    public static class TodoRow {
        private final long performanceId;
        private final String status;
        private final Long pmId;
        private final String pmStatus;
        private final Feature application;
        private final Feature candidate;
        private final Feature playability;
        private final Feature vote;
        private final Feature availability;
        private final Feature teamSubmission;
        private final String candidateScope;
        private final Integer candidateLimit;
        private final String voteMethod;
        private final Integer voteMaxChoices;
        private final long myProposals;
        private final boolean inTeam;
        private final long unanswered;
        private final long votable;
        private final long myVotes;
        private final long mySlots;
        private final long songCount;
    }

    @Getter
    @AllArgsConstructor
    public static class LedTeam {
        private final long performanceId;
        private final long teamId;
    }

    private static String featureCols(String name) {
        return " COALESCE(pol." + name + "_state, 'DISABLED') AS " + name + "_state, pol." + name + "_deadline AS "
                + name + "_deadline,";
    }

    private static Feature feature(ResultSet rs, String name) throws SQLException {
        return new Feature(rs.getString(name + "_state"), rs.getObject(name + "_deadline", LocalDateTime.class));
    }

    // 응답률 재료: ACTIVE 공연마다 기능 상태와 입력 가능 인원·응답 인원
    public List<RateRow> rateRows(long clubId) {
        String sql = "SELECT p.id, p.title,"
                + featureCols("playability")
                + featureCols("vote")
                + featureCols("availability")
                // 가불 입력 가능 인원: APPLIED+APPROVED(PlayabilityService.saveMine의 requireParticipant)
                + " (SELECT COUNT(*) FROM performance_member pm WHERE pm.performance_id = p.id"
                + " AND pm.status IN ('APPLIED','APPROVED')) AS playability_total,"
                + " (SELECT COUNT(DISTINCT sp.performance_member_id) FROM song_playability sp"
                + " JOIN performance_member pm ON pm.id = sp.performance_member_id"
                + " WHERE sp.performance_id = p.id AND pm.status IN ('APPLIED','APPROVED')) AS playability_answered,"
                // 투표·가능 시간 입력 가능 인원: APPROVED(V-05의 requireApprovedParticipant, AV-01)
                + " (SELECT COUNT(*) FROM performance_member pm WHERE pm.performance_id = p.id"
                + " AND pm.status = 'APPROVED') AS approved_total,"
                + " (SELECT COUNT(DISTINCT v.performance_member_id) FROM song_vote v"
                + " JOIN performance_member pm ON pm.id = v.performance_member_id"
                + " WHERE v.performance_id = p.id AND pm.status = 'APPROVED') AS vote_answered,"
                + " (SELECT COUNT(DISTINCT a.performance_member_id) FROM available_slot a"
                + " JOIN performance_member pm ON pm.id = a.performance_member_id"
                + " WHERE pm.performance_id = p.id AND pm.status = 'APPROVED') AS availability_answered"
                + " FROM performance p LEFT JOIN performance_policy pol ON pol.performance_id = p.id"
                + " WHERE p.club_id = :id AND p.status = 'ACTIVE' ORDER BY p.id";
        return jdbc.query(
                sql,
                new MapSqlParameterSource("id", clubId),
                (rs, i) -> RateRow.builder()
                        .performanceId(rs.getLong("id"))
                        .title(rs.getString("title"))
                        .playability(feature(rs, "playability"))
                        .vote(feature(rs, "vote"))
                        .availability(feature(rs, "availability"))
                        .playabilityTotal(rs.getLong("playability_total"))
                        .playabilityAnswered(rs.getLong("playability_answered"))
                        .approvedTotal(rs.getLong("approved_total"))
                        .voteAnswered(rs.getLong("vote_answered"))
                        .availabilityAnswered(rs.getLong("availability_answered"))
                        .build());
    }

    // 할 일 재료: 주어진 공연마다 내 참여 행·기능 상태·이미 한 수. 판정은 서비스가 한다
    public List<TodoRow> todoRows(long membershipId, boolean guest, List<Long> performanceIds) {
        if (performanceIds.isEmpty()) {
            return List.of();
        }
        String sql = "SELECT p.id, p.status, pm.id AS pm_id, pm.status AS pm_status,"
                + featureCols("application")
                + featureCols("candidate")
                + featureCols("playability")
                + featureCols("vote")
                + featureCols("availability")
                + featureCols("team_submission")
                + " COALESCE(pol.candidate_scope, 'ALL_PARTICIPANTS') AS candidate_scope,"
                + " pol.candidate_limit_per_member, COALESCE(pol.vote_method, 'APPROVAL') AS vote_method,"
                + " pol.vote_max_choices,"
                // S-03: 철회 곡을 뺀 내 제안 수
                + " (SELECT COUNT(*) FROM song s WHERE s.performance_id = p.id"
                + " AND s.proposed_by_membership_id = :mid AND s.status <> 'WITHDRAWN') AS my_proposals,"
                // 후보곡 TEAM_MEMBERS 범위: 반려·철회되지 않은 팀의 팀원만 등록(SongService.create)
                + " EXISTS (SELECT 1 FROM team_member tm JOIN team t ON t.id = tm.team_id"
                + " WHERE tm.performance_member_id = pm.id AND t.status NOT IN ('REJECTED','WITHDRAWN')) AS in_team,"
                // 가불(API-091)·투표(VoteService.requireInputOpen) 대상 곡: PROPOSED만, 팀 후보곡은 그 팀원만,
                // 객원은 제안자·팀 후보곡 팀원만(G-06). 응답 단위는 곡+파트지만 곡에 내 응답 행이 하나라도 있으면 응답한 곡
                + " (SELECT COUNT(*) FROM song s WHERE " + ANSWERABLE
                + " AND NOT EXISTS (SELECT 1 FROM song_playability sp"
                + " WHERE sp.song_id = s.id AND sp.performance_member_id = pm.id)) AS unanswered,"
                // 투표할 수 있는 곡이 하나도 없으면 투표 할 일도 없다
                + " (SELECT COUNT(*) FROM song s WHERE " + ANSWERABLE + ") AS votable,"
                // V-05: PROPOSED 곡에 한 내 투표 수(SongVoteRepository.countByMemberAndSongStatus)
                + " (SELECT COUNT(*) FROM song_vote v JOIN song s ON s.id = v.song_id"
                + " WHERE v.performance_member_id = pm.id AND s.status = 'PROPOSED') AS my_votes,"
                // AV-01: 내가 저장한 가능 시간 슬롯 수
                + " (SELECT COUNT(*) FROM available_slot a WHERE a.performance_member_id = pm.id) AS my_slots,"
                // 확정곡 중 내가 연주자인 곡 수
                + " (SELECT COUNT(DISTINCT ssm.song_id) FROM song_session_member ssm JOIN song s ON s.id = ssm.song_id"
                + " WHERE ssm.performance_member_id = pm.id AND s.status = 'CONFIRMED') AS song_count"
                + " FROM performance p LEFT JOIN performance_policy pol ON pol.performance_id = p.id"
                + " LEFT JOIN performance_member pm ON pm.performance_id = p.id AND pm.membership_id = :mid"
                + " WHERE p.id IN (:ids)";
        return jdbc.query(
                sql,
                new MapSqlParameterSource("mid", membershipId)
                        .addValue("guest", guest)
                        .addValue("ids", performanceIds),
                (rs, i) -> TodoRow.builder()
                        .performanceId(rs.getLong("id"))
                        .status(rs.getString("status"))
                        .pmId(rs.getObject("pm_id", Long.class))
                        .pmStatus(rs.getString("pm_status"))
                        .application(feature(rs, "application"))
                        .candidate(feature(rs, "candidate"))
                        .playability(feature(rs, "playability"))
                        .vote(feature(rs, "vote"))
                        .availability(feature(rs, "availability"))
                        .teamSubmission(feature(rs, "team_submission"))
                        .candidateScope(rs.getString("candidate_scope"))
                        .candidateLimit(rs.getObject("candidate_limit_per_member", Integer.class))
                        .voteMethod(rs.getString("vote_method"))
                        .voteMaxChoices(rs.getObject("vote_max_choices", Integer.class))
                        .myProposals(rs.getLong("my_proposals"))
                        .inTeam(rs.getBoolean("in_team"))
                        .unanswered(rs.getLong("unanswered"))
                        .votable(rs.getLong("votable"))
                        .myVotes(rs.getLong("my_votes"))
                        .mySlots(rs.getLong("my_slots"))
                        .songCount(rs.getLong("song_count"))
                        .build());
    }

    // T-02: 내가 팀장인 DRAFT 팀(제출 전)
    public List<LedTeam> ledDraftTeams(long membershipId, List<Long> performanceIds) {
        if (performanceIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query(
                "SELECT t.performance_id, t.id FROM team t JOIN performance_member pm ON pm.id = t.leader_member_id"
                        + " WHERE pm.membership_id = :mid AND t.performance_id IN (:ids) AND t.status = 'DRAFT'"
                        + " ORDER BY t.id",
                new MapSqlParameterSource("mid", membershipId).addValue("ids", performanceIds),
                (rs, i) -> new LedTeam(rs.getLong("performance_id"), rs.getLong("id")));
    }

    // 내가 속한 APPROVED 팀 이름(공연별, 이름 순)
    public Map<Long, List<String>> approvedTeamNames(long membershipId, List<Long> performanceIds) {
        Map<Long, List<String>> out = new HashMap<>();
        if (performanceIds.isEmpty()) {
            return out;
        }
        jdbc.query(
                "SELECT t.performance_id, t.name FROM team_member tm JOIN team t ON t.id = tm.team_id"
                        + " JOIN performance_member pm ON pm.id = tm.performance_member_id"
                        + " WHERE pm.membership_id = :mid AND t.performance_id IN (:ids) AND t.status = 'APPROVED'"
                        + " ORDER BY t.name",
                new MapSqlParameterSource("mid", membershipId).addValue("ids", performanceIds),
                rs -> {
                    out.computeIfAbsent(rs.getLong("performance_id"), k -> new ArrayList<>())
                            .add(rs.getString("name"));
                });
        return out;
    }

    // 일정 장소(CalendarEvent에 없음)
    public Map<Long, String> scheduleLocations(List<Long> scheduleIds) {
        Map<Long, String> out = new HashMap<>();
        if (scheduleIds.isEmpty()) {
            return out;
        }
        jdbc.query(
                "SELECT id, location FROM practice_schedule WHERE id IN (:ids)",
                new MapSqlParameterSource("ids", scheduleIds),
                rs -> {
                    out.put(rs.getLong("id"), rs.getString("location"));
                });
        return out;
    }
}
