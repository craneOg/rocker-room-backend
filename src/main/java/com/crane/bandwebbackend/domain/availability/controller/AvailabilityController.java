package com.crane.bandwebbackend.domain.availability.controller;

import com.crane.bandwebbackend.domain.availability.dto.AvailabilityRequest;
import com.crane.bandwebbackend.domain.availability.dto.AvailabilityResponse;
import com.crane.bandwebbackend.domain.availability.dto.CommonAvailabilityResponse;
import com.crane.bandwebbackend.domain.availability.service.AvailabilityService;
import com.crane.bandwebbackend.domain.participant.service.PerformanceRef;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// API-104(revision 조회)는 폐기 예정이라 구현하지 않음
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/clubs/{clubId}/performances/{performanceId}/availability")
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    // API-100. from·to를 생략하면 전체
    @GetMapping("/me")
    public AvailabilityResponse getMine(
            @PathVariable Long clubId,
            @PathVariable Long performanceId,
            @AuthenticationPrincipal(expression = "userId()") Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        return availabilityService.getMine(new PerformanceRef(clubId, performanceId, userId), from, to);
    }

    // API-101
    @PutMapping("/me")
    public AvailabilityResponse replaceMine(
            @PathVariable Long clubId,
            @PathVariable Long performanceId,
            @AuthenticationPrincipal(expression = "userId()") Long userId,
            @Valid @RequestBody AvailabilityRequest req) {
        return availabilityService.replaceMine(new PerformanceRef(clubId, performanceId, userId), req);
    }

    // API-102
    @GetMapping("/members/{performanceMemberId}")
    public AvailabilityResponse getMember(
            @PathVariable Long clubId,
            @PathVariable Long performanceId,
            @PathVariable Long performanceMemberId,
            @AuthenticationPrincipal(expression = "userId()") Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        return availabilityService.getMember(
                new PerformanceRef(clubId, performanceId, userId), performanceMemberId, from, to);
    }

    // API-103. teamIds·songIds는 쉼표 구분
    @GetMapping("/common")
    public CommonAvailabilityResponse common(
            @PathVariable Long clubId,
            @PathVariable Long performanceId,
            @AuthenticationPrincipal(expression = "userId()") Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) List<Long> teamIds,
            @RequestParam(required = false) List<Long> songIds,
            @RequestParam(defaultValue = "false") boolean forAllParticipants) {
        return availabilityService.common(
                new PerformanceRef(clubId, performanceId, userId), from, to, teamIds, songIds, forAllParticipants);
    }
}
