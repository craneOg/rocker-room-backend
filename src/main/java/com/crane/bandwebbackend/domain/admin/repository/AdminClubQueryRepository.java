package com.crane.bandwebbackend.domain.admin.repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

// 운영 화면(API 118·119·141)이 동아리·소속·공연을 가로질러 집계하는 조회 전용 저장소.
// 공연 엔티티는 다른 도메인이라 네이티브 SQL로만 읽는다. 시각은 UTC LocalDateTime 그대로 읽는다(JVM 시간대 무관)
@Repository
@RequiredArgsConstructor
public class AdminClubQueryRepository {

    // 소속이 확정된 활동 상태. 임원 수·마지막 활동 계산에 쓴다(C-19: 최소 1명 계산은 ACTIVE·ON_LEAVE)
    private static final String LIVE = "('ACTIVE','ON_LEAVE')";

    private static final String BASE = "SELECT c.id, c.name, c.description, c.club_type, c.status, c.is_searchable,"
            + " c.created_by, c.created_at, c.updated_at,"
            + " (SELECT COUNT(*) FROM membership m WHERE m.club_id = c.id AND m.status = 'ACTIVE')"
            + " AS active_member_count,"
            + " (SELECT COUNT(*) FROM membership m WHERE m.club_id = c.id AND m.role = 'CLUB_ADMIN'"
            + " AND m.status IN " + LIVE + ") AS admin_count,"
            + " (SELECT MAX(u.last_active_at) FROM membership m JOIN users u ON u.id = m.user_id"
            + " WHERE m.club_id = c.id AND m.status IN " + LIVE + ") AS last_active_at,"
            + " (SELECT MAX(u.last_active_at) FROM membership m JOIN users u ON u.id = m.user_id"
            + " WHERE m.club_id = c.id AND m.role = 'CLUB_ADMIN' AND m.status IN " + LIVE + ") AS admin_last_active_at,"
            + " (SELECT COUNT(*) FROM performance p WHERE p.club_id = c.id AND p.status = 'ACTIVE')"
            + " AS ongoing_performance_count"
            + " FROM club c";

    private final NamedParameterJdbcTemplate jdbc;

    @Getter
    @Builder
    public static class ClubRow {
        private final Long id;
        private final String name;
        private final String description;
        private final String clubType;
        private final String status;
        private final boolean searchable;
        private final Long createdBy;
        private final LocalDateTime createdAt;
        private final LocalDateTime updatedAt;
        private final long activeMemberCount;
        private final long adminCount;
        private final LocalDateTime lastActiveAt;
        private final LocalDateTime adminLastActiveAt;
        private final long ongoingPerformanceCount;
    }

    @Getter
    @Builder
    public static class CreatorRow {
        private final Long userId;
        private final String name;
        private final String status;
    }

    @Getter
    @Builder
    public static class AdminRow {
        private final Long membershipId;
        private final Long userId;
        private final String displayName;
        private final String handle;
        private final String status;
        private final LocalDateTime lastActiveAt;
    }

    @Getter
    @Builder
    public static class AttentionRow {
        private final Long clubId;
        private final String clubName;
        private final long adminCount;
        private final LocalDateTime adminLastActiveAt;
    }

    private static final RowMapper<ClubRow> CLUB_MAPPER = (rs, i) -> ClubRow.builder()
            .id(rs.getLong("id"))
            .name(rs.getString("name"))
            .description(rs.getString("description"))
            .clubType(rs.getString("club_type"))
            .status(rs.getString("status"))
            .searchable(rs.getBoolean("is_searchable"))
            .createdBy(rs.getLong("created_by"))
            .createdAt(rs.getObject("created_at", LocalDateTime.class))
            .updatedAt(rs.getObject("updated_at", LocalDateTime.class))
            .activeMemberCount(rs.getLong("active_member_count"))
            .adminCount(rs.getLong("admin_count"))
            .lastActiveAt(rs.getObject("last_active_at", LocalDateTime.class))
            .adminLastActiveAt(rs.getObject("admin_last_active_at", LocalDateTime.class))
            .ongoingPerformanceCount(rs.getLong("ongoing_performance_count"))
            .build();

