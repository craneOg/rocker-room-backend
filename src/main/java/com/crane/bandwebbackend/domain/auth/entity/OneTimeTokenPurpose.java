package com.crane.bandwebbackend.domain.auth.entity;

public enum OneTimeTokenPurpose {
    // 카카오 로그인 시작 → 콜백(10분)
    OAUTH_STATE,
    // 카카오 콜백 → 로그인 결과 교환(60초)
    LOGIN_EXCHANGE,
    // 로그인 결과 교환 → 탈퇴 계정 복구 선택(10분)
    ACCOUNT_RESTORATION
}
