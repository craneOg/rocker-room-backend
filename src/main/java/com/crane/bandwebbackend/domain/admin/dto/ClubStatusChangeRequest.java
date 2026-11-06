package com.crane.bandwebbackend.domain.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// API 120·159 요청: 사유는 필수(1~500자)이며 감사 로그에 기록한다
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ClubStatusChangeRequest {

    @NotBlank
    @Size(max = 500)
    private String reason;
}
