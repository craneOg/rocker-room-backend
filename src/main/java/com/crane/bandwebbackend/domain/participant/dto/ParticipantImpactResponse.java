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
public class ParticipantImpactResponse {
    @JsonProperty("teamIds")
    private List<String> teamIds;

    @JsonProperty("songIds")
    private List<String> songIds;

    @JsonProperty("scheduleIds")
    private List<String> scheduleIds;

    @JsonProperty("affectedUserIds")
    private List<String> affectedUserIds;

    @JsonProperty("warnings")
    private List<Warning> warnings;

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @EqualsAndHashCode
    public static class Warning {
        @JsonProperty("type")
        private String type;

        @JsonProperty("targetId")
        private String targetId;

        @JsonProperty("message")
        private String message;
    }
}
