package com.crane.bandwebbackend.domain.club.dto;

import com.crane.bandwebbackend.domain.club.entity.Club;
import com.crane.bandwebbackend.global.serialization.IdString;
import com.crane.bandwebbackend.global.time.KstTime;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.Getter;

// API 15·16 응답 data: Club. 부원에게 보이면 안 되므로 joinCode는 담지 않는다
@Getter
@Builder
public class ClubDetailResponse {

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

    @IdString
    private final Long createdByUserId;

    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;

    public static ClubDetailResponse from(Club club) {
        return ClubDetailResponse.builder()
                .id(club.getId())
                .name(club.getName())
                .description(club.getDescription())
                .clubType(club.getClubType().name())
                .status(club.getStatus().name())
                .searchable(club.isSearchable())
                .profileImageUrl(club.getProfileImageUrl())
                .themeColor(club.getThemeColor())
                .createdByUserId(club.getCreatedBy().getId())
                .createdAt(KstTime.toKst(club.getCreatedAt()))
                .updatedAt(KstTime.toKst(club.getUpdatedAt()))
                .build();
    }
}
