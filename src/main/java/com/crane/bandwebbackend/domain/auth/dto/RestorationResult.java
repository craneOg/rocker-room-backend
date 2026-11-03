package com.crane.bandwebbackend.domain.auth.dto;

// API 132·133 응답 data. 토큰은 Set-Cookie로만 내려간다
public record RestorationResult(LoginResult loginResult) {}
