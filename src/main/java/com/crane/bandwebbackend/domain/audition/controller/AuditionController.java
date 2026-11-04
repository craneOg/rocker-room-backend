package com.crane.bandwebbackend.domain.audition.controller;

import com.crane.bandwebbackend.domain.audition.dto.AuditionListResponse;
import com.crane.bandwebbackend.domain.audition.dto.AuditionReviewRequest;
import com.crane.bandwebbackend.domain.audition.dto.AuditionReviewResponse;
import com.crane.bandwebbackend.domain.audition.dto.AuditionCreateRequest;
import com.crane.bandwebbackend.domain.audition.dto.AuditionCreateResponse;
import com.crane.bandwebbackend.domain.audition.dto.AuditionCreatedItem;
import com.crane.bandwebbackend.domain.audition.service.AuditionService;
import com.crane.bandwebbackend.global.response.ApiIds;
import com.crane.bandwebbackend.global.response.ApiResponse;
import com.crane.bandwebbackend.global.response.RequestIdFilter;
import com.crane.bandwebbackend.global.security.CurrentActor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AUD-03 상세 조회. 결과 등록과 정정은 revision 검사·감사 로그·LOCKED 예외를 묶어야 하므로
 * 해당 서비스와 인증 필터가 완성된 뒤 라우트를 등록한다.
 */
@RestController
@RequestMapping("/api/v1/clubs/{clubId}/performances/{performanceId}/auditions")
@ConditionalOnProperty(prefix = "rocker.api-v1", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class AuditionController {
	private final AuditionService service;
	private final CurrentActor currentActor;

	/** 오디션 목록을 조회한다 */
	@GetMapping
	public ResponseEntity<ApiResponse<AuditionListResponse>> list(
		@PathVariable String clubId,
		@PathVariable String performanceId,
		@RequestParam(required = false) String tab,
		@RequestParam(required = false) String type,
		@RequestParam(required = false) String songId,
		@RequestParam(required = false) String scheduleId,
		@RequestParam(defaultValue = "0") int page,
		@RequestParam(defaultValue = "20") int size,
		HttpServletRequest request
	) {
		AuditionListResponse result = service.pageAdmin(ApiIds.positive(clubId, "clubId"),
				ApiIds.positive(performanceId, "performanceId"), currentActor.requireUserId(),
				tab, type, songId == null ? null : ApiIds.positive(songId, "songId"),
				scheduleId == null ? null : ApiIds.positive(scheduleId, "scheduleId"), page, size);
		return ResponseEntity.ok(ApiResponse.of(result, RequestIdFilter.require(request)));
	}

	/** 오디션 생성한다 */
	@PostMapping
	public ResponseEntity<ApiResponse<AuditionCreateResponse>> create(
		@PathVariable String clubId,
		@PathVariable String performanceId,
		@Valid @RequestBody AuditionCreateRequest body,
		@RequestHeader("X-Requested-With") String requestedWith,
		HttpServletRequest request
	) {
		var entries = service.create(ApiIds.positive(clubId, "clubId"),
				ApiIds.positive(performanceId, "performanceId"), currentActor.requireUserId(), body);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(AuditionCreateResponse.builder()
				.items(entries.stream().map(AuditionCreatedItem::from).toList()).build(),
				RequestIdFilter.require(request)));
	}

	/** 오디션 심사 결과를 저장한다 */
	@PutMapping("/{auditionId}/result")
	public ResponseEntity<ApiResponse<AuditionReviewResponse>> result(
		@PathVariable String clubId,
		@PathVariable String performanceId,
		@PathVariable String auditionId,
		@Valid @RequestBody AuditionReviewRequest body,
		@RequestHeader("X-Requested-With") String requestedWith,
		HttpServletRequest request
	) {
		var entry = service.review(ApiIds.positive(clubId, "clubId"),
				ApiIds.positive(performanceId, "performanceId"), ApiIds.positive(auditionId, "auditionId"),
				currentActor.requireUserId(), body);
		return ResponseEntity.ok(ApiResponse.of(AuditionReviewResponse.from(entry), RequestIdFilter.require(request)));
	}
}
