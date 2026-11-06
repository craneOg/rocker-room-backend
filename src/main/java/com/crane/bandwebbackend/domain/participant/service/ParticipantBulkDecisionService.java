package com.crane.bandwebbackend.domain.participant.service;

import com.crane.bandwebbackend.domain.membership.service.MembershipService;
import com.crane.bandwebbackend.domain.participant.dto.ParticipantBulkDecisionRequest;
import com.crane.bandwebbackend.domain.participant.dto.ParticipantBulkDecisionResponse;
import com.crane.bandwebbackend.domain.participant.dto.ParticipantRemovalRequest;
import com.crane.bandwebbackend.domain.performance.entity.PerformanceStatus;
import com.crane.bandwebbackend.domain.performance.service.PerformanceService;
import com.crane.bandwebbackend.global.error.ErrorCode;
import com.crane.bandwebbackend.global.error.BusinessException;
import com.crane.bandwebbackend.global.response.ApiIds;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** API-144: 각 항목을 별도 트랜잭션으로 처리한다. 바깥 트랜잭션은 열지 않고 LOCKED에서는 일괄 결정을 막는다. */
@Service
@RequiredArgsConstructor
public class ParticipantBulkDecisionService {
	private final MembershipService memberships;
	private final PerformanceService performances;
	private final ParticipantDecisionService approvals;
	private final ParticipantRemovalService removals;
	private final PlatformTransactionManager transactionManager;

	/** 공연 참여 신청을 승인하거나 거절한다 */
	public ParticipantBulkDecisionResponse decide(
		Long clubId,
		Long performanceId,
		Long actorUserId,
		ParticipantBulkDecisionRequest request
	) {
		// 관리자 권한과 공연 상태를 확인해 잠긴 공연의 대량 변경을 차단한다.
		memberships.requireAdmin(clubId, actorUserId);
		var performance = performances.require(clubId, performanceId);
		if (performance.getStatus() != PerformanceStatus.DRAFT
				&& performance.getStatus() != PerformanceStatus.ACTIVE) {
			throw BusinessException.of(ErrorCode.STATE_CONFLICT, "일괄 결정이 가능한 공연 상태가 아닙니다.",
					"INVALID_STATE");
		}
		if (request == null || request.decision() == null || request.performanceMemberIds() == null
				|| request.performanceMemberIds().isEmpty() || request.performanceMemberIds().size() > 100) {
			throw BusinessException.of(ErrorCode.VALIDATION_ERROR, "일괄 결정 요청이 유효하지 않습니다.");
		}
		if (request.reason() != null && request.reason().length() > 500) {
			throw BusinessException.of(ErrorCode.VALIDATION_ERROR, "사유는 500자 이하여야 합니다.");
		}
		List<Long> ids = request.performanceMemberIds().stream()
				.map(value -> ApiIds.positive(value, "performanceMemberIds")).toList();
		if (new HashSet<>(ids).size() != ids.size()) {
			throw BusinessException.of(ErrorCode.VALIDATION_ERROR, "performanceMemberIds에 중복 값이 있습니다.");
		}
		// 각 참여자를 새 트랜잭션에서 처리해 일부 실패 결과를 반환한다.
		TransactionTemplate tx = new TransactionTemplate(transactionManager);
		tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		List<ParticipantBulkDecisionResponse.Success> succeeded = new ArrayList<>();
		List<ParticipantBulkDecisionResponse.Failure> failed = new ArrayList<>();
		// 항목마다 최신 공연 버전을 다시 읽어 승인 또는 거절 서비스에 전달한다.
		for (Long id : ids) {
			try {
				tx.executeWithoutResult(status -> {
					var current = performances.require(clubId, performanceId);
					if (request.decision() == ParticipantBulkDecisionRequest.Decision.APPROVE) {
						approvals.approve(clubId, performanceId, id, actorUserId,
								current.getVersion().toString(), request.reason());
					} else {
						removals.remove(clubId, performanceId, id, actorUserId,
								new ParticipantRemovalRequest(current.getVersion().toString(),
										request.reason(), List.of()), true);
					}
				});
				succeeded.add(new ParticipantBulkDecisionResponse.Success(id.toString()));
			} catch (BusinessException exception) {
				String reason = exception.getCode() == ErrorCode.NOT_FOUND ? "NOT_FOUND"
						: exception.getCode() == ErrorCode.STATE_CONFLICT ? "NOT_APPLIED"
						: exception.getReason() == null ? exception.getCode().name() : exception.getReason();
				failed.add(new ParticipantBulkDecisionResponse.Failure(id.toString(), reason));
			} catch (RuntimeException exception) {
				failed.add(new ParticipantBulkDecisionResponse.Failure(id.toString(), "INTERNAL_ERROR"));
			}
		}
		return new ParticipantBulkDecisionResponse(succeeded, failed,
				new ParticipantBulkDecisionResponse.Summary(ids.size(), succeeded.size(), failed.size()));
	}
}
