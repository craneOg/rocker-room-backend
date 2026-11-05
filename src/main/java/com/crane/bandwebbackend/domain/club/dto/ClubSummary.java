package com.crane.bandwebbackend.domain.club.dto;

import com.crane.bandwebbackend.domain.club.entity.Club;
import com.crane.bandwebbackend.global.serialization.IdString;
import lombok.Builder;
import lombok.Getter;

// 가입 전 사용자에게 보여 주는 동아리 요약. 가입 코드와 소속자 정보는 담지 않는다
@Getter
@Builder
public class ClubSummary {

    @IdString
    private final Long id;

    private final String name;
    private final String description;
    private final String clubType;
    private final String profileImageUrl;
    private final String themeColor;

    public static ClubSummary from(Club club) {
        return ClubSummary.builder()
                .id(club.getId())
                .name(club.getName())
                .description(club.getDescription())
                .clubType(club.getClubType().name())
                .profileImageUrl(club.getProfileImageUrl())
                .themeColor(club.getThemeColor())
                .build();
    }
}
