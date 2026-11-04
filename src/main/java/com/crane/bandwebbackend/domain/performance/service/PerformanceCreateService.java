package com.crane.bandwebbackend.domain.performance.service;

import com.crane.bandwebbackend.domain.club.entity.ClubStatus;
import com.crane.bandwebbackend.domain.membership.service.MembershipService;
import com.crane.bandwebbackend.domain.performance.dto.PerformanceCreateRequest;
import com.crane.bandwebbackend.domain.performance.dto.PerformanceResponse;
import com.crane.bandwebbackend.domain.performance.entity.Performance;
import com.crane.bandwebbackend.domain.performance.entity.PerformancePolicy;
import com.crane.bandwebbackend.domain.performance.entity.PerformancePreset;
import com.crane.bandwebbackend.domain.performance.entity.VoteMethod;
import com.crane.bandwebbackend.domain.performance.repository.PerformancePolicyRepository;
import com.crane.bandwebbackend.domain.performance.repository.PerformanceRepository;
import com.crane.bandwebbackend.global.audit.AuditLog;
import com.crane.bandwebbackend.global.error.ApiErrorCode;
import com.crane.bandwebbackend.global.error.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** API-038: 공연과 정책을 한 트랜잭션에서 만든다. */
@Service
@Transactional
@RequiredArgsConstructor
public class PerformanceCreateService {
	private final MembershipService memberships;
	private final PerformanceRepository performances;
	private final PerformancePolicyRepository policies;
	private final PerformancePolicyEditService policyEdit;
	private final BandPerformanceInitializationService bandInitializer;
	private final EntityManager entityManager;

	/** 공연 생성한다 */
	@Transactional
	public PerformanceResponse create(
		Long clubId,
		Long actorUserId,
		PerformanceCreateRequest request
	) {
		// 관리자 권한과 동아리 활성 상태를 먼저 확인한다.
		var actor = memberships.requireAdmin(clubId, actorUserId);
		if (actor.getClub().getStatus() != ClubStatus.ACTIVE) {
			throw new ApiException(ApiErrorCode.STATE_CONFLICT, "비활성 동아리에서는 공연을 만들 수 없습니다.",
					"CLUB_INACTIVE");
		}
		if (request == null || request.title() == null || request.title().isBlank()
				|| request.title().length() > 200 || (request.location() != null
				&& request.location().length() > 200)) {
			throw new ApiException(ApiErrorCode.VALIDATION_ERROR, "공연 기본 정보가 유효하지 않습니다.");
		}
		// 프리셋과 선택 정책 덮어쓰기의 구조 및 CUSTOM 필수 필드를 검증한다.
		PerformancePreset preset = request.preset() == null ? PerformancePreset.CUSTOM : request.preset();
		JsonNode overrides = request.policy();
		if (overrides != null && !overrides.isObject()) {
			throw new ApiException(ApiErrorCode.VALIDATION_ERROR, "policy는 객체여야 합니다.");
		}
		if (preset == PerformancePreset.CUSTOM && (overrides == null
				|| !overrides.has("applicationState") || !overrides.has("teamApplicationState")
				|| !overrides.has("candidateState") || !overrides.has("playabilityState")
				|| !overrides.has("voteState") || !overrides.has("availabilityState")
				|| !overrides.has("candidateScope"))) {
			throw new ApiException(ApiErrorCode.VALIDATION_ERROR, "CUSTOM 정책의 시작 상태가 필요합니다.");
		}
		// 공연 엔티티를 생성하고 ID가 필요한 정책보다 먼저 저장한다.
		Performance performance;
		try {
			performance = Performance.draft(actor.getClub(), request.title(), request.performDate(),
					request.location(), preset, actor.getUser());
		} catch (IllegalArgumentException exception) {
			throw new ApiException(ApiErrorCode.VALIDATION_ERROR, exception.getMessage());
		}
		performances.saveAndFlush(performance);
		// 프리셋 기본 정책을 만든 뒤 요청된 투표 방식과 세부 정책을 덮어쓴다.
		PerformancePolicy policy = PerformancePolicy.initial(performance, preset);
		if (overrides != null && overrides.has("voteMethod")) {
			try {
				policy.changeVoteMethod(VoteMethod.valueOf(overrides.path("voteMethod").asText()));
			} catch (IllegalArgumentException exception) {
				throw new ApiException(ApiErrorCode.VALIDATION_ERROR, "voteMethod가 유효하지 않습니다.");
			}
		}
		policies.saveAndFlush(policy);
		if (overrides != null && !overrides.isEmpty()) {
			ObjectNode patch = ((ObjectNode) overrides).deepCopy();
			patch.remove("voteMethod");
			patch.put("version", performance.getVersion().toString());
			policyEdit.update(clubId, performance.getId(), actorUserId, patch);
		}
		// BAND 프리셋은 활성 정회원과 기본 팀을 같은 트랜잭션에서 자동 구성한다.
		if (preset == PerformancePreset.BAND) {
			bandInitializer.initialize(performance, actorUserId);
		}
		// 생성 이벤트를 기록하고 모든 제약조건을 즉시 확인한 뒤 응답으로 변환한다.
		entityManager.persist(AuditLog.record(clubId, actorUserId, "PERFORMANCE_CREATED",
				"PERFORMANCE", performance.getId(), null, "{\"preset\":\"" + preset + "\"}"));
		entityManager.flush();
		return PerformanceResponse.from(performance);
	}
}
