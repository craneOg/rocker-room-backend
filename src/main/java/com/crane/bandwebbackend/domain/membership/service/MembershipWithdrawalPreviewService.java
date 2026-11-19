package com.crane.bandwebbackend.domain.membership.service;

import com.crane.bandwebbackend.domain.membership.dto.LeavePreviewResponse;
import com.crane.bandwebbackend.domain.membership.dto.WithdrawalPreviewResponse;
import com.crane.bandwebbackend.domain.membership.entity.MemberType;
import com.crane.bandwebbackend.domain.membership.entity.Membership;
import com.crane.bandwebbackend.domain.membership.entity.MembershipRole;
import com.crane.bandwebbackend.domain.membership.entity.MembershipStatus;
import com.crane.bandwebbackend.domain.membership.repository.MembershipRepository;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMember;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMemberStatus;
import com.crane.bandwebbackend.domain.participant.service.ParticipationLookup;
import com.crane.bandwebbackend.domain.participant.service.TeamLeaderSuccession;
import com.crane.bandwebbackend.domain.performance.entity.PerformanceStatus;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class MembershipWithdrawalPreviewService {
    private final MembershipService memberships;
    private final MembershipRepository repository;
    private final ParticipationLookup participants;
    private final EntityManager entityManager;
    private final TeamLeaderSuccession teamSuccession;

    /** 소속 변경 영향을 미리 조회한다 */
    @Transactional(readOnly = true)
    public WithdrawalPreviewResponse preview(Long clubId, Long membershipId, Long actorUserId) {
        // 실제 내보내기와 동일하게 관리자 권한과 대상 소속을 확인한다.
        memberships.requireAdmin(clubId, actorUserId);
        return compute(clubId, memberships.require(clubId, membershipId));
    }

    /** API-145: 본인이 나갈 때의 영향. 146과 같은 계산에 role만 담고, 후임이 없으면 비활성화 경고를 더한다 */
    @Transactional(readOnly = true)
    public LeavePreviewResponse leavePreview(Long clubId, Long actorUserId) {
        WithdrawalPreviewResponse result = compute(clubId, memberships.requireMember(clubId, actorUserId));
        List<String> warnings = new ArrayList<>(result.getWarnings());
        if (result.getSuccession() != null && result.getSuccession().getCandidateMembershipId() == null) {
            warnings.add("동아리가 비활성화됩니다.");
        }
        return new LeavePreviewResponse(
                result.getTarget().getRole(),
                result.getSuccession(),
                result.getPerformanceImpacts(),
                result.getTeamLeaderCandidates(),
                warnings);
    }

    /** 146·145 공통: 대상 소속이 나갈 때의 승계·공연 영향·팀장 후보를 계산한다 */
    private WithdrawalPreviewResponse compute(Long clubId, Membership target) {
        Long membershipId = target.getId();
        List<Membership> clubMembers = repository.findByClub_Id(clubId);
        // 대상이 유효한 마지막 관리자인지 계산해 관리자 승계 필요 여부를 판단한다.
        boolean soleAdmin = target.getRole() == MembershipRole.CLUB_ADMIN
                && clubMembers.stream()
                        .noneMatch(member -> !member.getId().equals(membershipId)
                                && member.getRole() == MembershipRole.CLUB_ADMIN
                                && (member.getStatus() == MembershipStatus.ACTIVE
                                        || member.getStatus() == MembershipStatus.ON_LEAVE));
        WithdrawalPreviewResponse.Succession succession = null;
        // 후임이 지정되지 않았을 때 사용할 가장 이른 활성 정회원을 미리 보여 준다.
        if (soleAdmin) {
            Membership candidate = clubMembers.stream()
                    .filter(member -> !member.getId().equals(membershipId))
                    .filter(member -> member.getMemberType() == MemberType.REGULAR
                            && member.getStatus() == MembershipStatus.ACTIVE)
                    .min(Comparator.comparing(
                                    Membership::getApprovedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(Membership::getId))
                    .orElse(null);
            succession = new WithdrawalPreviewResponse.Succession(
                    "DESIGNATED_OR_EARLIEST",
                    candidate == null ? null : candidate.getId().toString(),
                    candidate == null ? null : name(candidate));
        }
        List<WithdrawalPreviewResponse.PerformanceImpact> impacts = new ArrayList<>();
        List<WithdrawalPreviewResponse.TeamLeaderCandidates> teams = new ArrayList<>();
        List<String> teamWarnings = new ArrayList<>();
        // 진행 중 공연의 유효 참여만 순회해 철회 영향을 계산한다.
        for (PerformanceMember member : participants.membersOfMembership(membershipId)) {
            var performance = member.getPerformance();
            if (performance.getStatus() != PerformanceStatus.DRAFT
                    && performance.getStatus() != PerformanceStatus.ACTIVE
                    && performance.getStatus() != PerformanceStatus.LOCKED) {
                continue;
            }
            if (member.getStatus() != PerformanceMemberStatus.APPLIED
                    && member.getStatus() != PerformanceMemberStatus.APPROVED) {
                continue;
            }
            // 대상 참여자가 팀장인 팀을 조회해 공연별 영향에 포함한다.
            @SuppressWarnings("unchecked")
            List<Object[]> leaderRows = entityManager
                    .createNativeQuery("""
					SELECT t.id, t.name, t.status FROM team t WHERE t.performance_id = :performanceId
					AND t.leader_member_id = :participantId ORDER BY t.id
					""")
                    .setParameter("performanceId", performance.getId())
                    .setParameter("participantId", member.getId())
                    .getResultList();
            impacts.add(new WithdrawalPreviewResponse.PerformanceImpact(
                    performance.getId().toString(),
                    performance.getTitle(),
                    member.getStatus(),
                    !leaderRows.isEmpty(),
                    leaderRows.isEmpty() ? null : (String) leaderRows.get(0)[1]));
            // 각 팀에서 후임이 될 수 있는 활성 정회원 팀원을 조회한다.
            for (Object[] leader : leaderRows) {
                Long teamId = ((Number) leader[0]).longValue();
                // 실제 승계(TeamLeaderSuccession.apply)와 같은 후보 조건을 쓴다. 행: (참여 id, 소속 id, 사용자 id)
                String teamName = (String) leader[1];
                String teamStatus = String.valueOf(leader[2]);
                // 작성 중 팀은 삭제되어 후보가 없다.
                boolean draft = "DRAFT".equals(teamStatus);
                List<Object[]> candidateRows =
                        draft ? List.<Object[]>of() : teamSuccession.candidatesOf(teamId, member.getId());
                List<WithdrawalPreviewResponse.Candidate> candidates = candidateRows.stream()
                        .map(row -> ((Number) row[1]).longValue())
                        .map(id -> repository.findByIdAndClub_Id(id, clubId).orElseThrow())
                        .map(candidate -> new WithdrawalPreviewResponse.Candidate(
                                candidate.getId().toString(), name(candidate)))
                        .toList();
                if (draft) {
                    teamWarnings.add("작성 중 팀 '" + teamName + "'은(는) 삭제됩니다.");
                } else if ("SUBMITTED".equals(teamStatus)) {
                    teamWarnings.add("심사 대기 팀 '" + teamName + "'의 팀장은 지정과 관계없이 첫 번째 후보에게 자동 승계됩니다.");
                }
                teams.add(new WithdrawalPreviewResponse.TeamLeaderCandidates(
                        performance.getId().toString(),
                        performance.getTitle(),
                        teamId.toString(),
                        teamName,
                        teamStatus,
                        candidates.isEmpty() ? null : candidates.get(0).getMembershipId(),
                        candidates));
            }
        }
        // 공연 참여 철회가 발생할 때만 사용자 확인용 경고를 생성한다.
        List<String> warnings = new ArrayList<>();
        if (!impacts.isEmpty()) {
            warnings.add("진행 중 공연 " + impacts.size() + "건에서 참여가 철회됩니다.");
        }
        warnings.addAll(teamWarnings);
        return new WithdrawalPreviewResponse(
                new WithdrawalPreviewResponse.Target(membershipId.toString(), name(target), target.getRole()),
                succession,
                impacts,
                teams,
                warnings);
    }

    /** 소속의 동아리 표시명 또는 사용자 기본 이름을 반환한다. */
    private static String name(Membership membership) {
        return membership.getDisplayName() == null ? membership.getUser().getName() : membership.getDisplayName();
    }
}
