package com.crane.bandwebbackend.domain.membership.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record JoinBulkDecisionRequest(
		@NotEmpty(message = "처리할 소속은 필수입니다.")
		@Size(max = 100, message = "한 번에 100명까지 처리할 수 있습니다.")
		List<String> membershipIds,
		@NotNull(message = "승인 또는 거절을 선택해야 합니다.") Decision decision,
		@Size(max = 500, message = "사유는 500자 이하여야 합니다.") String reason
) {
	public enum Decision {
		APPROVE, REJECT
	}
}
