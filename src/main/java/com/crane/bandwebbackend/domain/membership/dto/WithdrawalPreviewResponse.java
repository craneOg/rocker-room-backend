package com.crane.bandwebbackend.domain.membership.dto;

import com.crane.bandwebbackend.domain.membership.entity.MembershipRole;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMemberStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** API-146 임원 내보내기 전 영향 미리보기. */
@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode
public class WithdrawalPreviewResponse {
    @JsonProperty("target")
    private Target target;

    @JsonProperty("succession")
    private Succession succession;

    @JsonProperty("performanceImpacts")
    private List<PerformanceImpact> performanceImpacts;

    @JsonProperty("teamLeaderCandidates")
    private List<TeamLeaderCandidates> teamLeaderCandidates;

    @JsonProperty("warnings")
    private List<String> warnings;

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @EqualsAndHashCode
    public static class Target {
        @JsonProperty("membershipId")
        private String membershipId;

        @JsonProperty("displayName")
        private String displayName;

        @JsonProperty("role")
        private MembershipRole role;
    }

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @EqualsAndHashCode
    public static class Succession {
        @JsonProperty("type")
        private String type;

        @JsonProperty("candidateMembershipId")
        private String candidateMembershipId;

        @JsonProperty("candidateName")
        private String candidateName;
    }

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @EqualsAndHashCode
    public static class PerformanceImpact {
        @JsonProperty("performanceId")
        private String performanceId;

        @JsonProperty("performanceName")
        private String performanceName;

        @JsonProperty("participationStatus")
        private PerformanceMemberStatus participationStatus;

        @JsonProperty("isTeamLeader")
        @Getter(onMethod_ = @JsonProperty("isTeamLeader"))
        private boolean isTeamLeader;

        @JsonProperty("teamName")
        private String teamName;
    }

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @EqualsAndHashCode
    public static class TeamLeaderCandidates {
        @JsonProperty("performanceId")
        private String performanceId;

        @JsonProperty("performanceName")
        private String performanceName;

        @JsonProperty("teamId")
        private String teamId;

        @JsonProperty("teamName")
        private String teamName;

        @JsonProperty("candidates")
        private List<Candidate> candidates;
    }

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @EqualsAndHashCode
    public static class Candidate {
        @JsonProperty("membershipId")
        private String membershipId;

        @JsonProperty("displayName")
        private String displayName;
    }
}
