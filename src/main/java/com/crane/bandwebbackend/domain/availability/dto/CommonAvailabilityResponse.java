package com.crane.bandwebbackend.domain.availability.dto;

import com.crane.bandwebbackend.global.serialization.IdString;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

/**
 * API-103 히트맵. 30분 슬롯별 가능 인원 n / 대상 m(participantCount). 가능 인원이 0인 칸은 생략.
 * participants·missingAvailabilityMemberIds·slots[].availableMembers는 A에게만(AV-04), 그 밖에는 생략
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CommonAvailabilityResponse {

    private final OffsetDateTime from;
    private final OffsetDateTime to;
    private final int participantCount;
    // 대상 중 가능 시간을 한 번이라도 입력한 사람 수
    private final int respondedCount;
    private final List<Slot> slots;
    private final List<Member> participants;

    @IdString
    private final List<Long> missingAvailabilityMemberIds;

    @Getter
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Slot {

        private final OffsetDateTime startAt;
        private final int availableCount;
        private final List<Member> availableMembers;
    }

    @Getter
    @Builder
    public static class Member {

        @IdString
        private final Long performanceMemberId;

        private final String displayName;
    }
}
