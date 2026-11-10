package com.crane.bandwebbackend.domain.user.repository;

import com.crane.bandwebbackend.domain.user.dto.MyMembershipsResponse;
import com.crane.bandwebbackend.global.time.InvitationExpiry;
import java.time.LocalDateTime;
import java.util.ArrayList;
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

// API 10 전용 조회 저장소. 본인 소속(+동아리 요약, 파트)만 읽고 바꾸지 않는다.
// membership·club·part는 다른 도메인이라 엔티티·저장소 대신 네이티브 SQL로 읽는다. 시각은 UTC LocalDateTime 그대로
@Repository
@RequiredArgsConstructor
public class MyMembershipQueryRepository {

    private final NamedParameterJdbcTemplate jdbc;

    @Getter
    @Builder
    public static class Row {
        private final Long id;
        private final Long clubId;
        private final String clubName;
        private final String clubType;
        private final String clubStatus;
        private final String clubProfileImageUrl;
        private final String clubThemeColor;
        private final Long userId;
        private final String displayName;
        private final String memberType;
        private final String role;
        private final String status;
        private final String joinSource;
        private final LocalDateTime approvedAt;
        private final LocalDateTime invitedAt;
        private final LocalDateTime leftAt;
        private final LocalDateTime lastViewedAt;
        private final LocalDateTime createdAt;
        private final LocalDateTime updatedAt;
    }

    // 최근 본 순(last_viewed_at 내림차순, 값이 없는 소속은 뒤). 같으면 나중에 만든 소속 먼저
    public List<Row> find(long userId, List<String> statuses, LocalDateTime utcNow, long offset, int size) {
        MapSqlParameterSource p =
                params(userId, statuses, utcNow).addValue("offset", offset).addValue("size", size);
        return jdbc.query(
                "SELECT m.id, m.club_id, c.name AS club_name, c.club_type, c.status AS club_status,"
                        + " c.profile_image_url, c.theme_color, m.user_id,"
                        + " COALESCE(m.display_name, u.name) AS display_name,"
                        + " m.member_type, m.role, m.status, m.join_source, m.approved_at, m.invited_at, m.left_at,"
                        + " m.last_viewed_at, m.created_at, m.updated_at"
                        + " FROM membership m JOIN club c ON c.id = m.club_id JOIN users u ON u.id = m.user_id"
                        + where(statuses)
                        + " ORDER BY m.last_viewed_at IS NULL, m.last_viewed_at DESC, m.id DESC"
                        + " LIMIT :size OFFSET :offset",
                p,
                (rs, i) -> Row.builder()
                        .id(rs.getLong("id"))
                        .clubId(rs.getLong("club_id"))
                        .clubName(rs.getString("club_name"))
                        .clubType(rs.getString("club_type"))
                        .clubStatus(rs.getString("club_status"))
                        .clubProfileImageUrl(rs.getString("profile_image_url"))
                        .clubThemeColor(rs.getString("theme_color"))
                        .userId(rs.getLong("user_id"))
                        .displayName(rs.getString("display_name"))
                        .memberType(rs.getString("member_type"))
                        .role(rs.getString("role"))
                        .status(rs.getString("status"))
                        .joinSource(rs.getString("join_source"))
                        .approvedAt(rs.getObject("approved_at", LocalDateTime.class))
                        .invitedAt(rs.getObject("invited_at", LocalDateTime.class))
                        .leftAt(rs.getObject("left_at", LocalDateTime.class))
                        .lastViewedAt(rs.getObject("last_viewed_at", LocalDateTime.class))
                        .createdAt(rs.getObject("created_at", LocalDateTime.class))
                        .updatedAt(rs.getObject("updated_at", LocalDateTime.class))
                        .build());
    }

