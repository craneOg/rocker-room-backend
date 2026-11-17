package com.crane.bandwebbackend.domain.club.controller;

import com.crane.bandwebbackend.domain.club.dto.ClubCreateRequest;
import com.crane.bandwebbackend.domain.club.dto.ClubResponse;
import com.crane.bandwebbackend.domain.club.service.ClubService;
import com.crane.bandwebbackend.global.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
}
