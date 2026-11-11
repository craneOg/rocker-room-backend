package com.crane.bandwebbackend.domain.performance.dto;

import com.crane.bandwebbackend.domain.performance.entity.VoteMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VoteMethodChangeRequest(
        @NotBlank(message = "버전은 필수입니다.") @Pattern(regexp = "[0-9]+", message = "버전은 0 이상의 정수여야 합니다.")
        String version,

        @NotNull(message = "투표 방식은 필수입니다.") VoteMethod voteMethod,
        Boolean confirmed,
        @Size(max = 500, message = "사유는 500자 이하여야 합니다.") String reason) {}
