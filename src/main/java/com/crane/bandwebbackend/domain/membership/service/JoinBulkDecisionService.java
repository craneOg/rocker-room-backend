package com.crane.bandwebbackend.domain.membership.service;

import com.crane.bandwebbackend.domain.membership.dto.JoinBulkDecisionRequest;
import com.crane.bandwebbackend.domain.membership.dto.JoinBulkDecisionResponse;
import com.crane.bandwebbackend.domain.membership.entity.MembershipStatus;
import com.crane.bandwebbackend.global.error.ErrorCode;
import com.crane.bandwebbackend.global.error.BusinessException;
import com.crane.bandwebbackend.global.response.ApiIds;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** API-143: 각 항목을 독립 트랜잭션으로 처리한다. 부분 실패를 위해 바깥 트랜잭션은 열지 않는다. */
@Service
@Slf4j
@RequiredArgsConstructor
public class JoinBulkDecisionService {
	private final MembershipService memberships;
	private final MembershipDecisionService decisions;
	private final PlatformTransactionManager transactionManager;

	/** 소속 신청을 승인하거나 거절한다 */
	public JoinBulkDecisionResponse decide(
		Long clubId,
		Long actorUserId,
		JoinBulkDecisionRequest request
	) {
		// 일괄 결정 권한과 요청 크기·사유·중복 식별자를 먼저 검증한다.
		memberships.requireAdmin(clubId, actorUserId);
		if (request == null || request.decision() == null || request.membershipIds() == null
				|| request.membershipIds().isEmpty() || request.membershipIds().size() > 100) {
			throw BusinessException.of(ErrorCode.VALIDATION_ERROR, "일괄 결정 요청이 유효하지 않습니다.");
		}
		if (request.reason() != null && request.reason().length() > 500) {
			throw BusinessException.of(ErrorCode.VALIDATION_ERROR, "사유는 500자 이하여야 합니다.");
		}
		List<Long> ids = request.membershipIds().stream()
				.map(value -> ApiIds.positive(value, "membershipIds")).toList();
		if (new HashSet<>(ids).size() != ids.size()) {
			throw BusinessException.of(ErrorCode.VALIDATION_ERROR, "membershipIds에 중복 값이 있습니다.");
		}
		// 한 항목의 실패가 나머지를 롤백하지 않도록 항목별 새 트랜잭션을 사용한다.
		TransactionTemplate tx = new TransactionTemplate(transactionManager);
		tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		List<JoinBulkDecisionResponse.Success> succeeded = new ArrayList<>();
		List<JoinBulkDecisionResponse.Failure> failed = new ArrayList<>();
		// 각 결과를 성공과 실패로 분리하고 API 오류를 항목별 실패 사유로 변환한다.
		for (Long id : ids) {
			try {
				tx.executeWithoutResult(status -> decisions.decide(clubId, id, actorUserId,
						MembershipStatus.PENDING,
						request.decision() == JoinBulkDecisionRequest.Decision.APPROVE
								? MembershipStatus.ACTIVE : MembershipStatus.REJECTED,
						request.reason()));
				succeeded.add(new JoinBulkDecisionResponse.Success(id.toString()));
			} catch (BusinessException exception) {
				String reason = exception.getCode() == ErrorCode.NOT_FOUND ? "NOT_FOUND"
						: exception.getCode() == ErrorCode.STATE_CONFLICT ? "NOT_PENDING"
						: exception.getReason() == null ? exception.getCode().name() : exception.getReason();
				failed.add(new JoinBulkDecisionResponse.Failure(id.toString(), reason));
			} catch (DataIntegrityViolationException | OptimisticLockingFailureException exception) {
				failed.add(new JoinBulkDecisionResponse.Failure(id.toString(), "CONFLICT"));
			} catch (RuntimeException exception) {
				log.error("Failed to process join bulk decision membershipId={}", id, exception);
				failed.add(new JoinBulkDecisionResponse.Failure(id.toString(), "PROCESSING_FAILED"));
			}
		}
		return new JoinBulkDecisionResponse(succeeded, failed,
				new JoinBulkDecisionResponse.Summary(ids.size(), succeeded.size(), failed.size()));
	}
}
