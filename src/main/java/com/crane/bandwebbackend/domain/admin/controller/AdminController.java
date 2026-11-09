package com.crane.bandwebbackend.domain.admin.controller;

import com.crane.bandwebbackend.domain.admin.dto.AdminClubDetailResponse;
import com.crane.bandwebbackend.domain.admin.dto.AdminClubResponse;
import com.crane.bandwebbackend.domain.admin.dto.AdminOverviewResponse;
import com.crane.bandwebbackend.domain.admin.dto.AdminUserResponse;
import com.crane.bandwebbackend.domain.admin.dto.AuditLogResponse;
import com.crane.bandwebbackend.domain.admin.dto.ClubStatusChangeRequest;
import com.crane.bandwebbackend.domain.admin.dto.ForceLogoutRequest;
import com.crane.bandwebbackend.domain.admin.dto.ForceLogoutResponse;
import com.crane.bandwebbackend.domain.admin.service.AdminAuditLogService;
import com.crane.bandwebbackend.domain.admin.service.AdminClubService;
import com.crane.bandwebbackend.domain.admin.service.AdminOverviewService;
import com.crane.bandwebbackend.domain.admin.service.AdminUserService;
import com.crane.bandwebbackend.global.error.BusinessException;
import com.crane.bandwebbackend.global.error.ErrorCode;
import com.crane.bandwebbackend.global.error.ErrorDetail;
import com.crane.bandwebbackend.global.response.PageResponse;
import com.crane.bandwebbackend.global.security.AuthPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 서비스 운영(백오피스) API. /api/v1/admin/** 은 SecurityConfig에서 Super Admin만 통과한다
@Validated
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminUserService adminUserService;
    private final AdminAuditLogService adminAuditLogService;
    private final AdminClubService adminClubService;
    private final AdminOverviewService adminOverviewService;

    // API 118: 전체 동아리. status·clubType은 쉼표로 여러 값, sort는 "키,asc|desc"
    @GetMapping("/clubs")
    public PageResponse<AdminClubResponse> clubs(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String clubType,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "false") boolean noAdmin,
            @RequestParam(defaultValue = "false") boolean inactiveOver180Days,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return adminClubService.search(status, clubType, q, noAdmin, inactiveOver180Days, sort, page, size);
    }

    // API 119: 조회하면 CLUB_DETAIL_VIEWED 감사 기록이 남는다
    @GetMapping("/clubs/{clubId}")
    public AdminClubDetailResponse clubDetail(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable @Pattern(regexp = "^[1-9][0-9]*$") String clubId) {
        return adminClubService.detail(principal.userId(), AdminUserService.parseId(clubId, "clubId"));
    }

    // API 120: 비활성(INACTIVE)으로 전환. 사유 필수
    @PostMapping(value = "/clubs/{clubId}/deactivate", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AdminClubDetailResponse.ClubInfo deactivateClub(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable @Pattern(regexp = "^[1-9][0-9]*$") String clubId,
            @Valid @RequestBody ClubStatusChangeRequest request) {
        return adminClubService.deactivate(
                principal.userId(), AdminUserService.parseId(clubId, "clubId"), request.getReason());
    }

    // API 159: 활성(ACTIVE)으로 되돌림. 사유 필수
    @PostMapping(value = "/clubs/{clubId}/reactivate", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AdminClubDetailResponse.ClubInfo reactivateClub(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable @Pattern(regexp = "^[1-9][0-9]*$") String clubId,
            @Valid @RequestBody ClubStatusChangeRequest request) {
        return adminClubService.reactivate(
                principal.userId(),
                AdminUserService.parseId(clubId, "clubId"),
                request.getReason(),
                request.isConfirmed());
    }

    // API 141: periodDays는 7, 30, 90 중 하나(기본 30)
    @GetMapping("/overview")
    public AdminOverviewResponse overview(@RequestParam(defaultValue = "30") @Min(7) @Max(90) int periodDays) {
        if (periodDays != 7 && periodDays != 30 && periodDays != 90) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "요청 값이 올바르지 않습니다.",
                    List.of(ErrorDetail.of("periodDays", "INVALID_VALUE")));
        }
        return adminOverviewService.overview(periodDays);
    }

    // API 122: userId 또는 handle(정확 일치)로만 조회한다
    @GetMapping("/users")
    public PageResponse<AdminUserResponse> users(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String handle,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return adminUserService.search(userId, handle, page, size);
    }

    // API 123
    @PostMapping(value = "/users/{userId}/force-logout", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ForceLogoutResponse forceLogout(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable @Pattern(regexp = "^[1-9][0-9]*$") String userId,
            @Valid @RequestBody ForceLogoutRequest request) {
        return adminUserService.forceLogout(
                principal.userId(), AdminUserService.parseId(userId, "userId"), request.getReason());
    }

    // API 125: 최근 기록부터. action은 쉼표로 여러 값을 보낼 수 있다
    @GetMapping("/audit-logs")
    public PageResponse<AuditLogResponse> auditLogs(
            @RequestParam(required = false) String clubId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String targetId,
            @RequestParam(required = false) String actorUserId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return adminAuditLogService.search(clubId, action, targetType, targetId, actorUserId, from, to, page, size);
    }
}
