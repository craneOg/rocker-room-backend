package com.crane.bandwebbackend.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

/**
 * "경고" 강도 규칙 결과(성공 응답 warnings, 409 CONFIRMATION_REQUIRED의 error.warnings).
 * type별로 쓰는 필드만 채움.
 * - PART_SHORTAGE·PART_EXCESS: partId, partName, requiredCount, assignedCount (M-04)
 * - SONG_COUNT_EXCEEDED: performanceMemberId, displayName, songCount (M-05)
 * - DUPLICATE_SONG: songId (같은 제목·아티스트)
 * - PART_NOT_REGISTERED: membershipId, partId (T-11)
 * - ASSIGNMENT_RELEASED: teamId, setlistOrder / SCHEDULE_LINK_RELEASED: scheduleIds
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Warning {

    private final String type;
    private final Long partId;
    private final String partName;
    private final Integer requiredCount;
    private final Integer assignedCount;
    private final Long performanceMemberId;
    private final Long membershipId;
    private final String displayName;
    private final Integer songCount;
    private final Long songId;
    private final Long teamId;
    private final Integer setlistOrder;
    private final List<Long> scheduleIds;
}
