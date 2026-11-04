package com.crane.bandwebbackend.domain.auth.dto;

import com.crane.bandwebbackend.domain.user.entity.User;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

// API 4 응답 data. 토큰은 Set-Cookie로만 내려가고 loginResult는 없다
@Getter
@RequiredArgsConstructor
public class RefreshResponse {

    private final ExchangeResponse.UserSummary user;
    private final boolean onboardingRequired;

    public static RefreshResponse of(User user) {
        return new RefreshResponse(ExchangeResponse.UserSummary.from(user), user.getHandle() == null);
    }
}
