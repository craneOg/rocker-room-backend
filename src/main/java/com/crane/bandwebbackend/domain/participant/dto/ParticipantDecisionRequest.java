package com.crane.bandwebbackend.domain.participant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 공연 참여 승인·거절 요청. */
public record ParticipantDecisionRequest(
		@NotBlank(message = "공연 버전은 필수입니다.")
		@Pattern(regexp = "[0-9]+", message = "공연 버전은 0 이상의 정수여야 합니다.") String performanceVersion,
		@Size(max = 500, message = "사유는 500자 이하여야 합니다.") String reason
) {
}
