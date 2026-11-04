package com.crane.bandwebbackend.domain.performance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record LockConfirmRequest(@NotBlank(message = "버전은 필수입니다.")
								 @Pattern(regexp = "[0-9]+", message = "버전은 0 이상의 정수여야 합니다.") String version,
								 Boolean confirmed) { }
