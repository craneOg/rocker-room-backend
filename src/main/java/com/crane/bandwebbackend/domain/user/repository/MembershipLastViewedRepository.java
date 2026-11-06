package com.crane.bandwebbackend.domain.user.repository;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

// API 140 전용 UPDATE. Membership 엔티티를 거치면 updated_at이 바뀌므로 네이티브로 처리한다
@Repository
@RequiredArgsConstructor
public class MembershipLastViewedRepository {

    // 홈을 열 수 있는 상태만 기록한다(PENDING·INVITED·REJECTED·DECLINED·WITHDRAWN은 접근 불가)
    private static final String UPDATE = "UPDATE membership SET last_viewed_at = :at, updated_at = updated_at"
            + " WHERE id = :id AND user_id = :userId AND status IN ('ACTIVE','ON_LEAVE','GRADUATED')";

    private final NamedParameterJdbcTemplate jdbc;

    // 갱신된 행 수. 본인 소속이 아니거나 없거나 허용 상태가 아니면 0
    public int touch(long membershipId, long userId, LocalDateTime utcNow) {
        return jdbc.update(
                UPDATE,
                new MapSqlParameterSource("at", utcNow)
                        .addValue("id", membershipId)
                        .addValue("userId", userId));
    }
}
