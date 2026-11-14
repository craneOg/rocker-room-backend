package com.crane.bandwebbackend.domain.participant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
public class ParticipantBulkDecisionResponse {
    @JsonProperty("succeeded")
    private List<Success> succeeded;

    @JsonProperty("failed")
    private List<Failure> failed;

    @JsonProperty("summary")
    private Summary summary;

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @EqualsAndHashCode
    public static class Success {
        @JsonProperty("performanceMemberId")
        private String performanceMemberId;
    }

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @EqualsAndHashCode
    public static class Failure {
        @JsonProperty("performanceMemberId")
        private String performanceMemberId;

        @JsonProperty("reason")
        private String reason;
    }

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @EqualsAndHashCode
    public static class Summary {
        @JsonProperty("requested")
        private int requested;

        @JsonProperty("succeeded")
        private int succeeded;

        @JsonProperty("failed")
        private int failed;
    }
}
