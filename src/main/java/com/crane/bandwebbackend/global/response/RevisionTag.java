package com.crane.bandwebbackend.global.response;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * membership/participant/audition에는 DB version 컬럼이 없다. v2 명세의 revision은 이 두 값으로
 * 만든 불투명 표현이다. 향후 전이 API가 이 태그로 충돌을 검사할 때는 같은 행을 잠근 트랜잭션에서
 * 다시 계산해야 한다. 이 문자열을 클라이언트가 해석하거나 순서 비교하는 계약은 없다.
 */
public final class RevisionTag {
    private RevisionTag() {}

    public static String from(String prefix, Long id, LocalDateTime updatedAt) {
        if (id == null || updatedAt == null) {
            throw new IllegalStateException("저장되지 않은 행은 revision을 만들 수 없습니다.");
        }
        long seconds = updatedAt.toEpochSecond(ZoneOffset.UTC);
        return prefix + "-" + id + "-" + seconds + "-" + updatedAt.getNano();
    }
}
