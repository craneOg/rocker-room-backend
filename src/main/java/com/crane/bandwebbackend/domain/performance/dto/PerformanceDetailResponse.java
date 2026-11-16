package com.crane.bandwebbackend.domain.performance.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode
public class PerformanceDetailResponse {
    @JsonProperty("performance")
    private PerformanceResponse performance;

    @JsonProperty("policy")
    private PerformancePolicyResponse policy;

    @JsonProperty("counts")
    private Counts counts;

    @JsonProperty("nextSchedule")
    private NextSchedule nextSchedule;

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @EqualsAndHashCode
    public static class Counts {
        @JsonProperty("teams")
        private long teams;

        @JsonProperty("candidateSongs")
        private long candidateSongs;

        @JsonProperty("confirmedSongs")
        private long confirmedSongs;

        @JsonProperty("participants")
        private long participants;
    }

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @EqualsAndHashCode
    public static class NextSchedule {
        @JsonProperty("id")
        private String id;

        @JsonProperty("title")
        private String title;

        @JsonProperty("startAt")
        private OffsetDateTime startAt;
    }
}
