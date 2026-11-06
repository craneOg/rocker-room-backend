package com.crane.bandwebbackend.domain.participant.service;

import com.crane.bandwebbackend.domain.membership.service.MembershipService;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMember;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMemberSource;
import com.crane.bandwebbackend.global.audit.AuditRecorder;
import com.crane.bandwebbackend.global.error.ApiErrorCode;
import com.crane.bandwebbackend.global.error.ApiException;
import com.crane.bandwebbackend.global.response.ApiIds;
import java.util.Map;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class ParticipantBulkService {
	private final MembershipService memberships;
	private final PerformanceMemberService performanceMembers;
	private final EntityManager entityManager;
	private final AuditRecorder auditRecorder;

	/** 공연 참여 추가한다 */
	@Transactional
	public List<PerformanceMember> add(
		Long clubId,
		Long performanceId,
		Long actorUserId,
		List<String> membershipIds,
		String reason
	) {
		// 관리자 권한과 목록 크기·사유·중복을 전체 저장 전에 검증한다.
		memberships.requireAdmin(clubId, actorUserId);
		if (membershipIds == null || membershipIds.isEmpty() || membershipIds.size() > 100) {
			throw new ApiException(ApiErrorCode.VALIDATION_ERROR, "membershipIds는 1~100개여야 합니다.");
		}
		if (reason != null && reason.length() > 500) {
			throw new ApiException(ApiErrorCode.VALIDATION_ERROR, "reason은 500자 이하여야 합니다.");
		}
		List<Long> ids = membershipIds.stream().map(id -> ApiIds.positive(id, "membershipIds")).toList();
		if (new HashSet<>(ids).size() != ids.size()) {
			throw new ApiException(ApiErrorCode.VALIDATION_ERROR, "membershipIds에 중복 값이 있습니다.");
		}
		// 모든 대상자를 같은 트랜잭션에서 추가해 하나라도 실패하면 전체를 롤백한다.
		List<PerformanceMember> result = new ArrayList<>();
		for (Long id : ids) {
			// 한 명이라도 실패하면 @Transactional 전체가 롤백된다.
			PerformanceMember member = performanceMembers.register(clubId, performanceId, actorUserId,
					PerformanceMemberSource.ADMIN, id);
			result.add(member);
			auditRecorder.record(clubId, actorUserId, "PERFORMANCE_MEMBER_ADDED", "PERFORMANCE_MEMBER", member.getId(), reason, Map.of("membershipId", id));
		}
		return result;
	}
}
