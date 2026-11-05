package com.crane.bandwebbackend.domain.admin.dto;

import com.crane.bandwebbackend.global.serialization.IdString;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

// API 123 응답 data
@Getter
@RequiredArgsConstructor
public class ForceLogoutResponse {

    @IdString
    private final Long userId;

    private final boolean revoked;
}
