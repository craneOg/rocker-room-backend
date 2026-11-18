package com.crane.bandwebbackend.domain.user.dto;

import com.crane.bandwebbackend.global.serialization.IdString;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

// API 009 응답 data: 실제로 후임이 정해진 동아리·팀만 담는다
@Getter
@RequiredArgsConstructor
public class AccountWithdrawalResponse {

    private final Successions successions;

    @Getter
    @RequiredArgsConstructor
    public static class Successions {

        private final List<ClubSuccession> clubs;
        private final List<TeamSuccession> teams;
    }

    @Getter
    @RequiredArgsConstructor
    public static class ClubSuccession {

        @IdString
        private final Long clubId;

        @IdString
        private final Long successorUserId;
    }

    @Getter
    @RequiredArgsConstructor
    public static class TeamSuccession {

        @IdString
        private final Long teamId;

        @IdString
        private final Long newLeaderMembershipId;
    }
}
