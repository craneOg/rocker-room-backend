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
public class LockMutationResponse {
    @JsonProperty("resource")
    private PerformanceResponse resource;

    @JsonProperty("warnings")
    private List<LockChecklistItem> warnings;

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

        public static Effects none() {
            return new Effects(List.of(), List.of(), List.of());
        }
    }
}
