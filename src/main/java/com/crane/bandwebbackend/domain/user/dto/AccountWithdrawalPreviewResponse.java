package com.crane.bandwebbackend.domain.user.dto;

import com.crane.bandwebbackend.global.serialization.IdString;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

// API 134 응답 data. warnings 항목은 해당 없는 키도 null로 내려야 해서 맵으로 둔다(009의 409 warnings와 같은 모양)
@Getter
@RequiredArgsConstructor
public class AccountWithdrawalPreviewResponse {

    private final List<ClubItem> clubs;
    private final List<Map<String, Object>> warnings;

    @Getter
    @RequiredArgsConstructor
    public static class ClubItem {

        @IdString
        private final Long clubId;

        private final String clubName;
        private final String role;
        private final Succession succession;
        private final List<TeamLeaderTransfer> teamLeaderTransfers;
    }

    @Getter
    @RequiredArgsConstructor
    public static class Succession {

        // DESIGNATED_OR_EARLIEST, NOT_REQUIRED, NONE
        private final String type;

        @IdString
        private final Long candidateMembershipId;

        @IdString
        private final Long candidateUserId;

        private final String candidateName;
        private final List<Candidate> candidates;
    }

    @Getter
    @RequiredArgsConstructor
    public static class Candidate {

        @IdString
        private final Long membershipId;

        @IdString
        private final Long userId;

        private final String displayName;
    }

    @Getter
    @RequiredArgsConstructor
    public static class TeamLeaderTransfer {

        @IdString
        private final Long performanceId;

        private final String performanceName;

        @IdString
        private final Long teamId;

        private final String teamName;

        // APPROVED, SUBMITTED, DRAFT
        private final String teamStatus;

        @IdString
        private final Long defaultMembershipId;

        private final List<TeamCandidate> candidates;
    }

    @Getter
    @RequiredArgsConstructor
    public static class TeamCandidate {

        @IdString
        private final Long membershipId;

        private final String displayName;
    }
}
