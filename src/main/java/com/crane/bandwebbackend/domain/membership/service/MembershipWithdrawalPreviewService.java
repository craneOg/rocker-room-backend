package com.crane.bandwebbackend.domain.membership.service;

import com.crane.bandwebbackend.domain.membership.dto.WithdrawalPreviewResponse;
import com.crane.bandwebbackend.domain.membership.entity.MemberType;
import com.crane.bandwebbackend.domain.membership.entity.Membership;
import com.crane.bandwebbackend.domain.membership.entity.MembershipRole;
import com.crane.bandwebbackend.domain.membership.entity.MembershipStatus;
import com.crane.bandwebbackend.domain.membership.repository.MembershipRepository;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMember;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMemberStatus;
import com.crane.bandwebbackend.domain.participant.repository.PerformanceMemberRepository;
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
	private final PerformanceMemberRepository participants;
	private final EntityManager entityManager;

	/** 소속 변경 영향을 미리 조회한다 */
	@Transactional(readOnly = true)
	public WithdrawalPreviewResponse preview(
		Long clubId,
		Long membershipId,
		Long actorUserId
	) {
		// 실제 내보내기와 동일하게 관리자 권한과 대상 소속을 확인한다.
		memberships.requireAdmin(clubId, actorUserId);
		Membership target = memberships.require(clubId, membershipId);
		List<Membership> clubMembers = repository.findByClub_Id(clubId);
		// 대상이 유효한 마지막 관리자인지 계산해 관리자 승계 필요 여부를 판단한다.
		boolean soleAdmin = target.getRole() == MembershipRole.CLUB_ADMIN
				&& clubMembers.stream().noneMatch(member -> !member.getId().equals(membershipId)
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
					.min(Comparator.comparing(Membership::getApprovedAt,
							Comparator.nullsLast(Comparator.naturalOrder()))
							.thenComparing(Membership::getId)).orElse(null);
			succession = new WithdrawalPreviewResponse.Succession("DESIGNATED_OR_EARLIEST",
					candidate == null ? null : candidate.getId().toString(),
					candidate == null ? null : name(candidate));
		}
		List<WithdrawalPreviewResponse.PerformanceImpact> impacts = new ArrayList<>();
		List<WithdrawalPreviewResponse.TeamLeaderCandidates> teams = new ArrayList<>();
		// 진행 중 공연의 유효 참여만 순회해 철회 영향을 계산한다.
		for (PerformanceMember member : participants.findByMembership_Id(membershipId)) {
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
			List<Object[]> leaderRows = entityManager.createNativeQuery("""
					SELECT t.id, t.name FROM team t WHERE t.performance_id = :performanceId
					AND t.leader_member_id = :participantId
					""").setParameter("performanceId", performance.getId())
					.setParameter("participantId", member.getId()).getResultList();
			impacts.add(new WithdrawalPreviewResponse.PerformanceImpact(performance.getId().toString(),
					performance.getTitle(), member.getStatus(), !leaderRows.isEmpty(),
					leaderRows.isEmpty() ? null : (String) leaderRows.get(0)[1]));
			// 각 팀에서 후임이 될 수 있는 활성 정회원 팀원을 조회한다.
			for (Object[] leader : leaderRows) {
				Long teamId = ((Number) leader[0]).longValue();
				@SuppressWarnings("unchecked")
				List<Number> candidateIds = entityManager.createNativeQuery("""
						SELECT m.id FROM team_member tm JOIN performance_member pm
						  ON pm.id = tm.performance_member_id
						JOIN membership m ON m.id = pm.membership_id
						WHERE tm.team_id = :teamId AND pm.id <> :participantId
						AND pm.status IN ('APPLIED','APPROVED')
						AND m.member_type = 'REGULAR' AND m.status IN ('ACTIVE','ON_LEAVE')
						ORDER BY tm.created_at, tm.id
						""").setParameter("teamId", teamId)
						.setParameter("participantId", member.getId()).getResultList();
				List<WithdrawalPreviewResponse.Candidate> candidates = candidateIds.stream()
						.map(Number::longValue).map(id -> repository.findByIdAndClub_Id(id, clubId).orElseThrow())
						.map(candidate -> new WithdrawalPreviewResponse.Candidate(
								candidate.getId().toString(), name(candidate))).toList();
				teams.add(new WithdrawalPreviewResponse.TeamLeaderCandidates(
						performance.getId().toString(), performance.getTitle(), teamId.toString(),
						(String) leader[1], candidates));
			}
		}
		// 공연 참여 철회가 발생할 때만 사용자 확인용 경고를 생성한다.
		List<String> warnings = impacts.isEmpty() ? List.of()
				: List.of("진행 중 공연 " + impacts.size() + "건에서 참여가 철회됩니다.");
		return new WithdrawalPreviewResponse(new WithdrawalPreviewResponse.Target(
				membershipId.toString(), name(target), target.getRole()),
				succession, impacts, teams, warnings);
	}

	/** 소속의 동아리 표시명 또는 사용자 기본 이름을 반환한다. */
	private static String name(Membership membership) {
		return membership.getDisplayName() == null ? membership.getUser().getName()
				: membership.getDisplayName();
	}
}
