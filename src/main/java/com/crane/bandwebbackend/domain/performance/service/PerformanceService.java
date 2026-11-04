package com.crane.bandwebbackend.domain.performance.service;

import com.crane.bandwebbackend.domain.membership.service.MembershipService;
import com.crane.bandwebbackend.domain.membership.entity.MemberType;
import com.crane.bandwebbackend.domain.membership.entity.Membership;
import com.crane.bandwebbackend.domain.membership.entity.MembershipRole;
import com.crane.bandwebbackend.domain.participant.service.ParticipationLookup;
import com.crane.bandwebbackend.domain.performance.entity.Performance;
import com.crane.bandwebbackend.domain.performance.entity.PerformancePolicy;
import com.crane.bandwebbackend.domain.performance.entity.PerformanceStatus;
import com.crane.bandwebbackend.domain.performance.entity.PerformancePreset;
import com.crane.bandwebbackend.domain.performance.repository.PerformancePolicyRepository;
import com.crane.bandwebbackend.domain.performance.repository.PerformanceRepository;
import com.crane.bandwebbackend.global.error.ApiErrorCode;
import com.crane.bandwebbackend.global.error.ApiException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class PerformanceService {
	private final PerformanceRepository repository;
	private final PerformancePolicyRepository policyRepository;
	private final MembershipService membershipService;
	private final ParticipationLookup participationLookup;

	/** 공연 존재와 접근 권한을 확인한다 */
	@Transactional(readOnly = true)
	public Performance require(
		Long clubId,
		Long performanceId
	) {
		// 동아리 ID를 조회 조건에 포함해 다른 동아리의 공연 ID를 숨긴다.
		return repository.findByIdAndClub_Id(performanceId, clubId)
				.orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND, "공연을 찾을 수 없습니다."));
	}

	/** 공연 목록을 조회한다 */
	@Transactional(readOnly = true)
	public List<Performance> list(
		Long clubId,
		Long actorUserId
	) {
		// 요청자의 소속을 확인하고 역할·유형별 가시성 규칙을 모든 공연에 적용한다.
		Membership actor = membershipService.requireMember(clubId, actorUserId);
		return repository.findByClub_Id(clubId).stream()
				.filter(performance -> visible(performance, actor)).toList();
	}

	/** PERF-01: 필터·페이지를 DB에서 적용한다. GUEST는 현재 신청·승인된 공연 ID만 허용한다. */
	@Transactional(readOnly = true)
	public Page<Performance> page(
		Long clubId,
		Long actorUserId,
		PerformanceStatus status,
		PerformancePreset preset,
		Pageable pageable
	) {
		// 동아리 경계를 기본 조건으로 시작해 요청 필터를 동적으로 조합한다.
		Membership actor = membershipService.requireMember(clubId, actorUserId);
		Specification<Performance> spec = (root, query, builder) ->
				builder.equal(root.get("club").get("id"), clubId);
		if (status != null) {
			spec = spec.and((root, query, builder) -> builder.equal(root.get("status"), status));
		}
		if (preset != null) {
			spec = spec.and((root, query, builder) -> builder.equal(root.get("preset"), preset));
		}
		if (actor.getRole() != MembershipRole.CLUB_ADMIN) {
			spec = spec.and((root, query, builder) -> builder.notEqual(root.get("status"), PerformanceStatus.DRAFT));
		}
		// 객원은 실제 신청 또는 승인된 공연 ID 범위로 다시 제한한다.
		if (actor.getMemberType() == MemberType.GUEST) {
			List<Long> ids = participationLookup.visiblePerformanceIds(actor.getId());
			if (ids.isEmpty()) {
				return Page.empty(pageable);
			}
			spec = spec.and((root, query, builder) -> root.get("id").in(ids));
		}
		return repository.findAll(spec, pageable);
	}

	/** 공연 상세 정보를 조회한다 */
	@Transactional(readOnly = true)
	public Performance detail(
		Long clubId,
		Long performanceId,
		Long actorUserId
	) {
		// 소속과 공연 존재를 확인한 뒤 보이지 않는 공연도 404로 숨긴다.
		Membership actor = membershipService.requireMember(clubId, actorUserId);
		Performance performance = require(clubId, performanceId);
		if (!visible(performance, actor)) {
			throw new ApiException(ApiErrorCode.NOT_FOUND, "공연을 찾을 수 없습니다.");
		}
		return performance;
	}

	/** 공연 운영 정책을 조회한다 */
	@Transactional(readOnly = true)
	public PerformancePolicy policy(
		Long clubId,
		Long performanceId,
		Long actorUserId
	) {
		// 공연 상세 접근 권한을 재사용한 뒤 1:1 정책을 조회한다.
		detail(clubId, performanceId, actorUserId);
		return policyRepository.findById(performanceId)
				.orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND, "공연 정책을 찾을 수 없습니다."));
	}

	/** 소속 유형과 참여 이력을 기준으로 공연 열람 가능 여부를 판단한다. */
	private boolean visible(Performance performance, Membership actor) {
		// 초안은 관리자에게만, 객원은 유효 참여 공연만 공개한다.
		if (performance.getStatus() == PerformanceStatus.DRAFT && actor.getRole() != MembershipRole.CLUB_ADMIN) {
			return false;
		}
		return actor.getMemberType() != MemberType.GUEST
				|| participationLookup.isVisibleToGuest(performance.getId(), actor.getId());
	}
}
