package com.crane.bandwebbackend.domain.club.dto;

import com.crane.bandwebbackend.domain.club.entity.Club;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

// API 127 응답: 로그인 전에는 이름·유형·소개·사진·대표 색상만 보여 준다(id·멤버 정보 없음, C-17)
@Getter
@RequiredArgsConstructor
public class InvitationPreviewResponse {

    private final String name;
    private final String clubType;
    private final String description;
    private final String profileImageUrl;
    private final String themeColor;

    public static InvitationPreviewResponse from(Club club) {
        return new InvitationPreviewResponse(
                club.getName(),
                club.getClubType().name(),
                club.getDescription(),
                club.getProfileImageUrl(),
                club.getThemeColor());
    }
}
