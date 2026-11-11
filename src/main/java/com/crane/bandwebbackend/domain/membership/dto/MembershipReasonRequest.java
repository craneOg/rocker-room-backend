package com.crane.bandwebbackend.domain.membership.dto;

import jakarta.validation.constraints.Size;

/** 소속 상태 변경 사유 요청. */
public record MembershipReasonRequest(
        @Size(max = 500, message = "사유는 500자 이하여야 합니다.") String reason) {}
