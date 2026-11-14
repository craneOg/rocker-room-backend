package com.crane.bandwebbackend.domain.performance.controller;

import com.crane.bandwebbackend.domain.performance.dto.PerformanceResponse;
import com.crane.bandwebbackend.domain.performance.dto.PerformanceTransitionRequest;
import com.crane.bandwebbackend.domain.performance.entity.PerformanceStatus;
import com.crane.bandwebbackend.domain.performance.service.PerformanceTransitionService;
import com.crane.bandwebbackend.global.response.ApiIds;
import com.crane.bandwebbackend.global.response.ApiResponse;
import com.crane.bandwebbackend.global.security.CurrentActor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API-045~049, 152. 인증과 CSRF 필터가 제공될 때 api-v1 스위치를 켠다. */
@RestController
@RequestMapping("/api/v1/clubs/{clubId}/performances/{performanceId}")
@ConditionalOnProperty(prefix = "rocker.api-v1", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class PerformanceStatusController {
    private final PerformanceTransitionService service;
    private final CurrentActor actor;

    /** 공연 활성화한다 */
    @PostMapping("/activate")
    public ResponseEntity<ApiResponse<PerformanceResponse>> activate(
            @PathVariable String clubId,
            @PathVariable String performanceId,
            @Valid @RequestBody PerformanceTransitionRequest body,
            HttpServletRequest request) {
        return ResponseEntity.ok(
                change(clubId, performanceId, body, PerformanceStatus.DRAFT, PerformanceStatus.ACTIVE, request));
    }

    /** 공연 다시 연다 */
    @PostMapping("/reopen")
    public ResponseEntity<ApiResponse<PerformanceResponse>> reopen(
            @PathVariable String clubId,
            @PathVariable String performanceId,
            @Valid @RequestBody PerformanceTransitionRequest body,
            HttpServletRequest request) {
        return ResponseEntity.ok(
                change(clubId, performanceId, body, PerformanceStatus.LOCKED, PerformanceStatus.ACTIVE, request));
    }

    /** 공연 종료한다 */
    @PostMapping("/finish")
    public ResponseEntity<ApiResponse<PerformanceResponse>> finish(
            @PathVariable String clubId,
            @PathVariable String performanceId,
            @Valid @RequestBody PerformanceTransitionRequest body,
            HttpServletRequest request) {
        return ResponseEntity.ok(
                change(clubId, performanceId, body, PerformanceStatus.LOCKED, PerformanceStatus.FINISHED, request));
    }

    /** 공연 취소한다 */
    @PostMapping("/cancel")
    public ResponseEntity<ApiResponse<PerformanceResponse>> cancel(
            @PathVariable String clubId,
            @PathVariable String performanceId,
            @Valid @RequestBody PerformanceTransitionRequest body,
            HttpServletRequest request) {
        return ResponseEntity.ok(change(clubId, performanceId, body, null, PerformanceStatus.CANCELED, request));
    }

    /** 공연 복구한다 */
    @PostMapping("/restore")
    public ResponseEntity<ApiResponse<PerformanceResponse>> restore(
            @PathVariable String clubId,
            @PathVariable String performanceId,
            @Valid @RequestBody PerformanceTransitionRequest body,
            HttpServletRequest request) {
        return ResponseEntity.ok(
                change(clubId, performanceId, body, PerformanceStatus.CANCELED, PerformanceStatus.DRAFT, request));
    }

    /** 공연 확정 상태로 되돌린다 */
    @PostMapping("/revert-to-locked")
    public ResponseEntity<ApiResponse<PerformanceResponse>> revertToLocked(
            @PathVariable String clubId,
            @PathVariable String performanceId,
            @Valid @RequestBody PerformanceTransitionRequest body,
            HttpServletRequest request) {
        return ResponseEntity.ok(
                change(clubId, performanceId, body, PerformanceStatus.FINISHED, PerformanceStatus.LOCKED, request));
    }

    /** 공연 준비 상태로 되돌린다 */
    @PostMapping("/revert-to-draft")
    public ResponseEntity<ApiResponse<PerformanceResponse>> revertToDraft(
            @PathVariable String clubId,
            @PathVariable String performanceId,
            @Valid @RequestBody PerformanceTransitionRequest body,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(PerformanceResponse.from(service.revertToDraft(
                id(clubId), id(performanceId), actor.requireUserId(), body.getVersion(), body.getReason()))));
    }

    /** 공연 상태 변경 엔드포인트의 공통 호출과 응답 생성을 처리한다. */
    private ApiResponse<PerformanceResponse> change(
            String clubId,
            String performanceId,
            PerformanceTransitionRequest body,
            PerformanceStatus source,
            PerformanceStatus target,
            HttpServletRequest request) {
        return ApiResponse.ok(PerformanceResponse.from(service.change(
                id(clubId),
                id(performanceId),
                actor.requireUserId(),
                source,
                target,
                body.getVersion(),
                body.getReason())));
    }

    /** 경로 식별자를 양의 정수로 검증해 변환한다. */
    private Long id(String raw) {
        return ApiIds.positive(raw, "id");
    }
}
