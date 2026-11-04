package com.crane.bandwebbackend.domain.auth.dto;

import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

// API 131 응답 data. 복구 선택 화면에 보여 줄 보관 정보(이름·사진·복구 기한)
@Getter
@RequiredArgsConstructor
public class RestorationInfo {

    private final String displayName;
    private final String profileImageUrl;
    private final OffsetDateTime withdrawnAt;
    private final OffsetDateTime restorableUntil;
}
