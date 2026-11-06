package com.crane.bandwebbackend.domain.participant.service;

import com.crane.bandwebbackend.domain.membership.service.MembershipService;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMember;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMemberStatus;
import com.crane.bandwebbackend.domain.participant.repository.PerformanceMemberRepository;
import com.crane.bandwebbackend.domain.performance.entity.Performance;
import com.crane.bandwebbackend.domain.performance.entity.PerformanceStatus;
import com.crane.bandwebbackend.domain.performance.service.PerformanceService;
import com.crane.bandwebbackend.global.audit.AuditRecorder;
import com.crane.bandwebbackend.global.error.ErrorCode;
import com.crane.bandwebbackend.global.error.BusinessException;
import java.util.Map;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** API-057: 참여 승인과 공연 버전 검사를 같은 트랜잭션에서 처리한다. */
@Service
@Transactional
@RequiredArgsConstructor
public class ParticipantDecisionService {
	private final MembershipService memberships;
	private final PerformanceService performances;
	private final PerformanceMemberRepository participants;
	private final EntityManager entityManager;
	private final AuditRecorder auditRecorder;

	/** 공연 참여 승인한다 */
	@Transactional
	public PerformanceMember approve(
		Long clubId,
		Long performanceId,
		Long participantId,
		Long actorUserId,
		String performanceVersion,
		String reason
	) {
		// 관리자 권한을 확인하고 공연 행을 잠가 버전 검사를 원자적으로 수행한다.
		memberships.requireAdmin(clubId, actorUserId);
		Performance performance = performances.requireLocked(clubId, performanceId);
		if (!String.valueOf(performance.getVersion()).equals(performanceVersion)) {
			throw BusinessException.of(ErrorCode.VERSION_CONFLICT, "공연 버전이 달라졌습니다.");
		}
		if (performance.getStatus() != PerformanceStatus.DRAFT
				&& performance.getStatus() != PerformanceStatus.ACTIVE
				&& performance.getStatus() != PerformanceStatus.LOCKED) {
			throw BusinessException.of(ErrorCode.STATE_CONFLICT, "참여 승인 가능 상태가 아닙니다.",
					"INVALID_STATE");
		}
		if (performance.getStatus() == PerformanceStatus.LOCKED && (reason == null || reason.isBlank())) {
			throw BusinessException.of(ErrorCode.VALIDATION_ERROR, "사유가 필요합니다.", "REASON_REQUIRED");
		}
		// 같은 동아리·공연의 신청 대기 참여자만 승인한다.
		PerformanceMember participant = participants.findByIdAndPerformance_IdAndClubId(
						participantId, performanceId, clubId)
				.orElseThrow(() -> BusinessException.of(ErrorCode.NOT_FOUND, "참여자를 찾을 수 없습니다."));
		if (participant.getStatus() != PerformanceMemberStatus.APPLIED) {
			throw BusinessException.of(ErrorCode.STATE_CONFLICT, "승인 대기 상태가 아닙니다.", "INVALID_STATE");
		}
		// 상태 전이와 일반 감사 로그를 같은 트랜잭션에 기록한다.
		participant.transitionTo(PerformanceMemberStatus.APPROVED, LocalDateTime.now(ZoneOffset.UTC));
		auditRecorder.record(clubId, actorUserId, "PERFORMANCE_MEMBER_APPROVED", "PERFORMANCE_MEMBER", participantId, reason, Map.of("from", "APPLIED", "to", "APPROVED"));
		// 확정 공연 예외 수정은 별도 감사 이벤트도 추가한다.
		if (performance.getStatus() == PerformanceStatus.LOCKED) {
			auditRecorder.record(clubId, actorUserId, "LOCKED_EXCEPTION_EDIT", "PERFORMANCE_MEMBER", participantId, reason, Map.of("from", "APPLIED", "to", "APPROVED"));
		}
		return participant;
	}
}
