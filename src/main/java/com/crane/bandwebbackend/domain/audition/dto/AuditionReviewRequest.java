package com.crane.bandwebbackend.domain.audition.dto;

import com.crane.bandwebbackend.domain.audition.entity.AuditionResult;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 오디션 심사 결과 입력·정정 요청. */
public record AuditionReviewRequest(
		@NotNull(message = "오디션 결과는 필수입니다.") AuditionResult result,
		@Size(max = 500, message = "심사 의견은 500자 이하여야 합니다.") String comment,
		@Size(max = 500, message = "정정 사유는 500자 이하여야 합니다.") String reason,
		@NotBlank(message = "버전은 필수입니다.")
		@Pattern(regexp = "[0-9]+", message = "버전은 0 이상의 정수여야 합니다.") String version
) {
}
