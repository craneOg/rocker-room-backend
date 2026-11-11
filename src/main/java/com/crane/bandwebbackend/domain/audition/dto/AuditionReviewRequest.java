package com.crane.bandwebbackend.domain.audition.dto;

import com.crane.bandwebbackend.domain.audition.entity.AuditionResult;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 오디션 심사 결과 입력·정정 요청. */
public record AuditionReviewRequest(
        @NotNull(message = "오디션 결과는 필수입니다.") AuditionResult result,
        @Size(max = 500, message = "심사 의견은 500자 이하여야 합니다.") String comment,
        @Size(max = 500, message = "정정 사유는 500자 이하여야 합니다.") String reason,

        @NotBlank(message = "version은 필수입니다.") @Size(max = 100, message = "version은 100자 이하여야 합니다.")
        String version) {}
