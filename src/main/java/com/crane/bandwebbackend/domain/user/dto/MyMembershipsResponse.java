package com.crane.bandwebbackend.domain.user.dto;

import com.crane.bandwebbackend.global.response.PageResponse;
import com.crane.bandwebbackend.global.serialization.IdString;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

// API 10 응답 data. 공통 페이지 응답에 PENDING·INVITED 건수를 더한다
@Getter
@Builder
public class MyMembershipsResponse {

    @JsonUnwrapped
    private final PageResponse<Item> page;

    private final long pendingCount;
    private final long invitedCount;

    @Getter
    @Builder
    public static class Item {
        @IdString
        private final Long id;

        @IdString
        private final Long clubId;

        private final ClubInfo club;

        @IdString
        private final Long userId;

        private final String displayName;
        private final String memberType;
        private final String role;
        private final String status;
        private final String joinSource;
        private final OffsetDateTime approvedAt;
        private final OffsetDateTime leftAt;
        private final OffsetDateTime lastViewedAt;
        private final OffsetDateTime invitationExpiresAt;
        private final OffsetDateTime requestedAt;
        private final List<PartInfo> parts;
        private final OffsetDateTime createdAt;
        private final OffsetDateTime updatedAt;
    }

    @Getter
    @Builder
    public static class ClubInfo {
        @IdString
        private final Long id;

        private final String name;
        private final String clubType;
        private final String status;
        private final String profileImageUrl;
        private final String themeColor;
    }

    @Getter
    @Builder
    public static class PartInfo {
        @IdString
        private final Long id;

        private final String name;
        private final int displayOrder;

        @Getter(onMethod_ = @JsonProperty("isActive"))
        private final boolean active;
    }
}
