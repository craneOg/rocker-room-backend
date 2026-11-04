package com.crane.bandwebbackend.domain.membership.service;

import com.crane.bandwebbackend.domain.membership.dto.MembershipBulkRequest;
import com.crane.bandwebbackend.domain.membership.dto.MembershipBulkResponse;
import com.crane.bandwebbackend.domain.membership.entity.MembershipStatus;
import com.crane.bandwebbackend.domain.part.entity.Part;
import com.crane.bandwebbackend.domain.part.repository.PartRepository;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMemberSource;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMemberStatus;
import com.crane.bandwebbackend.domain.participant.repository.PerformanceMemberRepository;
import com.crane.bandwebbackend.domain.participant.service.PerformanceMemberService;
import com.crane.bandwebbackend.domain.performance.repository.PerformanceRepository;
import com.crane.bandwebbackend.global.audit.AuditLog;
import com.crane.bandwebbackend.global.error.ApiErrorCode;
import com.crane.bandwebbackend.global.error.ApiException;
import com.crane.bandwebbackend.global.response.ApiIds;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** API-142: 각 항목을 별도 트랜잭션으로 커밋한다. 부분 실패를 위해 바깥 트랜잭션은 열지 않는다. */
@Service
@RequiredArgsConstructor
public class MembershipBulkService {
	private final MembershipService memberships;
	private final MembershipStatusService statusService;
	private final PartRepository parts;
	private final PerformanceRepository performances;
	private final PerformanceMemberRepository performanceMembers;
	private final PerformanceMemberService participantService;
	private final EntityManager entityManager;
	private final PlatformTransactionManager transactionManager;

	/** 소속 일괄 요청을 처리한다 */
	public MembershipBulkResponse process(
		Long clubId,
		Long actorUserId,
		MembershipBulkRequest request
	) {
		// 관리자 권한과 요청 크기·본문 형식·사유 길이를 일괄 검증한다.
		memberships.requireAdmin(clubId, actorUserId);
		if (request == null || request.membershipIds() == null || request.membershipIds().isEmpty()
				|| request.membershipIds().size() > 100 || request.action() == null || request.params() == null
				|| !request.params().isObject() || (request.reason() != null
				&& (request.reason().isBlank() || request.reason().length() > 500))) {
			throw new ApiException(ApiErrorCode.VALIDATION_ERROR, "일괄 처리 요청이 유효하지 않습니다.");
		}
		List<Long> ids = request.membershipIds().stream()
				.map(value -> ApiIds.positive(value, "membershipIds")).toList();
		if (new HashSet<>(ids).size() != ids.size()) {
			throw new ApiException(ApiErrorCode.VALIDATION_ERROR, "membershipIds가 중복됩니다.");
		}
		// 작업 종류별 파라미터와 참조 엔티티를 전체 처리 전에 검증한다.
		Params params = validateParams(clubId, request);
		// 항목별 독립 커밋으로 일부 실패를 허용한다.
		TransactionTemplate tx = new TransactionTemplate(transactionManager);
		tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		List<MembershipBulkResponse.Success> succeeded = new ArrayList<>();
		List<MembershipBulkResponse.Failure> failed = new ArrayList<>();
		// 각 소속 처리 결과를 성공·실패 목록으로 분리한다.
		for (Long id : ids) {
			try {
				Boolean skipped = tx.execute(status -> applyOne(clubId, actorUserId, id, request, params));
				succeeded.add(new MembershipBulkResponse.Success(id.toString(), Boolean.TRUE.equals(skipped)));
			} catch (ApiException exception) {
				String reason = exception.getCode() == ApiErrorCode.NOT_FOUND ? "NOT_FOUND"
						: exception.getReason() == null ? exception.getCode().name() : exception.getReason();
				failed.add(new MembershipBulkResponse.Failure(id.toString(), reason));
			}
		}
		int skipped = (int) succeeded.stream().filter(MembershipBulkResponse.Success::skipped).count();
		return new MembershipBulkResponse(succeeded, failed,
				new MembershipBulkResponse.Summary(ids.size(), succeeded.size(), failed.size(), skipped));
	}

