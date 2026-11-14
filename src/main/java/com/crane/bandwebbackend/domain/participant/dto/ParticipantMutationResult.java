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
public class ParticipantMutationResult {
    @JsonProperty("resource")
    private ParticipantResponse resource;

    @JsonProperty("warnings")
    private List<Object> warnings;

    @JsonProperty("effects")
    private Effects effects;

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @EqualsAndHashCode
    public static class Effects {
        @JsonProperty("approvedPerformanceMemberIds")
        private List<String> approvedPerformanceMemberIds;

        @JsonProperty("affectedScheduleIds")
        private List<String> affectedScheduleIds;

        @JsonProperty("affectedUserIds")
        private List<String> affectedUserIds;
    }
}
