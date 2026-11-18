package com.crane.bandwebbackend.domain.membership.dto;

import com.crane.bandwebbackend.domain.membership.entity.MembershipRole;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** API-031 임원 역할 부여·해제 요청. reason 길이는 서비스에서 strip 후 검증한다. */
@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode
public class MembershipRoleRequest {
    @JsonProperty("role")
    @NotNull(message = "역할은 필수입니다.")
    private MembershipRole role;

    @JsonProperty("reason")
    private String reason;
}
