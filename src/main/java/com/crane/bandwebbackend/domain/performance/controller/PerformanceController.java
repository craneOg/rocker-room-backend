package com.crane.bandwebbackend.domain.performance.controller;

import com.crane.bandwebbackend.domain.performance.dto.PerformanceCreateRequest;
import com.crane.bandwebbackend.domain.performance.dto.PerformanceDetailResponse;
import com.crane.bandwebbackend.domain.performance.dto.PerformanceListItem;
import com.crane.bandwebbackend.domain.performance.dto.PerformancePolicyResponse;
import com.crane.bandwebbackend.domain.performance.dto.PerformanceResponse;
import com.crane.bandwebbackend.domain.performance.dto.PerformanceUpdateResponse;
import com.crane.bandwebbackend.domain.performance.dto.PolicyUpdateResponse;
import com.crane.bandwebbackend.domain.performance.dto.VoteMethodChangeRequest;
import com.crane.bandwebbackend.domain.performance.entity.PerformancePreset;
import com.crane.bandwebbackend.domain.performance.service.PerformanceBasicEditService;
import com.crane.bandwebbackend.domain.performance.service.PerformanceCreateService;
import com.crane.bandwebbackend.domain.performance.service.PerformanceDetailReader;
import com.crane.bandwebbackend.domain.performance.service.PerformanceListReader;
import com.crane.bandwebbackend.domain.performance.service.PerformancePolicyEditService;
import com.crane.bandwebbackend.domain.performance.service.PerformanceService;
import com.crane.bandwebbackend.domain.performance.service.VoteMethodChangeService;
import com.crane.bandwebbackend.global.error.BusinessException;
import com.crane.bandwebbackend.global.error.ErrorCode;
import com.crane.bandwebbackend.global.response.ApiIds;
import com.crane.bandwebbackend.global.response.ApiResponse;
import com.crane.bandwebbackend.global.response.PageResponse;
import com.crane.bandwebbackend.global.security.CurrentActor;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * PERF-01 목록. 생성·상세·정책 수정은 프리셋 기본값(D-01 등), 정책 공개 범위 및
 * 곡/팀 집계와 인증 필터가 준비된 뒤 API 스위치로 등록한다.
 */
@RestController
@RequestMapping("/api/v1/clubs/{clubId}/performances")
@ConditionalOnProperty(prefix = "rocker.api-v1", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class PerformanceController {
    private final PerformanceService service;
    private final PerformanceDetailReader detailReader;
    private final PerformanceListReader listReader;
    private final PerformanceBasicEditService editService;
    private final PerformancePolicyEditService policyEditService;
    private final VoteMethodChangeService voteMethodChangeService;
    private final PerformanceCreateService createService;
    private final CurrentActor currentActor;

    /** 공연 목록을 조회한다 */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<PerformanceListItem>>> list(
            @PathVariable String clubId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "performDate,asc") String sort,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) PerformancePreset preset,
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate,
            HttpServletRequest request) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "page 또는 size 범위가 유효하지 않습니다.");
        }
        Page<PerformanceListItem> result = listReader.page(
                ApiIds.positive(clubId, "clubId"),
                currentActor.requireUserId(),
                status,
                preset,
                fromDate,
                toDate,
                sort,
                PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(result)));
    }

    /** 공연 생성한다 */
    @PostMapping
    public ResponseEntity<ApiResponse<PerformanceResponse>> create(
            @PathVariable String clubId,
            @Valid @RequestBody PerformanceCreateRequest body,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(
                        createService.create(ApiIds.positive(clubId, "clubId"), currentActor.requireUserId(), body)));
    }

    /** 공연 상세 정보를 조회한다 */
    @GetMapping("/{performanceId}")
    public ResponseEntity<ApiResponse<PerformanceDetailResponse>> detail(
            @PathVariable String clubId, @PathVariable String performanceId, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(detailReader.read(
                ApiIds.positive(clubId, "clubId"),
                ApiIds.positive(performanceId, "performanceId"),
                currentActor.requireUserId())));
    }

    /** 공연 운영 정책을 조회한다 */
    @GetMapping("/{performanceId}/policy")
    public ResponseEntity<ApiResponse<PerformancePolicyResponse>> policy(
            @PathVariable String clubId, @PathVariable String performanceId, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(PerformancePolicyResponse.from(service.policy(
                ApiIds.positive(clubId, "clubId"),
                ApiIds.positive(performanceId, "performanceId"),
                currentActor.requireUserId()))));
    }

    /** 공연 수정한다 */
    @PatchMapping("/{performanceId}")
    public ResponseEntity<ApiResponse<PerformanceUpdateResponse>> update(
            @PathVariable String clubId,
            @PathVariable String performanceId,
            @RequestBody JsonNode body,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(editService.update(
                ApiIds.positive(clubId, "clubId"),
                ApiIds.positive(performanceId, "performanceId"),
                currentActor.requireUserId(),
                body)));
    }

    /** 공연 운영 정책을 수정한다 */
    @PatchMapping("/{performanceId}/policy")
    public ResponseEntity<ApiResponse<PolicyUpdateResponse>> updatePolicy(
            @PathVariable String clubId,
            @PathVariable String performanceId,
            @RequestBody JsonNode body,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(policyEditService.update(
                ApiIds.positive(clubId, "clubId"),
                ApiIds.positive(performanceId, "performanceId"),
                currentActor.requireUserId(),
                body)));
    }

    /** 공연 투표 방식 변경을 검증한다 */
    @PostMapping("/{performanceId}/policy/vote-method/validate")
    public ResponseEntity<ApiResponse<VoteMethodChangeService.Validation>> validateVoteMethod(
            @PathVariable String clubId,
            @PathVariable String performanceId,
            @Valid @RequestBody VoteMethodChangeRequest body,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(voteMethodChangeService.validate(
                ApiIds.positive(clubId, "clubId"),
                ApiIds.positive(performanceId, "performanceId"),
                currentActor.requireUserId(),
                body)));
    }

    /** 공연 투표 방식을 변경한다 */
    @PutMapping("/{performanceId}/policy/vote-method")
    public ResponseEntity<ApiResponse<VoteMethodChangeService.ChangeResult>> changeVoteMethod(
            @PathVariable String clubId,
            @PathVariable String performanceId,
            @Valid @RequestBody VoteMethodChangeRequest body,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(voteMethodChangeService.change(
                ApiIds.positive(clubId, "clubId"),
                ApiIds.positive(performanceId, "performanceId"),
                currentActor.requireUserId(),
                body)));
    }
}
