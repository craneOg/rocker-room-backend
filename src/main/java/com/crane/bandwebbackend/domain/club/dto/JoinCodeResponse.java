package com.crane.bandwebbackend.domain.club.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

// API 17·18 응답 data. 발급 전이면 joinCode는 null이다(키는 그대로 내려간다)
@Getter
@AllArgsConstructor
public class JoinCodeResponse {

    private final String joinCode;
}
