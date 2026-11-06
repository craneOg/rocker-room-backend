package com.crane.bandwebbackend.domain.participant.controller;

import com.crane.bandwebbackend.domain.participant.dto.ParticipantResponse;
import com.crane.bandwebbackend.domain.participant.dto.ParticipantDecisionRequest;
import com.crane.bandwebbackend.domain.participant.dto.ParticipantBulkRequest;
import com.crane.bandwebbackend.domain.participant.dto.ParticipantRemovalRequest;
import com.crane.bandwebbackend.domain.participant.dto.ParticipantImpactResponse;
import com.crane.bandwebbackend.domain.participant.dto.ParticipantMutationResult;
import com.crane.bandwebbackend.domain.participant.dto.ParticipantBulkDecisionRequest;
import com.crane.bandwebbackend.domain.participant.dto.ParticipantBulkDecisionResponse;
import com.crane.bandwebbackend.domain.participant.dto.ParticipantListResponse;
import com.crane.bandwebbackend.domain.participant.dto.MyParticipationResponse;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMember;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMemberSource;
import com.crane.bandwebbackend.domain.participant.service.PerformanceMemberService;
import com.crane.bandwebbackend.domain.participant.service.ParticipantDecisionService;
import com.crane.bandwebbackend.domain.participant.service.ParticipantBulkService;
import com.crane.bandwebbackend.domain.participant.service.ParticipantImpactService;
import com.crane.bandwebbackend.domain.participant.service.ParticipantRemovalService;
import com.crane.bandwebbackend.domain.participant.service.ParticipantBulkDecisionService;
import com.crane.bandwebbackend.domain.participant.service.ParticipantListReader;
import com.crane.bandwebbackend.domain.participant.service.MyParticipationReader;
import com.crane.bandwebbackend.global.response.ApiIds;
import com.crane.bandwebbackend.global.response.ApiResponse;
import com.crane.bandwebbackend.global.security.CurrentActor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

/**
 * PAR-02/03. 인증·CSRF·Idempotency-Key 저장소가 연결될 때까지 라우트를 등록하지 않는다.
 * 권한 판단은 서비스가 현재 소속 행을 다시 읽어 수행한다. 요청의 membershipId는 ADMIN 대상이며
 * 로그인 사용자 ID로 사용하지 않는다.
 */
