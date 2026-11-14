package com.crane.bandwebbackend.domain.schedule.dto;

import com.crane.bandwebbackend.domain.membership.entity.MemberType;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** API-108 상세: 참석 대상은 현재 팀원·연주자 연결에서 계산한다. */
@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode
public class ScheduleDetailResponse {
    @JsonProperty("schedule")
    private ScheduleResponse schedule;

    @JsonProperty("participants")
    private List<Participant> participants;

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @EqualsAndHashCode
    public static class Participant {
        @JsonProperty("userId")
        private String userId;

        @JsonProperty("performanceMemberId")
        private String performanceMemberId;

        @JsonProperty("membershipId")
        private String membershipId;

        @JsonProperty("displayName")
        private String displayName;

        @JsonProperty("parts")
        private List<Part> parts;

        @JsonProperty("memberType")
        private MemberType memberType;

        @JsonProperty("isSupportPlayer")
        @Getter(onMethod_ = @JsonProperty("isSupportPlayer"))
        private boolean isSupportPlayer;
    }

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @EqualsAndHashCode
    public static class Part {
        @JsonProperty("id")
        private String id;

        @JsonProperty("name")
        private String name;
    }
}
