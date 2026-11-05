package com.crane.bandwebbackend.domain.admin.dto;

import com.crane.bandwebbackend.global.serialization.IdString;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

// API 122 항목. providerUid는 내려주지 않는다. 사진을 비공개로 정한 사용자의 profileImageUrl은 Super Admin에게도 null이다(U-13)
@Getter
@Builder
public class AdminUserResponse {

    @IdString
    private final Long id;

    private final String name;
    private final String handle;
    private final String profileImageUrl;
    private final String status;

    @Getter(onMethod_ = @JsonProperty("isSuperAdmin"))
    private final boolean superAdmin;

    private final OffsetDateTime lastActiveAt;
    private final boolean kakaoLinked;
    private final List<ClubEntry> clubs;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;

    @Getter
    @Builder
    public static class ClubEntry {

        @IdString
        private final Long clubId;

        private final String name;
        private final String role;
        private final String membershipStatus;
        private final String joinSource;
    }
}
