package com.crane.bandwebbackend.domain.performance.dto;

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
public class PolicyUpdateResponse {
    @JsonProperty("policy")
    private PerformancePolicyResponse policy;

    @JsonProperty("warnings")
    private List<Warning> warnings;

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @EqualsAndHashCode
    public static class Warning {
        @JsonProperty("type")
        private String type;

        @JsonProperty("message")
        private String message;
    }
}
