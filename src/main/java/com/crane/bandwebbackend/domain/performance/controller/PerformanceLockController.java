package com.crane.bandwebbackend.domain.performance.controller;

import com.crane.bandwebbackend.domain.performance.dto.LockChecklistResponse;
import com.crane.bandwebbackend.domain.performance.dto.LockConfirmRequest;
import com.crane.bandwebbackend.domain.performance.dto.LockMutationResponse;
import com.crane.bandwebbackend.domain.performance.dto.LockValidateRequest;
import com.crane.bandwebbackend.domain.performance.dto.LockValidationResponse;
import com.crane.bandwebbackend.domain.performance.service.PerformanceLockService;
import com.crane.bandwebbackend.global.response.ApiIds;
import com.crane.bandwebbackend.global.response.ApiResponse;
import com.crane.bandwebbackend.global.security.CurrentActor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 인증 필터와 API 스위치가 연결되면 API-050~052를 노출한다. */
@RestController
@RequestMapping("/api/v1/clubs/{clubId}/performances/{performanceId}")
@ConditionalOnProperty(prefix = "rocker.api-v1", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class PerformanceLockController {
    private final PerformanceLockService service;
    private final CurrentActor actor;

    /** 공연 입력과 상태를 검증한다 */
    @PostMapping("/lock/validate")
    public ResponseEntity<ApiResponse<LockValidationResponse>> validate(
            @PathVariable String clubId,
            @PathVariable String performanceId,
            @Valid @RequestBody LockValidateRequest body,
            HttpServletRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok(service.validate(id(clubId), id(performanceId), actor.requireUserId(), body.version())));
    }

    /** 공연 최종 확정한다 */
    @PostMapping("/lock")
    public ResponseEntity<ApiResponse<LockMutationResponse>> lock(
            @PathVariable String clubId,
            @PathVariable String performanceId,
            @Valid @RequestBody LockConfirmRequest body,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(service.lock(
                id(clubId),
                id(performanceId),
                actor.requireUserId(),
                body.version(),
                Boolean.TRUE.equals(body.confirmed()))));
    }

    /** 공연 확정 점검 항목을 조회한다 */
    @GetMapping("/warnings")
    public ResponseEntity<ApiResponse<LockChecklistResponse>> checklist(
            @PathVariable String clubId, @PathVariable String performanceId, HttpServletRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok(service.checklist(id(clubId), id(performanceId), actor.requireUserId())));
    }

    /** 경로 식별자를 양의 정수로 검증해 변환한다. */
    private static Long id(String raw) {
        return ApiIds.positive(raw, "id");
    }
}
