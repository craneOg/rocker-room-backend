package com.crane.bandwebbackend.domain.participant.dto;

import com.crane.bandwebbackend.domain.participant.entity.PerformanceMemberSource;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** 공연 참여 등록 요청. */
public record ParticipantRegisterRequest(
        @NotNull(message = "참여 등록 경로는 필수입니다.") PerformanceMemberSource source,

        @Pattern(regexp = "[1-9][0-9]{0,18}", message = "소속 ID는 양의 정수여야 합니다.")
        String membershipId) {}
