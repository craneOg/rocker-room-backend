package com.crane.bandwebbackend.domain.membership.dto;

import jakarta.validation.constraints.Size;

/** 소속 표시 이름 수정 요청. */
public record MembershipProfileRequest(
        @Size(max = 50, message = "표시 이름은 50자 이하여야 합니다.") String displayName) {}
