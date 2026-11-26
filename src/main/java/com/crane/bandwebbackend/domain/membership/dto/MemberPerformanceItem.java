package com.crane.bandwebbackend.domain.membership.dto;

import com.crane.bandwebbackend.domain.participant.entity.PerformanceMember;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMemberStatus;
import com.crane.bandwebbackend.domain.performance.entity.PerformanceStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** API-021 부원 상세의 참여 공연 항목 */
@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode
public class MemberPerformanceItem {
    @JsonProperty("id")
    private String id;

    @JsonProperty("title")
    private String title;

    @JsonProperty("status")
    private PerformanceStatus status;

    @JsonProperty("participationStatus")
    private PerformanceMemberStatus participationStatus;

    public static MemberPerformanceItem from(PerformanceMember row) {
        return new MemberPerformanceItem(
                row.getPerformance().getId().toString(),
                row.getPerformance().getTitle(),
                row.getPerformance().getStatus(),
                row.getStatus());
    }
}
