package com.crane.bandwebbackend.domain.schedule.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** API-110~112 공통 변경 결과. */
@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode
public class ScheduleMutationResponse {
    @JsonProperty("resource")
    private ScheduleResponse resource;

    @JsonProperty("warnings")
    private List<Map<String, Object>> warnings;

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

    /** 일정 응답 데이터를 생성한다 */
    public static ScheduleMutationResponse of(ScheduleResponse resource) {
        return new ScheduleMutationResponse(resource, List.of(), Effects.none());
    }
}
