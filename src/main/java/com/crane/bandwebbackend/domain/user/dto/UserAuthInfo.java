package com.crane.bandwebbackend.domain.user.dto;

import com.crane.bandwebbackend.domain.user.entity.UserStatus;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 요청마다 확인하는 최소 정보(G-02 token_version, G-03 status)
@Getter
@RequiredArgsConstructor
public class UserAuthInfo {

    private final int tokenVersion;
    private final UserStatus status;
    private final boolean superAdmin;
}
