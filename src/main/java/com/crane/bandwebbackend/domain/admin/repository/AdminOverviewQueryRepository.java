package com.crane.bandwebbackend.domain.admin.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

// 서비스 관리 운영 현황(API 141)의 집계 전용 저장소. 개인정보 없이 건수만 읽는다. 시각은 UTC로 비교한다
@Repository
@RequiredArgsConstructor
public class AdminOverviewQueryRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public long activeClubs() {
        return one("SELECT COUNT(*) FROM club WHERE status = 'ACTIVE'", new MapSqlParameterSource());
    }

    public long totalUsers() {
        return one("SELECT COUNT(*) FROM users WHERE status <> 'WITHDRAWN'", new MapSqlParameterSource());
    }

    public long activeUsersSince(LocalDateTime since) {
        return one(
                "SELECT COUNT(*) FROM users WHERE status <> 'WITHDRAWN' AND last_active_at >= :since",
                new MapSqlParameterSource("since", since));
    }

    public long ongoingPerformances() {
        return one("SELECT COUNT(*) FROM performance WHERE status = 'ACTIVE'", new MapSqlParameterSource());
    }

    // KST 달력 날짜별 생성 건수. table은 코드 상수만 넘긴다
    public Map<LocalDate, Long> createdPerKstDay(Table table, LocalDateTime startUtc) {
        Map<LocalDate, Long> out = new HashMap<>();
        jdbc.query(
                "SELECT DATE(DATE_ADD(created_at, INTERVAL 9 HOUR)) AS d, COUNT(*) AS n FROM " + table.sql
                        + " WHERE created_at >= :start GROUP BY d",
                new MapSqlParameterSource("start", startUtc),
                rs -> {
                    out.put(rs.getObject("d", LocalDate.class), rs.getLong("n"));
                });
        return out;
    }

    public enum Table {
        USERS("users"),
        CLUBS("club"),
        PERFORMANCES("performance"),
        SCHEDULES("practice_schedule");

        private final String sql;

        Table(String sql) {
            this.sql = sql;
        }
    }

    // 본인 신청(JOIN_CODE·SEARCH)과 임원 직접 추가(ADMIN_ADDED)를 membership 행의 현재 상태·시각으로 센다
    public long requested(LocalDateTime start) {
        return one(
                "SELECT COUNT(*) FROM membership WHERE join_source IN ('JOIN_CODE','SEARCH') AND created_at >= :start",
                new MapSqlParameterSource("start", start));
    }

    public long approved(LocalDateTime start) {
        return one(
                "SELECT COUNT(*) FROM membership WHERE join_source IN ('JOIN_CODE','SEARCH')"
                        + " AND approved_at >= :start",
                new MapSqlParameterSource("start", start));
    }

    public long rejected(LocalDateTime start) {
        return one(
                "SELECT COUNT(*) FROM membership WHERE status = 'REJECTED' AND updated_at >= :start",
                new MapSqlParameterSource("start", start));
    }

    public long invited(LocalDateTime start) {
        return one(
                "SELECT COUNT(*) FROM membership WHERE join_source = 'ADMIN_ADDED' AND invited_at >= :start",
                new MapSqlParameterSource("start", start));
    }

    public long invitationAccepted(LocalDateTime start) {
        return one(
                "SELECT COUNT(*) FROM membership WHERE join_source = 'ADMIN_ADDED' AND approved_at >= :start",
                new MapSqlParameterSource("start", start));
    }

    // 아직 수락하지 않은 초대 중 유효기간(invited_at + 1개월)이 기간 안에 끝난 건
    public long invitationExpired(LocalDateTime start, LocalDateTime now) {
        return one(
                "SELECT COUNT(*) FROM membership WHERE status = 'INVITED'"
                        + " AND DATE_ADD(invited_at, INTERVAL 1 MONTH) >= :start"
                        + " AND DATE_ADD(invited_at, INTERVAL 1 MONTH) < :now",
                new MapSqlParameterSource("start", start).addValue("now", now));
    }

    private long one(String sql, MapSqlParameterSource params) {
        Long n = jdbc.queryForObject(sql, params, Long.class);
        return n == null ? 0 : n;
    }
}
