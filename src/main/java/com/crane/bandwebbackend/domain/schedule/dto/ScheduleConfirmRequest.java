package com.crane.bandwebbackend.domain.schedule.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** API-111: 경고 확인 후 같은 version으로 다시 요청한다. */
@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode
public class ScheduleConfirmRequest {
    @JsonProperty("version")
    @NotBlank(message = "버전은 필수입니다.")
    @Pattern(regexp = "[0-9]+", message = "버전은 0 이상의 정수여야 합니다.")
    private String version;

    @JsonProperty("confirmed")
    private Boolean confirmed;
}
