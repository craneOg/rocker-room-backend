package com.crane.bandwebbackend.domain.audition.controller;

import com.crane.bandwebbackend.domain.audition.dto.AuditionCreateRequest;
import com.crane.bandwebbackend.domain.audition.dto.AuditionCreateResponse;
import com.crane.bandwebbackend.domain.audition.dto.AuditionListResponse;
import com.crane.bandwebbackend.domain.audition.dto.AuditionReviewRequest;
import com.crane.bandwebbackend.domain.audition.dto.AuditionReviewResponse;
import com.crane.bandwebbackend.domain.audition.service.AuditionService;
import com.crane.bandwebbackend.global.response.ApiIds;
import com.crane.bandwebbackend.global.response.ApiResponse;
import com.crane.bandwebbackend.global.security.CurrentActor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API-147~149: 오디션 목록·등록·심사. version 검사·감사·LOCKED 예외 처리는 서비스에 맡긴다. */
@RestController
@RequestMapping("/api/v1/clubs/{clubId}/performances/{performanceId}/auditions")
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
            @RequestParam(required = false) String auditionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        AuditionListResponse result = service.pageAdmin(
                ApiIds.positive(clubId, "clubId"),
                ApiIds.positive(performanceId, "performanceId"),
                currentActor.requireUserId(),
                tab,
                type,
                songId == null ? null : ApiIds.positive(songId, "songId"),
                scheduleId == null ? null : ApiIds.positive(scheduleId, "scheduleId"),
                auditionId == null ? null : ApiIds.positive(auditionId, "auditionId"),
                page,
                size);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /** 오디션 생성한다 */
    @PostMapping
    public ResponseEntity<ApiResponse<AuditionCreateResponse>> create(
            @PathVariable String clubId,
            @PathVariable String performanceId,
            @Valid @RequestBody AuditionCreateRequest body,
            HttpServletRequest request) {
        var items = service.create(
                ApiIds.positive(clubId, "clubId"),
                ApiIds.positive(performanceId, "performanceId"),
                currentActor.requireUserId(),
                body);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(
                        AuditionCreateResponse.builder().items(items).build()));
    }

    /** 오디션 심사 결과를 저장한다 */
    @PutMapping("/{auditionId}/result")
    public ResponseEntity<ApiResponse<AuditionReviewResponse>> result(
            @PathVariable String clubId,
            @PathVariable String performanceId,
            @PathVariable String auditionId,
            @Valid @RequestBody AuditionReviewRequest body,
            HttpServletRequest request) {
        var result = service.review(
                ApiIds.positive(clubId, "clubId"),
                ApiIds.positive(performanceId, "performanceId"),
                ApiIds.positive(auditionId, "auditionId"),
                currentActor.requireUserId(),
                body);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
