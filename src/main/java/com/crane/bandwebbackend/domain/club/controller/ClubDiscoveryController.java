package com.crane.bandwebbackend.domain.club.controller;

import com.crane.bandwebbackend.domain.club.dto.InvitationPreviewRequest;
import com.crane.bandwebbackend.domain.club.dto.JoinCodeResolveRequest;
import com.crane.bandwebbackend.domain.club.dto.JoinPreviewResponse;
import com.crane.bandwebbackend.domain.club.dto.NameAvailabilityResponse;
import com.crane.bandwebbackend.domain.club.service.ClubDiscoveryService;
import com.crane.bandwebbackend.domain.club.service.InvitationPreviewRateLimiter;
import com.crane.bandwebbackend.global.error.ErrorCode;
import com.crane.bandwebbackend.global.error.ErrorDetail;
import com.crane.bandwebbackend.global.error.ErrorResponse;
import com.crane.bandwebbackend.global.response.PageResponse;
import com.crane.bandwebbackend.global.security.AuthPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ClubDiscoveryController {

    private final ClubDiscoveryService discoveryService;
    private final InvitationPreviewRateLimiter rateLimiter;

    // API 12: 정렬은 이름 오름차순 고정이다
    @GetMapping("/clubs/search")
    public PageResponse<JoinPreviewResponse> search(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return discoveryService.search(principal.userId(), q, page, size);
    }

    // API 13
    @PostMapping(value = "/clubs/join-code/resolve", consumes = MediaType.APPLICATION_JSON_VALUE)
    public JoinPreviewResponse resolve(
            @AuthenticationPrincipal AuthPrincipal principal, @RequestBody JoinCodeResolveRequest request) {
        return discoveryService.resolve(principal.userId(), request.getJoinCode());
    }

    // API 126
    @GetMapping("/clubs/name-availability")
    public NameAvailabilityResponse nameAvailability(
            @RequestParam String name, @RequestParam(required = false) String excludeClubId) {
        return discoveryService.nameAvailability(name, excludeClubId);
    }

    // API 127: 비인증. 같은 IP의 요청이 많으면 429(code RULE_VIOLATION, reason RATE_LIMITED, Retry-After 초)
    @PostMapping(value = "/invitations/preview", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> invitationPreview(
            @RequestBody InvitationPreviewRequest request, HttpServletRequest httpRequest) {
        long retryAfterSeconds = rateLimiter.tryAcquire(httpRequest.getRemoteAddr());
        if (retryAfterSeconds > 0) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds))
                    .body(ErrorResponse.of(
                            ErrorCode.RULE_VIOLATION,
                            "요청이 너무 많습니다.",
                            List.of(ErrorDetail.of("request", "RATE_LIMITED")),
                            List.of()));
        }
        return ResponseEntity.ok(discoveryService.invitationPreview(request.getCode()));
    }
}
