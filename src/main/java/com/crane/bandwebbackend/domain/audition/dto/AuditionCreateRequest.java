package com.crane.bandwebbackend.domain.audition.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 오디션 등록 요청. 세션 심사에서는 지원자 목록과 파트를 함께 지정한다. */
@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode
public class AuditionCreateRequest {
    @JsonProperty("type")
    @NotBlank(message = "오디션 유형은 필수입니다.")
    private String type;

    @JsonProperty("songId")
    @NotBlank(message = "곡 ID는 필수입니다.")
    @Pattern(regexp = "[1-9][0-9]*", message = "곡 ID는 양의 정수여야 합니다.")
    private String songId;

    @JsonProperty("partId")
    @Pattern(regexp = "[1-9][0-9]*", message = "파트 ID는 양의 정수여야 합니다.")
    private String partId;

    @JsonProperty("applicantPerformanceMemberIds")
    @Size(max = 50, message = "지원자는 한 번에 50명까지 등록할 수 있습니다.")
    private List<String> applicantPerformanceMemberIds;

    @JsonProperty("linkedScheduleId")
    @Pattern(regexp = "[1-9][0-9]*", message = "연결 일정 ID는 양의 정수여야 합니다.")
    private String linkedScheduleId;

    @JsonProperty("memo")
    @Size(max = 500, message = "메모는 500자 이하여야 합니다.")
    private String memo;
}
