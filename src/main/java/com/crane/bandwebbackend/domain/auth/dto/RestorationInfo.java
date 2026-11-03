package com.crane.bandwebbackend.domain.auth.dto;

import java.time.OffsetDateTime;

// API 131 응답 data. 복구 선택 화면에 보여 줄 보관 정보(이름·사진·복구 기한)
public record RestorationInfo(
        String displayName, String profileImageUrl, OffsetDateTime withdrawnAt, OffsetDateTime restorableUntil) {}
