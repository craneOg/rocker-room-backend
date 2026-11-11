package com.crane.bandwebbackend.domain.membership.dto;

import com.crane.bandwebbackend.domain.membership.entity.MemberType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 소속 부원 유형 변경 요청. */
public record MembershipTypeRequest(
        @NotNull(message = "부원 유형은 필수입니다.") MemberType memberType,

        @NotBlank(message = "변경 사유는 필수입니다.") @Size(max = 500, message = "사유는 500자 이하여야 합니다.")
        String reason) {}
