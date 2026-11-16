package com.crane.bandwebbackend.domain.membership.dto;

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
public class JoinBulkDecisionResponse {
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
        @JsonProperty("membershipId")
        private String membershipId;
    }

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @EqualsAndHashCode
    public static class Failure {
        @JsonProperty("membershipId")
        private String membershipId;

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
