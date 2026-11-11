package com.crane.bandwebbackend.domain.membership.dto;

import com.crane.bandwebbackend.domain.membership.entity.MembershipRole;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMemberStatus;
import java.util.List;

/** API-146 임원 내보내기 전 영향 미리보기. */
public record WithdrawalPreviewResponse(
        Target target,
        Succession succession,
        List<PerformanceImpact> performanceImpacts,
        List<TeamLeaderCandidates> teamLeaderCandidates,
        List<String> warnings) {
    public record Target(String membershipId, String displayName, MembershipRole role) {}

    public record Succession(String type, String candidateMembershipId, String candidateName) {}

    public record PerformanceImpact(
            String performanceId,
            String performanceName,
            PerformanceMemberStatus participationStatus,
            boolean isTeamLeader,
            String teamName) {}

    public record TeamLeaderCandidates(
            String performanceId, String performanceName, String teamId, String teamName, List<Candidate> candidates) {}

    public record Candidate(String membershipId, String displayName) {}
}
