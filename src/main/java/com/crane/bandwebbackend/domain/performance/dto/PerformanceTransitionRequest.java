package com.crane.bandwebbackend.domain.performance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 공연 상태 전이 요청. */
public record PerformanceTransitionRequest(
		@NotBlank(message = "버전은 필수입니다.")
		@Pattern(regexp = "[0-9]+", message = "버전은 0 이상의 정수여야 합니다.") String version,
		@Size(max = 500, message = "사유는 500자 이하여야 합니다.") String reason
) {
}
