package com.crane.bandwebbackend.domain.user.repository;

import com.crane.bandwebbackend.domain.user.dto.SuccessionNoticeResponse.Row;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

// API 135·136: succession_notice 읽기·확인 처리. 팀·공연 이름은 다른 도메인이라 네이티브 SQL로만 읽는다.
// 모든 쿼리가 user_id 조건을 가져 타인의 안내는 존재 여부도 드러나지 않는다
@Repository
@RequiredArgsConstructor
public class SuccessionNoticeQueryRepository {

    private static final String SELECT = "SELECT n.id, n.notice_type, n.club_id, c.name AS club_name, n.team_id,"
            + " t.name AS team_name, n.performance_id, p.title AS performance_name, n.created_at, n.confirmed_at"
            + " FROM succession_notice n JOIN club c ON c.id = n.club_id"
            + " LEFT JOIN team t ON t.id = n.team_id LEFT JOIN performance p ON p.id = n.performance_id";

    private static final RowMapper<Row> MAPPER = (rs, i) -> Row.builder()
            .id(rs.getLong("id"))
            .type(rs.getString("notice_type"))
            .clubId(rs.getLong("club_id"))
            .clubName(rs.getString("club_name"))
            .teamId(rs.getObject("team_id", Long.class))
            .teamName(rs.getString("team_name"))
            .performanceId(rs.getObject("performance_id", Long.class))
            .performanceName(rs.getString("performance_name"))
            .createdAt(rs.getObject("created_at", LocalDateTime.class))
            .confirmedAt(rs.getObject("confirmed_at", LocalDateTime.class))
            .build();

    private final NamedParameterJdbcTemplate jdbc;

    // 미확인만, 오래된 순
    public List<Row> findUnconfirmed(long userId) {
        return jdbc.query(
                SELECT + " WHERE n.user_id = :userId AND n.confirmed_at IS NULL ORDER BY n.created_at, n.id",
                new MapSqlParameterSource("userId", userId),
                MAPPER);
    }

    public Optional<Row> findOwned(long noticeId, long userId) {
        return jdbc
                .query(
                        SELECT + " WHERE n.id = :id AND n.user_id = :userId",
                        new MapSqlParameterSource("id", noticeId).addValue("userId", userId),
                        MAPPER)
                .stream()
                .findFirst();
    }

    // 미확인일 때만 기록해 재확인이 최초 확인 시각을 덮어쓰지 않는다
    public void confirm(long noticeId, long userId) {
        jdbc.update(
                "UPDATE succession_notice SET confirmed_at = UTC_TIMESTAMP(6)"
                        + " WHERE id = :id AND user_id = :userId AND confirmed_at IS NULL",
                new MapSqlParameterSource("id", noticeId).addValue("userId", userId));
    }
}
