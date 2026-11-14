package com.crane.bandwebbackend.domain.participant.dto;

import com.crane.bandwebbackend.domain.participant.entity.PerformanceMemberSource;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 공연 참여 등록 요청. */
@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode
public class ParticipantRegisterRequest {
    @JsonProperty("source")
    @NotNull(message = "참여 등록 경로는 필수입니다.")
    private PerformanceMemberSource source;

    @JsonProperty("membershipId")
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "소속 ID는 양의 정수여야 합니다.")
    private String membershipId;
}
