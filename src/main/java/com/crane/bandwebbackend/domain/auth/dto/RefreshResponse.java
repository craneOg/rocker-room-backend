package com.crane.bandwebbackend.domain.auth.dto;

import com.crane.bandwebbackend.domain.user.entity.User;

// API 4 응답 data. 토큰은 Set-Cookie로만 내려가고 loginResult는 없다
public record RefreshResponse(ExchangeResponse.UserSummary user, boolean onboardingRequired) {

    public static RefreshResponse of(User user) {
        return new RefreshResponse(ExchangeResponse.UserSummary.from(user), user.getHandle() == null);
    }
}
