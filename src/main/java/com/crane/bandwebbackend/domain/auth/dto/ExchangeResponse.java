package com.crane.bandwebbackend.domain.auth.dto;

import com.crane.bandwebbackend.domain.user.entity.User;
import com.crane.bandwebbackend.global.serialization.IdString;
import com.crane.bandwebbackend.global.time.KstTime;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

// API 3 응답 data. 토큰은 본문에 넣지 않는다(Set-Cookie). 복구 선택 대기면 user는 null
@Getter
@RequiredArgsConstructor
public class ExchangeResponse {

    private final UserSummary user;
    private final boolean onboardingRequired;
    private final LoginResult loginResult;
    private final String returnTo;

    public static ExchangeResponse of(User user, LoginResult loginResult, String returnTo) {
        // handle이 없으면 온보딩이 필요하다(API 7과 같은 기준)
        return new ExchangeResponse(UserSummary.from(user), user.getHandle() == null, loginResult, returnTo);
    }

    public static ExchangeResponse restorationRequired(String returnTo) {
        return new ExchangeResponse(null, false, LoginResult.RESTORATION_REQUIRED, returnTo);
    }

    // API 3·4 응답의 user. 명세 필드명은 isSuperAdmin이다
    @Getter
    @Builder
    public static class UserSummary {

        @IdString
        private final Long id;

        private final String name;
        private final String handle;
        private final String profileImageUrl;
        private final String status;

        // Lombok getter는 isSuperAdmin()이라 JSON 이름을 getter에 직접 지정한다
        @Getter(onMethod_ = @JsonProperty("isSuperAdmin"))
        private final boolean superAdmin;

        private final OffsetDateTime createdAt;
        private final OffsetDateTime updatedAt;

        public static UserSummary from(User user) {
            return UserSummary.builder()
                    .id(user.getId())
                    .name(user.getName())
                    .handle(user.getHandle())
                    .profileImageUrl(user.getProfileImageUrl())
                    .status(user.getStatus().name())
                    .superAdmin(user.isSuperAdmin())
                    .createdAt(KstTime.toKst(user.getCreatedAt()))
                    .updatedAt(KstTime.toKst(user.getUpdatedAt()))
                    .build();
        }
    }
}
