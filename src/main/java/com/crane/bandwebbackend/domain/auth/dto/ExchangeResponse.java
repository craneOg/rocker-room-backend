package com.crane.bandwebbackend.domain.auth.dto;

import com.crane.bandwebbackend.domain.user.entity.User;
import com.crane.bandwebbackend.global.serialization.IdString;
import com.crane.bandwebbackend.global.time.KstTime;
import java.time.OffsetDateTime;

// API 3 응답 data. 토큰은 본문에 넣지 않는다(Set-Cookie). 복구 선택 대기면 user는 null
public record ExchangeResponse(UserSummary user, boolean onboardingRequired, LoginResult loginResult, String returnTo) {

    public static ExchangeResponse of(User user, LoginResult loginResult, String returnTo) {
        // handle이 없으면 온보딩이 필요하다(API 7과 같은 기준)
        return new ExchangeResponse(UserSummary.from(user), user.getHandle() == null, loginResult, returnTo);
    }

    public static ExchangeResponse restorationRequired(String returnTo) {
        return new ExchangeResponse(null, false, LoginResult.RESTORATION_REQUIRED, returnTo);
    }

    public record UserSummary(
            @IdString Long id,
            String name,
            String handle,
            String profileImageUrl,
            String status,
            boolean isSuperAdmin,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {

        public static UserSummary from(User user) {
            return new UserSummary(
                    user.getId(),
                    user.getName(),
                    user.getHandle(),
                    user.getProfileImageUrl(),
                    user.getStatus().name(),
                    user.isSuperAdmin(),
                    KstTime.toKst(user.getCreatedAt()),
                    KstTime.toKst(user.getUpdatedAt()));
        }
    }
}
