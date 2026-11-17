package com.crane.bandwebbackend.domain.club.controller;

import com.crane.bandwebbackend.domain.club.dto.ClubCreateRequest;
import com.crane.bandwebbackend.domain.club.dto.ClubDetailResponse;
import com.crane.bandwebbackend.domain.club.dto.ClubResponse;
import com.crane.bandwebbackend.domain.club.dto.ClubUpdateRequest;
import com.crane.bandwebbackend.domain.club.dto.JoinCodeRegenerateRequest;
import com.crane.bandwebbackend.domain.club.dto.JoinCodeResponse;
import com.crane.bandwebbackend.domain.club.service.ClubService;
import com.crane.bandwebbackend.global.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ClubController {

    private final ClubService clubService;

    // API 11
    @PostMapping(value = "/clubs", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ClubResponse create(
            @AuthenticationPrincipal AuthPrincipal principal, @RequestBody ClubCreateRequest request) {
        return clubService.create(principal.userId(), request);
    }

    // API 15: 정회원은 Club 전체, 객원은 기본정보만이라 응답 타입이 둘이다
    @GetMapping("/clubs/{clubId}")
    public Object detail(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long clubId) {
        return clubService.detail(principal.userId(), clubId);
    }

    // API 16
    @PatchMapping(value = "/clubs/{clubId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ClubDetailResponse update(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long clubId,
            @RequestBody ClubUpdateRequest request) {
        return clubService.update(principal.userId(), clubId, request);
    }

    // API 17
    @GetMapping("/clubs/{clubId}/join-code")
    public JoinCodeResponse getJoinCode(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long clubId) {
        return clubService.getJoinCode(principal.userId(), clubId);
    }

    // API 18: 바디는 선택이다
    @PostMapping("/clubs/{clubId}/join-code/regenerate")
    public JoinCodeResponse regenerateJoinCode(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long clubId,
            @RequestBody(required = false) JoinCodeRegenerateRequest request) {
        return clubService.regenerateJoinCode(principal.userId(), clubId, request);
    }

    // API 19
    @DeleteMapping("/clubs/{clubId}/join-code")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disableJoinCode(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long clubId) {
        clubService.disableJoinCode(principal.userId(), clubId);
    }
}
