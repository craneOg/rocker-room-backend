package com.crane.bandwebbackend.domain.team.dto;

import com.crane.bandwebbackend.domain.participant.entity.PerformanceMemberStatus;
import com.crane.bandwebbackend.global.serialization.IdString;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

// API-063 members. id = teamMemberId
@Getter
@Builder
public class TeamMemberResponse {

    @IdString
    private final Long id;

    @IdString
    private final Long teamId;

    @IdString
    private final Long membershipId;

    @IdString
    private final Long performanceMemberId;

    private final String displayName;
    // U-13: 본인이 비공개로 정했으면 본인 외에는 NULL
    private final String profileImageUrl;

    @Getter(onMethod_ = @JsonProperty("isLeader"))
    private final boolean isLeader;

    @IdString
    private final Long partId;

    private final String partName;
    // APPLIED면 참여 희망 배지
    private final PerformanceMemberStatus participationStatus;

    // false = 대표 파트가 본인 등록 파트에 없음(T-11 경고 배지). 대표 파트가 없으면 true
    @Getter(onMethod_ = @JsonProperty("isRegisteredPart"))
    private final boolean isRegisteredPart;
}