	/** 일괄 변경 요청의 상태별 필수 인자와 참조 대상을 검증한다. */
	private Params validateParams(Long clubId, MembershipBulkRequest request) {
		// action마다 필요한 JSON 필드만 읽어 내부 실행용 불변 파라미터로 변환한다.
		JsonNode node = request.params();
		return switch (request.action()) {
			case CHANGE_STATUS -> {
				String value = requiredText(node, "status");
				MembershipStatus status;
				try { status = MembershipStatus.valueOf(value); }
				catch (IllegalArgumentException exception) {
					throw new ApiException(ApiErrorCode.VALIDATION_ERROR, "status가 유효하지 않습니다.");
				}
				if (status != MembershipStatus.ACTIVE && status != MembershipStatus.ON_LEAVE
						&& status != MembershipStatus.GRADUATED) {
					throw new ApiException(ApiErrorCode.VALIDATION_ERROR, "status가 유효하지 않습니다.");
				}
				yield new Params(status, null, List.of(), null);
			}
			case CHANGE_PARTS -> {
				String mode = requiredText(node, "mode");
				if (!mode.equals("ADD") && !mode.equals("REMOVE")) {
					throw new ApiException(ApiErrorCode.VALIDATION_ERROR, "mode가 유효하지 않습니다.");
				}
				JsonNode values = node.get("partIds");
				if (values == null || !values.isArray() || values.isEmpty() || values.size() > 20) {
					throw new ApiException(ApiErrorCode.VALIDATION_ERROR, "partIds는 1~20개여야 합니다.");
				}
				List<Long> partIds = new ArrayList<>();
				for (JsonNode value : values) {
					partIds.add(ApiIds.positive(value.asText(), "partIds"));
				}
				if (new HashSet<>(partIds).size() != partIds.size()) {
					throw new ApiException(ApiErrorCode.VALIDATION_ERROR, "partIds가 중복됩니다.");
				}
				for (Long id : partIds) {
					Part part = parts.findByIdAndClub_Id(id, clubId)
							.orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND, "파트를 찾을 수 없습니다."));
					if (mode.equals("ADD") && !part.isActive()) {
						throw new ApiException(ApiErrorCode.RULE_VIOLATION, "사용 중지 파트는 추가할 수 없습니다.",
								"PART_INACTIVE");
					}
				}
				yield new Params(null, mode, partIds, null);
			}
			case ASSIGN_PERFORMANCE -> {
				Long performanceId = ApiIds.positive(requiredText(node, "performanceId"), "performanceId");
				performances.findByIdAndClub_Id(performanceId, clubId)
						.orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND, "공연을 찾을 수 없습니다."));
				yield new Params(null, null, List.of(), performanceId);
			}
		};
	}

	/** 분리 트랜잭션에서 소속 한 건에 일괄 작업을 적용한다. */
	private Boolean applyOne(Long clubId, Long actorUserId, Long id,
			MembershipBulkRequest request, Params params) {
		// 개별 소속을 다시 조회해 다른 트랜잭션에서 바뀐 최신 상태를 검증한다.
		var member = memberships.require(clubId, id);
		if (member.getStatus() != MembershipStatus.ACTIVE
				&& member.getStatus() != MembershipStatus.ON_LEAVE
				&& member.getStatus() != MembershipStatus.GRADUATED) {
			throw new ApiException(ApiErrorCode.STATE_CONFLICT, "처리할 수 없는 소속 상태입니다.",
					"INVALID_MEMBERSHIP_STATUS");
		}
		// 상태 변경은 단건 서비스에 위임해 동일한 상태·감사 규칙을 재사용한다.
		if (request.action() == MembershipBulkRequest.Action.CHANGE_STATUS) {
			statusService.transition(clubId, id, actorUserId, params.status(), request.reason());
			return false;
		}
		// 파트 추가·삭제는 중복에 안전한 SQL로 적용하고 한 번의 감사 로그를 남긴다.
		if (request.action() == MembershipBulkRequest.Action.CHANGE_PARTS) {
			for (Long partId : params.partIds()) {
				if (params.mode().equals("ADD")) {
					Part current = parts.findByIdAndClub_Id(partId, clubId)
							.orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND, "파트를 찾을 수 없습니다."));
					if (!current.isActive()) {
						throw new ApiException(ApiErrorCode.RULE_VIOLATION,
								"사용 중지 파트는 추가할 수 없습니다.", "PART_INACTIVE");
					}
					entityManager.createNativeQuery("""
							INSERT IGNORE INTO membership_part (membership_id, part_id)
							VALUES (:membershipId, :partId)
							""").setParameter("membershipId", id).setParameter("partId", partId).executeUpdate();
				} else {
					entityManager.createNativeQuery("""
							DELETE FROM membership_part WHERE membership_id = :membershipId AND part_id = :partId
							""").setParameter("membershipId", id).setParameter("partId", partId).executeUpdate();
				}
			}
			entityManager.persist(AuditLog.record(clubId, actorUserId, "MEMBERSHIP_PARTS_CHANGED",
					"MEMBERSHIP", id, request.reason(), "{\"mode\":\"" + params.mode() + "\"}"));
			return false;
		}
		// 이미 유효 참여자인 경우는 성공한 건너뜀으로 표시하고, 아니면 관리자 참여를 생성한다.
		var existing = performanceMembers.findByPerformance_IdAndMembership_Id(params.performanceId(), id)
				.orElse(null);
		if (existing != null && (existing.getStatus() == PerformanceMemberStatus.APPLIED
				|| existing.getStatus() == PerformanceMemberStatus.APPROVED)) {
			return true;
		}
		var added = participantService.register(clubId, params.performanceId(), actorUserId,
				PerformanceMemberSource.ADMIN, id);
		entityManager.persist(AuditLog.record(clubId, actorUserId, "PERFORMANCE_MEMBER_ADDED",
				"PERFORMANCE_MEMBER", added.getId(), request.reason(), "{\"membershipId\":\"" + id + "\"}"));
		return false;
	}

	/** JSON 필드에서 비어 있지 않은 필수 문자열을 읽는다. */
	private static String requiredText(JsonNode node, String field) {
		JsonNode value = node.get(field);
		if (value == null || !value.isTextual() || value.asText().isBlank()) {
			throw new ApiException(ApiErrorCode.VALIDATION_ERROR, field + "가 필요합니다.");
		}
		return value.asText();
	}

	private record Params(MembershipStatus status, String mode, List<Long> partIds, Long performanceId) { }
}
