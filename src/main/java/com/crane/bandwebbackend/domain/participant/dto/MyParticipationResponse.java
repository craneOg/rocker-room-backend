package com.crane.bandwebbackend.domain.participant.dto;

import com.crane.bandwebbackend.domain.participant.entity.PerformanceMemberSource;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMemberStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode
public class MyParticipationResponse {
    @JsonProperty("id")
    private String id;

    @JsonProperty("performanceId")
    private String performanceId;

    @JsonProperty("membershipId")
    private String membershipId;

    @JsonProperty("status")
    private PerformanceMemberStatus status;

    @JsonProperty("source")
    private PerformanceMemberSource source;

    @JsonProperty("createdAt")
    private OffsetDateTime createdAt;

    @JsonProperty("updatedAt")
    private OffsetDateTime updatedAt;

    @JsonProperty("myTeams")
    private List<MyTeam> myTeams;

    @JsonProperty("myPlayingSongs")
    private List<MySong> myPlayingSongs;

    @JsonProperty("progress")
    private Progress progress;

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @EqualsAndHashCode
    public static class MyTeam {
        @JsonProperty("teamId")
        private String teamId;

        @JsonProperty("name")
        private String name;

        @JsonProperty("isLeader")
        @Getter(onMethod_ = @JsonProperty("isLeader"))
        private boolean isLeader;
    }

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @EqualsAndHashCode
    public static class MySong {
        @JsonProperty("songId")
        private String songId;

        @JsonProperty("title")
        private String title;

        @JsonProperty("partId")
        private String partId;

        @JsonProperty("partName")
        private String partName;

        @JsonProperty("setlistOrder")
        private Integer setlistOrder;
    }

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @EqualsAndHashCode
    public static class Usage {
        @JsonProperty("used")
        private long used;

        @JsonProperty("limit")
        private Integer limit;
    }

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @EqualsAndHashCode
    public static class Progress {
        @JsonProperty("candidateSongs")
        private Usage candidateSongs;

        @JsonProperty("unansweredPlayabilityCount")
        private Long unansweredPlayabilityCount;

        @JsonProperty("votes")
        private Usage votes;

        @JsonProperty("availabilitySlotCount")
        private Long availabilitySlotCount;
    }
}
