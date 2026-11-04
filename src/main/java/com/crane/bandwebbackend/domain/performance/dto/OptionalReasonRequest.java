package com.crane.bandwebbackend.domain.performance.dto;

import jakarta.validation.constraints.Size;

/** API-151에서는 사유 입력이 선택이다. */
public record OptionalReasonRequest(@Size(max = 500, message = "사유는 500자 이하여야 합니다.") String reason) {
}
