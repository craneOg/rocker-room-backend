package com.crane.bandwebbackend.domain.membership.dto;

import com.crane.bandwebbackend.domain.membership.entity.MemberType;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode
public class MembershipInviteRequest {
    @JsonProperty("handle")
    @NotBlank(message = "핸들은 필수입니다.")
    private String handle;

    @JsonProperty("memberType")
    private MemberType memberType;

    @JsonProperty("displayName")
    private String displayName;
}
