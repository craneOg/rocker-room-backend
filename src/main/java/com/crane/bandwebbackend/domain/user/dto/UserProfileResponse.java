package com.crane.bandwebbackend.domain.user.dto;

import com.crane.bandwebbackend.domain.user.entity.User;
import com.crane.bandwebbackend.global.serialization.IdString;
import com.crane.bandwebbackend.global.time.KstTime;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.Getter;

// API 7·8 응답 data: 본인 프로필. 본인에게는 비공개 사진도 보인다. onboardingRequired는 조회(7)에만 있다
@Getter
@Builder
public class UserProfileResponse {

    @IdString
    private final Long id;

    private final String name;
    private final String handle;
    private final String profileImageUrl;
    private final boolean profileImagePublic;
    private final String status;

    // Lombok getter는 isSuperAdmin()이라 JSON 이름을 getter에 직접 지정한다
    @Getter(onMethod_ = @JsonProperty("isSuperAdmin"))
    private final boolean superAdmin;

    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final Boolean onboardingRequired;

    public static UserProfileResponse withOnboarding(User user) {
        return baseBuilder(user).onboardingRequired(user.getHandle() == null).build();
    }

    public static UserProfileResponse of(User user) {
        return baseBuilder(user).build();
    }

    private static UserProfileResponseBuilder baseBuilder(User user) {
        return UserProfileResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .handle(user.getHandle())
                .profileImageUrl(user.getProfileImageUrl())
                .profileImagePublic(user.isProfileImagePublic())
                .status(user.getStatus().name())
                .superAdmin(user.isSuperAdmin())
                .createdAt(KstTime.toKst(user.getCreatedAt()))
                .updatedAt(KstTime.toKst(user.getUpdatedAt()));
    }
}
