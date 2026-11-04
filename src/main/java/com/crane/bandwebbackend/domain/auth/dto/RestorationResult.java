package com.crane.bandwebbackend.domain.auth.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// API 132·133 응답 data. 토큰은 Set-Cookie로만 내려간다
@Getter
@RequiredArgsConstructor
public class RestorationResult {

    private final LoginResult loginResult;
}
