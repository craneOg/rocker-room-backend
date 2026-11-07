package com.crane.bandwebbackend.domain.schedule.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 일정 상태 전이는 현재 버전을 확인한다. 취소 사유만 선택 사항이다. */
public record ScheduleVersionRequest(
		@NotBlank(message = "버전은 필수입니다.")
		@Pattern(regexp = "[0-9]+", message = "버전은 0 이상의 정수여야 합니다.") String version,
		@Size(max = 500, message = "사유는 500자 이하여야 합니다.") String reason
) {
}