@RestController
@RequestMapping("/api/v1/clubs/{clubId}/performances/{performanceId}")
@ConditionalOnProperty(prefix = "rocker.api-v1", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class PerformanceMemberController {
	private final PerformanceMemberService service;
	private final ParticipantDecisionService decisions;
	private final ParticipantBulkService bulk;
	private final ParticipantImpactService impact;
	private final ParticipantRemovalService removal;
	private final ParticipantBulkDecisionService bulkDecisions;
	private final ParticipantListReader listReader;
	private final MyParticipationReader myReader;
	private final CurrentActor currentActor;

	/** 공연 참여 본인 항목을 조회한다 */
	@GetMapping("/members/me")
	public ResponseEntity<ApiResponse<MyParticipationResponse>> mine(
		@PathVariable String clubId,
		@PathVariable String performanceId,
		HttpServletRequest request
	) {
		return ResponseEntity.ok(ApiResponse.ok(myReader.read(ApiIds.positive(clubId, "clubId"),
				ApiIds.positive(performanceId, "performanceId"), currentActor.requireUserId())));
	}

	/** 공연 참여 목록을 조회한다 */
	@GetMapping("/members")
	public ResponseEntity<ApiResponse<ParticipantListResponse>> list(
		@PathVariable String clubId,
		@PathVariable String performanceId,
		@RequestParam(required = false) String status,
		@RequestParam(required = false) String q,
		@RequestParam(required = false) String partId,
		@RequestParam(defaultValue = "0") int page,
		@RequestParam(defaultValue = "20") int size,
		HttpServletRequest request
	) {
		return ResponseEntity.ok(ApiResponse.ok(listReader.read(ApiIds.positive(clubId, "clubId"),
				ApiIds.positive(performanceId, "performanceId"), currentActor.requireUserId(),
				status, q, partId, page, size)));
	}

	/** 공연 참여 등록한다 */
	@PostMapping("/applications")
	public ResponseEntity<ApiResponse<ParticipantResponse>> register(
		@PathVariable String clubId,
		@PathVariable String performanceId,
		@RequestBody(required = false) Map<String, Object> body,
		@RequestHeader("X-Requested-With") String requestedWith,
		HttpServletRequest request
	) {
		if (body != null && !body.isEmpty()) {
			throw new com.crane.bandwebbackend.global.error.ApiException(
					com.crane.bandwebbackend.global.error.ApiErrorCode.VALIDATION_ERROR,
					"참여 신청 본문은 빈 객체여야 합니다.");
		}
		PerformanceMember member = service.register(ApiIds.positive(clubId, "clubId"),
				ApiIds.positive(performanceId, "performanceId"), currentActor.requireUserId(),
				PerformanceMemberSource.APPLICATION, null);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(ParticipantResponse.from(member)));
	}

	/** 공연 참여 임원 권한으로 추가한다 */
	@PostMapping("/members")
	public ResponseEntity<ApiResponse<Map<String, List<ParticipantResponse>>>> addByAdmin(
		@PathVariable String clubId,
		@PathVariable String performanceId,
		@Valid @RequestBody ParticipantBulkRequest body,
		@RequestHeader("X-Requested-With") String requestedWith,
		HttpServletRequest request
	) {
		var members = bulk.add(ApiIds.positive(clubId, "clubId"),
				ApiIds.positive(performanceId, "performanceId"), currentActor.requireUserId(),
				body.membershipIds(), body.reason());
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(Map.of("items", members.stream().map(ParticipantResponse::from).toList())));
	}

	/** 공연 참여 승인한다 */
	@PostMapping("/members/{performanceMemberId}/approve")
	public ResponseEntity<ApiResponse<ParticipantResponse>> approve(
		@PathVariable String clubId,
		@PathVariable String performanceId,
		@PathVariable String performanceMemberId,
		@Valid @RequestBody ParticipantDecisionRequest body,
		@RequestHeader("X-Requested-With") String requestedWith,
		HttpServletRequest request
	) {
		PerformanceMember member = decisions.approve(ApiIds.positive(clubId, "clubId"),
				ApiIds.positive(performanceId, "performanceId"),
				ApiIds.positive(performanceMemberId, "performanceMemberId"),
				currentActor.requireUserId(), body.performanceVersion(), body.reason());
		return ResponseEntity.ok(ApiResponse.ok(ParticipantResponse.from(member)));
	}

	/** 공연 참여 변경 영향을 조회한다 */
	@GetMapping("/members/{performanceMemberId}/impact")
	public ResponseEntity<ApiResponse<ParticipantImpactResponse>> impact(
		@PathVariable String clubId,
		@PathVariable String performanceId,
		@PathVariable String performanceMemberId,
		HttpServletRequest request
	) {
		return ResponseEntity.ok(ApiResponse.ok(impact.read(ApiIds.positive(clubId, "clubId"),
				ApiIds.positive(performanceId, "performanceId"),
				ApiIds.positive(performanceMemberId, "performanceMemberId"), currentActor.requireUserId())));
	}

	/** 공연 참여 거절한다 */
	@PostMapping("/members/{performanceMemberId}/reject")
	public ResponseEntity<ApiResponse<ParticipantMutationResult>> reject(
		@PathVariable String clubId,
		@PathVariable String performanceId,
		@PathVariable String performanceMemberId,
		@Valid @RequestBody ParticipantRemovalRequest body,
		@RequestHeader("X-Requested-With") String requestedWith,
		HttpServletRequest request
	) {
		return ResponseEntity.ok(remove(clubId, performanceId, performanceMemberId, body, true, request));
	}

	/** 공연 참여 철회하거나 탈퇴한다 */
	@PostMapping("/members/{performanceMemberId}/withdraw")
	public ResponseEntity<ApiResponse<ParticipantMutationResult>> withdraw(
		@PathVariable String clubId,
		@PathVariable String performanceId,
		@PathVariable String performanceMemberId,
		@RequestBody(required = false) ParticipantRemovalRequest body,
		@RequestHeader("X-Requested-With") String requestedWith,
		HttpServletRequest request
	) {
		return ResponseEntity.ok(remove(clubId, performanceId, performanceMemberId, body, false, request));
	}

	/** 참여 거절과 철회 요청을 공통 제거 서비스로 전달한다. */
	private ApiResponse<ParticipantMutationResult> remove(String clubId, String performanceId,
			String memberId, ParticipantRemovalRequest body, boolean reject, HttpServletRequest request) {
		return ApiResponse.ok(removal.remove(ApiIds.positive(clubId, "clubId"),
				ApiIds.positive(performanceId, "performanceId"), ApiIds.positive(memberId, "performanceMemberId"),
				currentActor.requireUserId(), body, reject));
	}

	/** 공연 참여 여러 신청을 일괄 처리한다 */
	@PostMapping("/members/bulk-decisions")
	public ResponseEntity<ApiResponse<ParticipantBulkDecisionResponse>> bulkDecide(
		@PathVariable String clubId,
		@PathVariable String performanceId,
		@Valid @RequestBody ParticipantBulkDecisionRequest body,
		@RequestHeader("X-Requested-With") String requestedWith,
		HttpServletRequest request
	) {
		return ResponseEntity.ok(ApiResponse.ok(bulkDecisions.decide(ApiIds.positive(clubId, "clubId"),
				ApiIds.positive(performanceId, "performanceId"), currentActor.requireUserId(), body)));
	}
}
