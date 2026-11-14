package com.crane.bandwebbackend.domain.schedule.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 일정 상태 전이는 현재 버전을 확인한다. 취소 사유만 선택 사항이다. */
@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode
public class ScheduleVersionRequest {
    @JsonProperty("version")
    @NotBlank(message = "버전은 필수입니다.")
    @Pattern(regexp = "[0-9]+", message = "버전은 0 이상의 정수여야 합니다.")
    private String version;

    @JsonProperty("reason")
    @Size(max = 500, message = "사유는 500자 이하여야 합니다.")
    private String reason;
}
