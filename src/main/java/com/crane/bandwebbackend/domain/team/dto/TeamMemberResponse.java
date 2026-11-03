package com.crane.bandwebbackend.domain.team.dto;

import com.crane.bandwebbackend.domain.participant.entity.PerformanceMemberStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

// API-063 members. id = teamMemberId
@Getter
@Builder
public class TeamMemberResponse {

    private final Long id;
    private final Long membershipId;
    private final Long performanceMemberId;
    private final String displayName;
    // 본인이 비공개로 정했으면 NULL(본인·Super Admin 제외)
    private final String profileImageUrl;

    @Getter(onMethod_ = @JsonProperty("isLeader"))
    private final boolean isLeader;

    private final Long partId;
    private final String partName;
    // APPLIED면 참여 희망 배지
    private final PerformanceMemberStatus participationStatus;

    // false = 대표 파트가 본인 등록 파트에 없음(T-11 경고 배지). 대표 파트가 없으면 true
    @Getter(onMethod_ = @JsonProperty("isRegisteredPart"))
    private final boolean isRegisteredPart;
}
