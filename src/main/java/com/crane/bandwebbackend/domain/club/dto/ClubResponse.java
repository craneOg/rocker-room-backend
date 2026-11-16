package com.crane.bandwebbackend.domain.club.dto;

import com.crane.bandwebbackend.domain.club.entity.Club;
import com.crane.bandwebbackend.global.serialization.IdString;
import com.crane.bandwebbackend.global.time.KstTime;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.Getter;

// API 11 응답 data: Club + joinCode
@Getter
@Builder
public class ClubResponse {

    @IdString
    private final Long id;

    private final String name;
    private final String description;
    private final String clubType;
    private final String status;

    @Getter(onMethod_ = @JsonProperty("isSearchable"))
    private final boolean searchable;

    private final String profileImageUrl;
    private final String themeColor;
    private final String joinCode;

    @IdString
    private final Long createdByUserId;

    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;

    public static ClubResponse from(Club club) {
        return ClubResponse.builder()
                .id(club.getId())
                .name(club.getName())
                .description(club.getDescription())
                .clubType(club.getClubType().name())
                .status(club.getStatus().name())
                .searchable(club.isSearchable())
                .profileImageUrl(club.getProfileImageUrl())
                .themeColor(club.getThemeColor())
                .joinCode(club.getJoinCode())
                .createdByUserId(club.getCreatedBy().getId())
                .createdAt(KstTime.toKst(club.getCreatedAt()))
                .updatedAt(KstTime.toKst(club.getUpdatedAt()))
                .build();
    }
}