    // 필터는 모두 선택. sortColumn·desc는 서비스가 허용 목록에서 고른 값만 온다
    public List<ClubRow> search(Filter f, String sortColumn, boolean desc, long offset, int size) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String where = where(f, params);
        params.addValue("offset", offset).addValue("size", size);
        String sql = "SELECT * FROM (" + BASE + ") t" + where + " ORDER BY " + sortColumn + (desc ? " DESC" : " ASC")
                + ", id " + (desc ? "DESC" : "ASC") + " LIMIT :size OFFSET :offset";
        return jdbc.query(sql, params, CLUB_MAPPER);
    }

    public long count(Filter f) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String where = where(f, params);
        return jdbc.queryForObject("SELECT COUNT(*) FROM (" + BASE + ") t" + where, params, Long.class);
    }

    @Getter
    @Builder
    public static class Filter {
        private final List<String> statuses;
        private final List<String> clubTypes;
        private final String likePattern; // 이름 부분 일치(LIKE 패턴, 이스케이프 완료). 없으면 null
        private final Long idMatch; // q가 숫자 문자열일 때의 ID 완전 일치. 없으면 null
        private final boolean noAdmin;
        private final boolean inactiveOver180Days;
        private final LocalDateTime inactiveCutoff;
    }

    private static String where(Filter f, MapSqlParameterSource params) {
        List<String> conds = new ArrayList<>();
        if (!f.getStatuses().isEmpty()) {
            conds.add("status IN (:statuses)");
            params.addValue("statuses", f.getStatuses());
        }
        if (!f.getClubTypes().isEmpty()) {
            conds.add("club_type IN (:clubTypes)");
            params.addValue("clubTypes", f.getClubTypes());
        }
        if (f.getLikePattern() != null) {
            String idPart = f.getIdMatch() != null ? " OR id = :idMatch" : "";
            conds.add("(LOWER(name) LIKE LOWER(:like) ESCAPE '\\\\'" + idPart + ")");
            params.addValue("like", f.getLikePattern());
            if (f.getIdMatch() != null) {
                params.addValue("idMatch", f.getIdMatch());
            }
        }
        if (f.isNoAdmin()) {
            conds.add("admin_count = 0");
        }
        if (f.isInactiveOver180Days()) {
            conds.add("(admin_count > 0 AND (admin_last_active_at IS NULL OR admin_last_active_at < :cutoff))");
            params.addValue("cutoff", f.getInactiveCutoff());
        }
        return conds.isEmpty() ? "" : " WHERE " + String.join(" AND ", conds);
    }

    public Optional<ClubRow> findClub(long clubId) {
        return jdbc
                .query(
                        "SELECT * FROM (" + BASE + ") t WHERE id = :id",
                        new MapSqlParameterSource("id", clubId),
                        CLUB_MAPPER)
                .stream()
                .findFirst();
    }

    // 이미 같은 상태면 0행이다. updated_at은 DB의 ON UPDATE가 갱신한다
    public int updateStatus(long clubId, String status) {
        return jdbc.update(
                "UPDATE club SET status = :status WHERE id = :id AND status <> :status",
                new MapSqlParameterSource("status", status).addValue("id", clubId));
    }

    public boolean joinCodeEnabled(long clubId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT join_code IS NOT NULL FROM club WHERE id = :id",
                new MapSqlParameterSource("id", clubId),
                Boolean.class));
    }

    public Optional<CreatorRow> findCreator(long userId) {
        return jdbc
                .query(
                        "SELECT id, name, status FROM users WHERE id = :id",
                        new MapSqlParameterSource("id", userId),
                        (rs, i) -> CreatorRow.builder()
                                .userId(rs.getLong("id"))
                                .name(rs.getString("name"))
                                .status(rs.getString("status"))
                                .build())
                .stream()
                .findFirst();
    }

    public List<AdminRow> findAdmins(long clubId) {
        return jdbc.query(
                "SELECT m.id, m.user_id, COALESCE(m.display_name, u.name) AS display_name, u.handle, m.status,"
                        + " u.last_active_at FROM membership m JOIN users u ON u.id = m.user_id"
                        + " WHERE m.club_id = :id AND m.role = 'CLUB_ADMIN' ORDER BY m.id",
                new MapSqlParameterSource("id", clubId),
                (rs, i) -> AdminRow.builder()
                        .membershipId(rs.getLong("id"))
                        .userId(rs.getLong("user_id"))
                        .displayName(rs.getString("display_name"))
                        .handle(rs.getString("handle"))
                        .status(rs.getString("status"))
                        .lastActiveAt(rs.getObject("last_active_at", LocalDateTime.class))
                        .build());
    }

    // key = "상태" 또는 "GUEST"(현재 소속인 객원). 값은 건수
    public Map<String, Long> memberStatusCounts(long clubId) {
        MapSqlParameterSource p = new MapSqlParameterSource("id", clubId);
        Map<String, Long> out = new java.util.HashMap<>();
        jdbc.query("SELECT status, COUNT(*) AS n FROM membership WHERE club_id = :id GROUP BY status", p, rs -> {
            out.put(rs.getString("status"), rs.getLong("n"));
        });
        out.put(
                "GUEST",
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM membership WHERE club_id = :id AND member_type = 'GUEST'"
                                + " AND status IN " + LIVE,
                        p,
                        Long.class));
        return out;
    }

    public Map<String, Long> performanceStatusCounts(long clubId) {
        Map<String, Long> out = new java.util.HashMap<>();
        jdbc.query(
                "SELECT status, COUNT(*) AS n FROM performance WHERE club_id = :id GROUP BY status",
                new MapSqlParameterSource("id", clubId),
                rs -> {
                    out.put(rs.getString("status"), rs.getLong("n"));
                });
        return out;
    }

    // ACTIVE 동아리 중 임원이 없거나 임원이 모두 cutoff 이전에 마지막으로 활동한 곳. 오래된 순
    public List<AttentionRow> findAttention(LocalDateTime cutoff, int limit) {
        return jdbc.query(
                "SELECT id, name, admin_count, admin_last_active_at FROM (" + BASE + ") t WHERE status = 'ACTIVE'"
                        + " AND (admin_count = 0 OR admin_last_active_at IS NULL OR admin_last_active_at < :cutoff)"
                        + " ORDER BY admin_last_active_at ASC, id ASC LIMIT :limit",
                new MapSqlParameterSource("cutoff", cutoff).addValue("limit", limit),
                (rs, i) -> AttentionRow.builder()
                        .clubId(rs.getLong("id"))
                        .clubName(rs.getString("name"))
                        .adminCount(rs.getLong("admin_count"))
                        .adminLastActiveAt(rs.getObject("admin_last_active_at", LocalDateTime.class))
                        .build());
    }
}
