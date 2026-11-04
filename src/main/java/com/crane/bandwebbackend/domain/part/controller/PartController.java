package com.crane.bandwebbackend.domain.part.controller;

import com.crane.bandwebbackend.domain.part.dto.PartCreateRequest;
import com.crane.bandwebbackend.domain.part.dto.PartListResponse;
import com.crane.bandwebbackend.domain.part.dto.PartResponse;
import com.crane.bandwebbackend.domain.part.dto.PartStatusRequest;
import com.crane.bandwebbackend.domain.part.dto.PartUpdateRequest;
import com.crane.bandwebbackend.domain.part.service.PartService;
import com.crane.bandwebbackend.global.response.ApiIds;
import com.crane.bandwebbackend.global.response.ApiResponse;
import com.crane.bandwebbackend.global.response.RequestIdFilter;
import com.crane.bandwebbackend.global.security.CurrentActor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * PART-01~04. 사용자 ID는 인증 연결 지점에서만 받는다. 관리자 권한은 요청 시 DB 소속으로 확인한다.
 * 변경 API를 켜기 전에 인증·CSRF와 POST 멱등 재시도 처리를 연결해야 한다.
 */
@RestController
@RequestMapping("/api/v2/clubs/{clubId}/parts")
@ConditionalOnProperty(prefix = "rocker.api-v2", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class PartController {
    private final PartService service;
    private final CurrentActor currentActor;

    /** 동아리 파트 목록을 활성 상태 필터와 함께 조회한다. */
    @GetMapping
    public ApiResponse<PartListResponse> list(@PathVariable String clubId,
            @RequestParam(defaultValue = "true") boolean includeInactive, HttpServletRequest request) {
        var items = service.list(ApiIds.positive(clubId, "clubId"), currentActor.requireUserId(), includeInactive)
                .stream().map(PartResponse::from).toList();
        return ApiResponse.of(new PartListResponse(items), RequestIdFilter.require(request));
    }

    /** 동아리에 새 파트를 생성한다. */
    @PostMapping
    public ResponseEntity<ApiResponse<PartResponse>> create(@PathVariable String clubId,
            @Valid @RequestBody PartCreateRequest body,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @RequestHeader("X-Requested-With") String requestedWith,
            HttpServletRequest request) {
        PartResponse result = PartResponse.from(service.create(ApiIds.positive(clubId, "clubId"),
                currentActor.requireUserId(), body.getName(), body.getDisplayOrder()));
        return ResponseEntity.status(HttpStatus.CREATED).eTag(result.getRevision())
                .body(ApiResponse.of(result, RequestIdFilter.require(request)));
    }

    /** 파트 이름과 표시 순서를 수정한다. */
    @PatchMapping("/{partId}")
    public ResponseEntity<ApiResponse<PartResponse>> update(@PathVariable String clubId,
            @PathVariable String partId, @Valid @RequestBody PartUpdateRequest body,
            @RequestHeader("X-Requested-With") String requestedWith, HttpServletRequest request) {
        PartResponse result = PartResponse.from(service.update(ApiIds.positive(clubId, "clubId"),
                ApiIds.positive(partId, "partId"), currentActor.requireUserId(),
                body.getExpectedRevision(), body.getName(), body.getDisplayOrder()));
        return ResponseEntity.ok().eTag(result.getRevision())
                .body(ApiResponse.of(result, RequestIdFilter.require(request)));
    }

    /** 파트의 활성 여부를 변경한다. */
    @PutMapping("/{partId}/status")
    public ResponseEntity<ApiResponse<PartResponse>> setStatus(@PathVariable String clubId,
            @PathVariable String partId, @Valid @RequestBody PartStatusRequest body,
            @RequestHeader("X-Requested-With") String requestedWith, HttpServletRequest request) {
        PartResponse result = PartResponse.from(service.setActive(ApiIds.positive(clubId, "clubId"),
                ApiIds.positive(partId, "partId"), currentActor.requireUserId(),
                body.getExpectedRevision(), body.getIsActive()));
        return ResponseEntity.ok().eTag(result.getRevision())
                .body(ApiResponse.of(result, RequestIdFilter.require(request)));
    }
}
