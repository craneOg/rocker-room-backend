package com.crane.bandwebbackend.global.response;

import com.crane.bandwebbackend.global.serialization.IdString;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
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
 * - 문장만 있는 경고(BusinessException.confirmationRequired): message
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Warning {

    private final String type;

    @IdString
    private final Long partId;

    private final String partName;
    private final Integer requiredCount;
    private final Integer assignedCount;

    @IdString
    private final Long performanceMemberId;

    @IdString
    private final Long membershipId;

    private final String displayName;
    private final Integer songCount;

    @IdString
    private final Long songId;

    @IdString
    private final Long teamId;

    private final Integer setlistOrder;

    @IdString
    private final List<Long> scheduleIds;

    private final String message;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // 409 error.warnings(BusinessException.confirmationRequired)용. @IdString·NON_NULL이 그대로 적용됨
    public static List<Map<String, Object>> toMaps(List<Warning> warnings) {
        return MAPPER.convertValue(warnings, new TypeReference<>() {});
    }
}
