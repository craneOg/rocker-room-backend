package com.crane.bandwebbackend.domain.membership.dto;

import jakarta.validation.constraints.Size;
import java.util.List;

public record MembershipWithdrawalRequest(
		@Size(max = 500, message = "사유는 500자 이하여야 합니다.") String reason,
		String successorMembershipId,
		List<TeamSuccessor> teamLeaderSuccessors
) {
	public record TeamSuccessor(String teamId, String membershipId) {
	}
}
