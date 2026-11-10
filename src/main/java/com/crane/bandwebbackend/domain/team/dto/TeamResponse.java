package com.crane.bandwebbackend.domain.team.dto;

import com.crane.bandwebbackend.domain.team.entity.TeamSource;
import com.crane.bandwebbackend.domain.team.entity.TeamStatus;
import com.crane.bandwebbackend.global.serialization.IdString;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

// 팀 카드·상세 공통(API-061·063)
@Getter
@Builder
public class TeamResponse {

    @IdString
    private final Long id;

    @IdString
    private final Long performanceId;

    private final String name;
    private final TeamStatus status;
    private final TeamSource source;

    @Getter(onMethod_ = @JsonProperty("isDefault"))
    private final boolean isDefault;

    @IdString
    private final Long leaderMembershipId;

    private final String leaderDisplayName;
    // REJECTED 팀에서만, 팀원과 A에게만
    private final String rejectReason;
    private final int memberCount;
    private final List<PartCount> partSummary;
    // 팀원과 A에게만, 그 밖에는 NULL
    private final Integer assignedSongCount;
    private final Integer candidateSongCount;

    @Getter(onMethod_ = @JsonProperty("isMine"))
    private final boolean isMine;

    private final OffsetDateTime submittedAt;
    private final OffsetDateTime decidedAt;
    private final OffsetDateTime createdAt;

    @IdString
    private final Long version;

    // 대표 파트 기준 파트별 팀원 수. 대표 파트가 없는 팀원은 partId·partName NULL 한 항목
    @Getter
    @Builder
    public static class PartCount {

        @IdString
        private final Long partId;

        private final String partName;
        private final int count;
    }
}
