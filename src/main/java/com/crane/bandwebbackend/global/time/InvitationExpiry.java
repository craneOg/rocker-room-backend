package com.crane.bandwebbackend.global.time;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

// 초대 유효기간 = invited_at + 1개월을 KST 달력으로 센다(DB는 UTC 저장). API·운영 집계가 같은 기준을 쓰도록 한 곳에 둔다
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class InvitationExpiry {

    // SQL 식: KST로 옮겨 1개월을 더한 뒤 다시 UTC로 돌린 만료 시각. UTC 시각과 바로 비교한다
    public static String expiresAtUtcSql(String invitedAtColumn) {
        return "DATE_SUB(DATE_ADD(DATE_ADD(" + invitedAtColumn + ", INTERVAL 9 HOUR), INTERVAL 1 MONTH),"
                + " INTERVAL 9 HOUR)";
    }

    // API 응답용 만료 시각(+09:00). expiresAtUtcSql과 같은 값이다
    public static OffsetDateTime expiresAt(LocalDateTime invitedAtUtc) {
        return invitedAtUtc == null ? null : KstTime.toKst(invitedAtUtc).plusMonths(1);
    }
}
