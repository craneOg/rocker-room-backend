package com.crane.bandwebbackend.domain.membership.dto;

import com.crane.bandwebbackend.domain.membership.entity.MemberType;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 소속 부원 유형 변경 요청. */
@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode
public class MembershipTypeRequest {
    @JsonProperty("memberType")
    @NotNull(message = "부원 유형은 필수입니다.")
    private MemberType memberType;

    @JsonProperty("reason")
    @NotBlank(message = "변경 사유는 필수입니다.")
    @Size(max = 500, message = "사유는 500자 이하여야 합니다.")
    private String reason;
}
