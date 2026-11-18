package com.crane.bandwebbackend.domain.user.repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

// API 009·134: 팀·공연은 다른 도메인이라 네이티브 SQL로만 읽는다
@Repository
@RequiredArgsConstructor
public class WithdrawalPreviewQueryRepository {

    private final NamedParameterJdbcTemplate jdbc;

    @Getter
    @Builder
    public static class LedTeam {

        private final long clubId;
        private final long performanceId;
        private final String performanceName;
        private final long teamId;
        private final String teamName;
        private final String teamStatus;
        // 팀장의 공연 참여(performance_member) ID
        private final long leaderMemberId;
    }

    // 본인 유효 소속의 참여가 팀장인 진행 중 공연의 팀. TeamLeaderSuccession이 승계하는 범위(확정 2)
    public List<LedTeam> findLedTeams(long userId) {
        return jdbc.query(
                "SELECT p.club_id, p.id AS performance_id, p.title, t.id AS team_id, t.name, t.status, t.leader_member_id"
                        + " FROM team t JOIN performance p ON p.id = t.performance_id"
                        + " JOIN performance_member pm ON pm.id = t.leader_member_id"
                        + " JOIN membership m ON m.id = pm.membership_id"
                        + " WHERE m.user_id = :userId AND m.status IN ('ACTIVE','ON_LEAVE','GRADUATED')"
                        + " AND pm.status IN ('APPLIED','APPROVED') AND p.status IN ('DRAFT','ACTIVE','LOCKED')"
                        + " AND t.status IN ('DRAFT','SUBMITTED','APPROVED')"
                        + " ORDER BY p.club_id, p.id, t.id",
                new MapSqlParameterSource("userId", userId),
                (rs, i) -> LedTeam.builder()
                        .clubId(rs.getLong("club_id"))
                        .performanceId(rs.getLong("performance_id"))
                        .performanceName(rs.getString("title"))
                        .teamId(rs.getLong("team_id"))
                        .teamName(rs.getString("name"))
                        .teamStatus(rs.getString("status"))
                        .leaderMemberId(rs.getLong("leader_member_id"))
                        .build());
    }

    // 본인 참여가 팀장인 팀 전체(상태 무관)의 팀 → 동아리. 지정값을 동아리별로 나눌 때 쓴다
    public Map<Long, Long> teamClubIds(long userId) {
        Map<Long, Long> result = new HashMap<>();
        jdbc.query(
                "SELECT t.id, p.club_id FROM team t JOIN performance p ON p.id = t.performance_id"
                        + " JOIN performance_member pm ON pm.id = t.leader_member_id"
                        + " JOIN membership m ON m.id = pm.membership_id WHERE m.user_id = :userId",
                new MapSqlParameterSource("userId", userId),
                rs -> {
                    result.put(rs.getLong(1), rs.getLong(2));
                });
        return result;
    }

    // 팀의 현재 팀장 소속. 본인이 아닌 사람이 팀장인 팀만(후임이 정해진 팀)
    public Map<Long, Long> newLeaders(Collection<Long> teamIds, long userId) {
        Map<Long, Long> result = new HashMap<>();
        if (teamIds.isEmpty()) {
            return result;
        }
        jdbc.query(
                "SELECT t.id, m.id FROM team t JOIN performance_member pm ON pm.id = t.leader_member_id"
                        + " JOIN membership m ON m.id = pm.membership_id"
                        + " WHERE t.id IN (:ids) AND m.user_id <> :userId",
                new MapSqlParameterSource("ids", teamIds).addValue("userId", userId),
                rs -> {
                    result.put(rs.getLong(1), rs.getLong(2));
                });
        return result;
    }

    // 소속의 표시 이름. 없으면 사용자 이름(145와 같은 규칙)
    public Map<Long, String> displayNames(Collection<Long> membershipIds) {
        Map<Long, String> result = new HashMap<>();
        if (membershipIds.isEmpty()) {
            return result;
        }
        jdbc.query(
                "SELECT m.id, COALESCE(m.display_name, u.name) FROM membership m JOIN users u ON u.id = m.user_id"
                        + " WHERE m.id IN (:ids)",
                new MapSqlParameterSource("ids", membershipIds),
                rs -> {
                    result.put(rs.getLong(1), rs.getString(2));
                });
        return result;
    }
}
