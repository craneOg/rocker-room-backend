package com.crane.bandwebbackend.domain.participant.service;

import com.crane.bandwebbackend.domain.membership.entity.MemberType;
import com.crane.bandwebbackend.domain.membership.entity.Membership;
import com.crane.bandwebbackend.domain.membership.entity.MembershipRole;
import com.crane.bandwebbackend.domain.membership.entity.MembershipStatus;
import com.crane.bandwebbackend.domain.membership.service.MembershipService;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMember;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMemberSource;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMemberStatus;
import com.crane.bandwebbackend.domain.participant.repository.PerformanceMemberRepository;
import com.crane.bandwebbackend.domain.performance.entity.FeatureState;
import com.crane.bandwebbackend.domain.performance.entity.Performance;
import com.crane.bandwebbackend.domain.performance.entity.PerformancePolicy;
import com.crane.bandwebbackend.domain.performance.entity.PerformanceStatus;
import com.crane.bandwebbackend.domain.performance.service.PerformanceService;
import com.crane.bandwebbackend.global.error.ErrorCode;
import com.crane.bandwebbackend.global.error.BusinessException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class PerformanceMemberService {
	private final PerformanceMemberRepository repository;
	private final MembershipService membershipService;
	private final PerformanceService performanceService;

	/** 공연 참여 목록을 조회한다 */
	@Transactional(readOnly = true)
	public List<PerformanceMember> list(
		Long clubId,
		Long performanceId,
		Long actorUserId
	) {
		// 정회원 여부와 공연 열람 권한을 먼저 확인한다.
		Membership actor = membershipService.requireMember(clubId, actorUserId);
		if (actor.getMemberType() != MemberType.REGULAR) {
			throw BusinessException.of(ErrorCode.NOT_FOUND, "공연 참여자 목록을 찾을 수 없습니다.");
		}
		performanceService.detail(clubId, performanceId, actorUserId);
		List<PerformanceMember> members = repository.findByPerformance_IdAndClubId(performanceId, clubId);
		// 관리자는 전체 상태를 보고 일반 부원은 승인된 참여자만 본다.
		if (actor.getRole() == MembershipRole.CLUB_ADMIN) {
			return members;
		}
		return members.stream().filter(member -> member.getStatus() == PerformanceMemberStatus.APPROVED).toList();
	}

	/** 공연 참여 본인 항목을 조회한다 */
	@Transactional(readOnly = true)
	public PerformanceMember mine(
		Long clubId,
		Long performanceId,
		Long actorUserId
	) {
		// 현재 소속과 공연 열람 권한을 확인한 뒤 본인의 참여 행만 조회한다.
		Membership actor = membershipService.requireMember(clubId, actorUserId);
		performanceService.detail(clubId, performanceId, actorUserId);
		return repository.findByPerformance_IdAndMembership_Id(performanceId, actor.getId())
				.orElseThrow(() -> BusinessException.of(ErrorCode.NOT_FOUND, "공연 참여 기록이 없습니다."));
	}

	/** 공연 참여 등록한다 */
	@Transactional
	public PerformanceMember register(
		Long clubId,
		Long performanceId,
		Long actorUserId,
		PerformanceMemberSource source,
		Long membershipId
	) {
		// 호출 경로를 본인 신청 또는 관리자 지정으로 제한한다.
		if (source != PerformanceMemberSource.APPLICATION && source != PerformanceMemberSource.ADMIN) {
			throw BusinessException.of(ErrorCode.VALIDATION_ERROR, "참여 등록 경로가 유효하지 않습니다.");
		}
		Membership actor = membershipService.requireMember(clubId, actorUserId);
		Performance performance = performanceService.require(clubId, performanceId);
		Membership target;
		LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
		// 본인 신청은 공연 상태·정책 마감·정회원 자격을 모두 확인한다.
		if (source == PerformanceMemberSource.APPLICATION) {
			if (membershipId != null) {
				throw BusinessException.of(ErrorCode.VALIDATION_ERROR, "본인 신청에는 membershipId를 보낼 수 없습니다.");
			}
			target = actor;
			if (performance.getStatus() != PerformanceStatus.ACTIVE) {
				throw BusinessException.of(ErrorCode.STATE_CONFLICT, "신청 가능한 공연 상태가 아닙니다.");
			}
			PerformancePolicy policy = performanceService.policy(clubId, performanceId, actorUserId);
			if (policy.getApplicationState() != FeatureState.OPEN
					|| (policy.getApplicationDeadline() != null && !now.isBefore(policy.getApplicationDeadline()))) {
				throw BusinessException.of(ErrorCode.STATE_CONFLICT, "참여 신청이 열려 있지 않습니다.");
			}
			if (target.getMemberType() != MemberType.REGULAR
					|| (target.getStatus() != MembershipStatus.ACTIVE
					&& target.getStatus() != MembershipStatus.ON_LEAVE)) {
				throw BusinessException.of(ErrorCode.FORBIDDEN, "공연 신청 자격이 없습니다.");
			}
		} else {
			// 관리자 지정은 대상 소속과 공연 상태를 별도 규칙으로 검증한다.
			membershipService.requireAdmin(clubId, actorUserId);
			if (membershipId == null) {
				throw BusinessException.of(ErrorCode.VALIDATION_ERROR, "관리자 지정에는 membershipId가 필요합니다.");
			}
			if (performance.getStatus() != PerformanceStatus.DRAFT
					&& performance.getStatus() != PerformanceStatus.ACTIVE) {
				throw BusinessException.of(ErrorCode.STATE_CONFLICT, "참여자를 지정할 수 없는 공연 상태입니다.");
			}
			target = membershipService.require(clubId, membershipId);
			if (target.getStatus() != MembershipStatus.ACTIVE && target.getStatus() != MembershipStatus.ON_LEAVE
					&& target.getStatus() != MembershipStatus.GRADUATED) {
				throw BusinessException.of(ErrorCode.RULE_VIOLATION, "지정 가능한 소속 상태가 아닙니다.");
			}
		}
		// 유일한 참여 행이 있으면 종료 상태만 재활성화하고, 아니면 새 행을 만든다.
		PerformanceMember existing = repository.findByPerformance_IdAndMembership_Id(performanceId, target.getId())
				.orElse(null);
		if (existing != null) {
			if (existing.getStatus() != PerformanceMemberStatus.REJECTED
					&& existing.getStatus() != PerformanceMemberStatus.WITHDRAWN) {
				throw BusinessException.of(ErrorCode.DUPLICATE, "이미 유효한 공연 참여 기록이 있습니다.",
						"ALREADY_APPLIED");
			}
			existing.reapply(source, now);
			repository.flush();
			return existing;
		}
		PerformanceMember member = source == PerformanceMemberSource.APPLICATION
				? PerformanceMember.apply(performance, target)
				: PerformanceMember.addByAdmin(performance, target, now);
		return repository.saveAndFlush(member);
	}
}
