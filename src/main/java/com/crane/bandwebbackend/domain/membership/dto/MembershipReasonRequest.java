package com.crane.bandwebbackend.domain.membership.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 소속 상태 변경 사유 요청. */
@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode
public class MembershipReasonRequest {
    @JsonProperty("reason")
    @Size(max = 500, message = "사유는 500자 이하여야 합니다.")
    private String reason;
}
