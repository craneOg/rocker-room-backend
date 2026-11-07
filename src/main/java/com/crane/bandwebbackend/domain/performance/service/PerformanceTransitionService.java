package com.crane.bandwebbackend.domain.performance.service;

import com.crane.bandwebbackend.domain.membership.service.MembershipService;
import com.crane.bandwebbackend.domain.club.entity.ClubStatus;
import com.crane.bandwebbackend.domain.performance.entity.Performance;
import com.crane.bandwebbackend.domain.performance.entity.PerformanceStatus;
import com.crane.bandwebbackend.domain.performance.repository.PerformanceRepository;
import com.crane.bandwebbackend.global.audit.AuditRecorder;
import com.crane.bandwebbackend.global.error.ErrorCode;
import com.crane.bandwebbackend.global.error.BusinessException;
import java.util.Map;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** API-045~049, 152: 상태 전이와 낙관적 락 검사를 한 트랜잭션에서 처리한다. */
@Service
@Transactional
@RequiredArgsConstructor
public class PerformanceTransitionService {
	private final MembershipService membershipService;
	private final PerformanceRepository repository;
	private final EntityManager entityManager;
	private final AuditRecorder auditRecorder;

	/** API-151: Member 입력이 있으면 공개 전 상태로 되돌릴 수 없다. */
	@Transactional
	public Performance revertToDraft(
		Long clubId,
		Long performanceId,
		Long actorUserId,
		String reason
	) {
		// 관리자 권한을 확인하고 공연 행을 잠가 되돌리기 판단과 상태 변경을 직렬화한다.
		membershipService.requireAdmin(clubId, actorUserId);
		Performance performance = repository.findWithLockByIdAndClub_Id(performanceId, clubId)
				.orElseThrow(() -> BusinessException.of(ErrorCode.NOT_FOUND, "공연을 찾을 수 없습니다."));
		if (performance.getClub().getStatus() != ClubStatus.ACTIVE) {
			throw BusinessException.of(ErrorCode.STATE_CONFLICT, "비활성 동아리에서는 변경할 수 없습니다.",
					"CLUB_INACTIVE");
		}
		if (performance.getStatus() != PerformanceStatus.ACTIVE) {
			throw BusinessException.of(ErrorCode.STATE_CONFLICT, "진행 중 공연이 아닙니다.", "NOT_ACTIVE");
		}
		if (reason != null && (reason.isBlank() || reason.length() > 500)) {
			throw BusinessException.of(ErrorCode.VALIDATION_ERROR, "사유는 1~500자여야 합니다.");
		}
		// 공개 이후 부원이 만든 참여·팀·곡·응답 데이터가 하나라도 있는지 합산한다.
		long inputCount = ((Number) entityManager.createNativeQuery("""
				SELECT
				  (SELECT COUNT(*) FROM performance_member pm WHERE pm.performance_id = :id
					 AND pm.source IN ('APPLICATION','TEAM_SUBMISSION'))
				+ (SELECT COUNT(*) FROM team t WHERE t.performance_id = :id AND t.source = 'SELF')
				+ (SELECT COUNT(*) FROM song s JOIN membership m ON m.id = s.proposed_by_membership_id
					 WHERE s.performance_id = :id AND m.role = 'MEMBER')
				+ (SELECT COUNT(*) FROM song_playability sp WHERE sp.performance_id = :id)
				+ (SELECT COUNT(*) FROM song_vote sv WHERE sv.performance_id = :id)
				+ (SELECT COUNT(*) FROM available_slot av JOIN performance_member pm
					 ON pm.id = av.performance_member_id WHERE pm.performance_id = :id)
				""").setParameter("id", performanceId).getSingleResult()).longValue();
		if (inputCount > 0) {
			throw BusinessException.of(ErrorCode.RULE_VIOLATION, "부원 입력이 있어 준비 중으로 되돌릴 수 없습니다.",
					"MEMBER_INPUT_EXISTS");
		}
		// 부원 입력이 없을 때만 초안으로 되돌리고 변경 내역을 기록한다.
		performance.transitionTo(PerformanceStatus.DRAFT);
		auditRecorder.record(clubId, actorUserId, "PERFORMANCE_STATUS_CHANGED", "PERFORMANCE", performanceId, reason, Map.of("from", "ACTIVE", "to", "DRAFT"));
		entityManager.flush();
		return performance;
	}

	/** 공연 변경한다 */
	@Transactional
	public Performance change(
		Long clubId,
		Long performanceId,
		Long actorUserId,
		PerformanceStatus source,
		PerformanceStatus target,
		String version,
		String reason
	) {
		// 관리자 권한과 현재 공연·동아리 상태를 확인한다.
		membershipService.requireAdmin(clubId, actorUserId);
		Performance performance = repository.findByIdAndClub_Id(performanceId, clubId)
				.orElseThrow(() -> BusinessException.of(ErrorCode.NOT_FOUND, "공연을 찾을 수 없습니다."));
		if (performance.getClub().getStatus() != ClubStatus.ACTIVE) {
			throw BusinessException.of(ErrorCode.STATE_CONFLICT, "비활성 동아리에서는 변경할 수 없습니다.",
					"CLUB_INACTIVE");
		}
		if (source != null && performance.getStatus() != source) {
			throw BusinessException.of(ErrorCode.STATE_CONFLICT, "공연 상태가 맞지 않습니다.", "INVALID_STATE");
		}
		// 버전이 전달된 상태 변경은 낙관적 동시성 검사를 수행한다.
		if (version != null) {
			if (!version.matches("[0-9]+")) {
				throw BusinessException.of(ErrorCode.VALIDATION_ERROR, "version 형식이 올바르지 않습니다.");
			}
			if (!version.equals(String.valueOf(performance.getVersion()))) {
				throw BusinessException.of(ErrorCode.VERSION_CONFLICT, "공연 버전이 달라졌습니다.");
			}
		}
		// 취소·복구·확정 해제처럼 영향이 큰 전이는 필수 사유를 요구한다.
		if ((target == PerformanceStatus.CANCELED || performance.getStatus() == PerformanceStatus.CANCELED
				|| (performance.getStatus() == PerformanceStatus.LOCKED && target == PerformanceStatus.ACTIVE)
				|| (performance.getStatus() == PerformanceStatus.FINISHED && target == PerformanceStatus.LOCKED))
				&& (reason == null || reason.isBlank())) {
			throw BusinessException.of(ErrorCode.VALIDATION_ERROR, "사유가 필요합니다.", "REASON_REQUIRED");
		}
		if (reason != null && reason.length() > 500) {
			throw BusinessException.of(ErrorCode.VALIDATION_ERROR, "사유는 500자 이하여야 합니다.");
		}
		// 엔티티 상태 머신으로 전이한 뒤 이전·이후 상태를 감사 로그에 남긴다.
		PerformanceStatus previous = performance.getStatus();
		try {
			performance.transitionTo(target);
		} catch (IllegalStateException | IllegalArgumentException exception) {
			throw BusinessException.of(ErrorCode.STATE_CONFLICT, exception.getMessage(), "INVALID_STATE");
		}
		auditRecorder.record(clubId, actorUserId, "PERFORMANCE_STATUS_CHANGED", "PERFORMANCE", performanceId, reason, Map.of("from", previous.toString(), "to", target.toString()));
		return performance;
	}
}
