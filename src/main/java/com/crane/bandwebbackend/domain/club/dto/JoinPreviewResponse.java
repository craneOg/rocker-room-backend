package com.crane.bandwebbackend.domain.club.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// API 12·13 응답: 동아리 요약과 "내" 가입 상태. canApply는 지금 가입을 신청할 수 있는지다
@Getter
@RequiredArgsConstructor
public class JoinPreviewResponse {

    private final ClubSummary club;
    private final String existingMembershipStatus;
    private final boolean canApply;
}
