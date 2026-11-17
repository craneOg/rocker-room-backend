package com.crane.bandwebbackend.domain.home.controller;

import com.crane.bandwebbackend.domain.admin.service.AdminAuditLogService;
import com.crane.bandwebbackend.domain.home.dto.ClubAuditLogItem;
import com.crane.bandwebbackend.domain.home.dto.ClubDashboardResponse;
import com.crane.bandwebbackend.domain.home.dto.HomeAdminResponse;
import com.crane.bandwebbackend.domain.home.dto.HomeBadgeResponse;
import com.crane.bandwebbackend.domain.home.dto.HomeResponse;
import com.crane.bandwebbackend.domain.home.service.HomeAdminService;
import com.crane.bandwebbackend.domain.home.service.HomeService;
import com.crane.bandwebbackend.domain.membership.service.MembershipService;
import com.crane.bandwebbackend.global.response.ApiIds;
import com.crane.bandwebbackend.global.response.PageResponse;
import com.crane.bandwebbackend.global.security.AuthPrincipal;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 동아리 홈·운영 현황(API 116·117·137·138·139). 모두 읽기 전용
@Validated
@RestController
@RequestMapping("/api/v1/clubs/{clubId}")
@RequiredArgsConstructor
public class HomeController {

    private final AdminAuditLogService auditLogService;
    private final MembershipService membershipService;
    private final HomeAdminService homeAdminService;
    private final HomeService homeService;

    // API 116: 임원용 동아리 감사 로그(API 125와 같은 필터, clubId는 경로로 고정)
    @GetMapping("/audit-logs")
    public PageResponse<ClubAuditLogItem> auditLogs(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String clubId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String targetId,
            @RequestParam(required = false) String actorUserId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Long club = ApiIds.positive(clubId, "clubId");
        membershipService.requireAdmin(club, principal.userId());
        return PageResponse.from(auditLogService
                .searchLogs(String.valueOf(club), action, targetType, targetId, actorUserId, from, to, page, size)
                .map(ClubAuditLogItem::from));
    }

    // API 117
    @GetMapping("/dashboard")
    public ClubDashboardResponse dashboard(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable String clubId) {
        return homeAdminService.dashboard(ApiIds.positive(clubId, "clubId"), principal.userId());
    }

    // API 137
    @GetMapping("/home")
    public HomeResponse home(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String clubId) {
        return homeService.home(ApiIds.positive(clubId, "clubId"), principal.userId());
    }

    // API 138
    @GetMapping("/home/admin")
    public HomeAdminResponse homeAdmin(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String clubId) {
        return homeAdminService.adminHome(ApiIds.positive(clubId, "clubId"), principal.userId());
    }

    // API 139
    @GetMapping("/home/badge")
    public HomeBadgeResponse badge(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable String clubId) {
        return homeService.badge(ApiIds.positive(clubId, "clubId"), principal.userId());
    }
}
