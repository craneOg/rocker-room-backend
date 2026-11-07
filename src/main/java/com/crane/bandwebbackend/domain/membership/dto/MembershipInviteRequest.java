package com.crane.bandwebbackend.domain.membership.dto;

import com.crane.bandwebbackend.domain.membership.entity.MemberType;
import jakarta.validation.constraints.NotBlank;

public record MembershipInviteRequest(
		@NotBlank(message = "핸들은 필수입니다.") String handle,
		MemberType memberType,
		String displayName
) {
}
