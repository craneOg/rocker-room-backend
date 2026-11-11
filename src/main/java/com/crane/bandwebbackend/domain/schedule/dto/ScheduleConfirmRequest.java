package com.crane.bandwebbackend.domain.schedule.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** API-111: 경고 확인 후 같은 version으로 다시 요청한다. */
public record ScheduleConfirmRequest(
        @NotBlank(message = "버전은 필수입니다.") @Pattern(regexp = "[0-9]+", message = "버전은 0 이상의 정수여야 합니다.")
        String version,

        Boolean confirmed) {}
