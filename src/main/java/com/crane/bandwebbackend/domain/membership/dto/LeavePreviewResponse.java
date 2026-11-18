package com.crane.bandwebbackend.domain.membership.dto;

import com.crane.bandwebbackend.domain.membership.entity.MembershipRole;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** API-145 본인 나가기 전 영향 미리보기. 중첩 타입은 API-146과 같다. */
@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode
public class LeavePreviewResponse {
    @JsonProperty("role")
    private MembershipRole role;

    @JsonProperty("succession")
    private WithdrawalPreviewResponse.Succession succession;

    @JsonProperty("performanceImpacts")
    private List<WithdrawalPreviewResponse.PerformanceImpact> performanceImpacts;

    @JsonProperty("teamLeaderCandidates")
    private List<WithdrawalPreviewResponse.TeamLeaderCandidates> teamLeaderCandidates;

    @JsonProperty("warnings")
    private List<String> warnings;
}