    public long count(long userId, List<String> statuses, LocalDateTime utcNow) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM membership m" + where(statuses), params(userId, statuses, utcNow), Long.class);
    }

    @Getter
    @Builder
    public static class BadgeCounts {
        private final long pending;
        private final long invited;
    }

    // 필터와 무관하게 본인의 PENDING·INVITED 전체 건수(배지용). 만료된 초대는 뺀다
    public BadgeCounts badgeCounts(long userId, LocalDateTime utcNow) {
        return jdbc.queryForObject(
                "SELECT COALESCE(SUM(m.status = 'PENDING'), 0) AS pending, COALESCE(SUM(m.status = 'INVITED'), 0) AS invited"
                        + " FROM membership m" + where(List.of()),
                params(userId, List.of(), utcNow),
                (rs, i) -> BadgeCounts.builder()
                        .pending(rs.getLong("pending"))
                        .invited(rs.getLong("invited"))
                        .build());
    }

    // 보유 파트(membershipId별). 사용 중지(is_active = false)된 파트도 포함한다(C-12)
    public Map<Long, List<MyMembershipsResponse.PartInfo>> findParts(Collection<Long> membershipIds) {
        if (membershipIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<MyMembershipsResponse.PartInfo>> out = new HashMap<>();
        jdbc.query(
                "SELECT mp.membership_id, p.id, p.name, p.display_order, p.is_active FROM membership_part mp"
                        + " JOIN part p ON p.id = mp.part_id WHERE mp.membership_id IN (:ids)"
                        + " ORDER BY p.display_order, p.id",
                new MapSqlParameterSource("ids", membershipIds),
                rs -> {
                    out.computeIfAbsent(rs.getLong("membership_id"), k -> new ArrayList<>())
                            .add(MyMembershipsResponse.PartInfo.builder()
                                    .id(rs.getLong("id"))
                                    .name(rs.getString("name"))
                                    .displayOrder(rs.getInt("display_order"))
                                    .active(rs.getBoolean("is_active"))
                                    .build());
                });
        return out;
    }

    // 만료된 초대(invited_at + 1개월, KST 달력)는 배지·목록 모두에서 뺀다
    private static String where(List<String> statuses) {
        return " WHERE m.user_id = :userId AND (m.status <> 'INVITED' OR "
                + InvitationExpiry.expiresAtUtcSql("m.invited_at") + " > :now)"
                + (statuses.isEmpty() ? "" : " AND m.status IN (:statuses)");
    }

    private static MapSqlParameterSource params(long userId, List<String> statuses, LocalDateTime utcNow) {
        MapSqlParameterSource p = new MapSqlParameterSource("userId", userId).addValue("now", utcNow);
        if (!statuses.isEmpty()) {
            p.addValue("statuses", statuses);
        }
        return p;
    }

    // API 140 전용 UPDATE. Membership 엔티티를 거치면 updated_at이 바뀌므로 네이티브로 처리한다.
    // 동시 호출에서 늦게 저장되는 오래된 시각이 최근 값을 덮어쓰지 않도록 GREATEST로 저장한다.
    // 홈을 열 수 있는 상태만 기록한다(PENDING·INVITED·REJECTED·DECLINED·WITHDRAWN은 접근 불가). 갱신된 행 수를 돌려준다
    public int touchLastViewed(long membershipId, long userId, LocalDateTime utcNow) {
        return jdbc.update(
                "UPDATE membership SET last_viewed_at = GREATEST(COALESCE(last_viewed_at, :at), :at),"
                        + " updated_at = updated_at"
                        + " WHERE id = :id AND user_id = :userId AND status IN ('ACTIVE','ON_LEAVE','GRADUATED')",
                new MapSqlParameterSource("at", utcNow)
                        .addValue("id", membershipId)
                        .addValue("userId", userId));
    }

    // API 140이 방금 저장(또는 유지)한 값. 같은 트랜잭션에서 읽는다
    public LocalDateTime findLastViewedAt(long membershipId, long userId) {
        return jdbc.queryForObject(
                "SELECT last_viewed_at FROM membership WHERE id = :id AND user_id = :userId",
                new MapSqlParameterSource("id", membershipId).addValue("userId", userId),
                LocalDateTime.class);
    }
}
