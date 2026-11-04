package com.crane.bandwebbackend.domain.performance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 공연 상태 변경 사유 요청. */
public record PerformanceReasonRequest(
		@NotBlank(message = "사유는 필수입니다.")
		@Size(max = 500, message = "사유는 500자 이하여야 합니다.") String reason
) {
}
