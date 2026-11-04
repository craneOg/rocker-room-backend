package com.crane.bandwebbackend.domain.membership.dto;

import com.crane.bandwebbackend.domain.membership.entity.JoinSource;
import com.crane.bandwebbackend.domain.membership.entity.MemberType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 가입 경로별 소속 생성 요청. */
public record MembershipCreateRequest(
		@NotNull(message = "가입 경로는 필수입니다.") JoinSource joinSource,
		@Size(min = 10, max = 10, message = "가입 코드는 10자여야 합니다.") String joinCode,
		@Pattern(regexp = "[a-z0-9_]{5,30}", message = "핸들은 소문자·숫자·밑줄 5~30자여야 합니다.") String handle,
		MemberType memberType,
		@Size(min = 1, max = 50, message = "표시 이름은 1~50자여야 합니다.") String displayName
) {
}
