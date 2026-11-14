package com.crane.bandwebbackend.domain.membership.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;
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
public class MembershipWithdrawalRequest {
    @JsonProperty("reason")
    @Size(max = 500, message = "사유는 500자 이하여야 합니다.")
    private String reason;

    @JsonProperty("successorMembershipId")
    private String successorMembershipId;

    @JsonProperty("teamLeaderSuccessors")
    private List<TeamSuccessor> teamLeaderSuccessors;

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @EqualsAndHashCode
    public static class TeamSuccessor {
        @JsonProperty("teamId")
        private String teamId;

        @JsonProperty("membershipId")
        private String membershipId;
    }
}
