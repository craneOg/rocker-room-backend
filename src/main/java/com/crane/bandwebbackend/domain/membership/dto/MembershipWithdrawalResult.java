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
public class MembershipWithdrawalResult {
    @JsonProperty("resource")
    private MembershipResponse resource;

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
