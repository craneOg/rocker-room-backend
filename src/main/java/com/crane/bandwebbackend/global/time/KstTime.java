package com.crane.bandwebbackend.global.time;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

// 저장은 UTC LocalDateTime, API는 +09:00 OffsetDateTime(U3). KST는 서머타임이 없어 고정 오프셋을 쓴다
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class KstTime {

    public static final ZoneOffset KST = ZoneOffset.ofHours(9);

    public static OffsetDateTime toKst(LocalDateTime utc) {
        return utc == null ? null : utc.atOffset(ZoneOffset.UTC).withOffsetSameInstant(KST);
    }

    public static LocalDateTime toUtc(OffsetDateTime offsetDateTime) {
        return offsetDateTime == null
                ? null
                : offsetDateTime.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    // UTC로 저장된 시각의 KST 달력 날짜
    public static LocalDate toKstDate(LocalDateTime utc) {
        return utc == null ? null : toKst(utc).toLocalDate();
    }
}
