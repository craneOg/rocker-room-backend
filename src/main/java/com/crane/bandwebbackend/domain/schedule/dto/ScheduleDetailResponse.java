package com.crane.bandwebbackend.domain.schedule.dto;

import com.crane.bandwebbackend.domain.membership.entity.MemberType;
import java.util.List;

/** API-108 상세: 참석 대상은 현재 팀원·연주자 연결에서 계산한다. */
public record ScheduleDetailResponse(ScheduleResponse schedule, List<Participant> participants) {
    public record Participant(
            String userId,
            String performanceMemberId,
            String membershipId,
            String displayName,
            List<Part> parts,
            MemberType memberType,
            boolean isSupportPlayer) {}

    public record Part(String id, String name) {}
}
