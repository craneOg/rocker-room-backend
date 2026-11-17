package com.crane.bandwebbackend.domain.membership.dto;

import com.crane.bandwebbackend.domain.membership.entity.JoinSource;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** API-014 가입 신청 요청. joinCode 형식 오류는 존재 비노출을 위해 서비스에서 404로 처리한다. */
@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode
public class JoinRequestCreateRequest {
    @JsonProperty("source")
    @NotNull(message = "가입 경로는 필수입니다.")
    private JoinSource source;

    @JsonProperty("joinCode")
    private String joinCode;

    @JsonProperty("displayName")
    private String displayName;
}
