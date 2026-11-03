package com.crane.bandwebbackend.domain.auth.dto;

// API 3 loginResult. RESTORATION_REQUIRED는 탈퇴 후 90일 이내 계정(복구 선택 대기)
public enum LoginResult {
    NEW_USER,
    EXISTING_USER,
    RESTORATION_REQUIRED
}
