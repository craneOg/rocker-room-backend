package com.crane.bandwebbackend.domain.audition.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 오디션 등록 요청. 세션 심사에서는 지원자 목록과 파트를 함께 지정한다. */
public record AuditionCreateRequest(
		@NotBlank(message = "오디션 유형은 필수입니다.") String type,
		@NotBlank(message = "곡 ID는 필수입니다.")
		@Pattern(regexp = "[1-9][0-9]*", message = "곡 ID는 양의 정수여야 합니다.") String songId,
		@Pattern(regexp = "[1-9][0-9]*", message = "파트 ID는 양의 정수여야 합니다.") String partId,
		@Size(max = 50, message = "지원자는 한 번에 50명까지 등록할 수 있습니다.")
		List<String> applicantPerformanceMemberIds,
		@Pattern(regexp = "[1-9][0-9]*", message = "연결 일정 ID는 양의 정수여야 합니다.") String linkedScheduleId,
		@Size(max = 500, message = "메모는 500자 이하여야 합니다.") String memo
) {
}
