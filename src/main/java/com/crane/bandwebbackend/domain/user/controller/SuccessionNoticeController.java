package com.crane.bandwebbackend.domain.user.controller;

import com.crane.bandwebbackend.domain.user.dto.SuccessionNoticeListResponse;
import com.crane.bandwebbackend.domain.user.dto.SuccessionNoticeResponse;
import com.crane.bandwebbackend.domain.user.service.SuccessionNoticeService;
import com.crane.bandwebbackend.global.security.AuthPrincipal;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/succession-notices")
@RequiredArgsConstructor
public class SuccessionNoticeController {

    private final SuccessionNoticeService service;

    // API 135
    @GetMapping
    public SuccessionNoticeListResponse list(@AuthenticationPrincipal AuthPrincipal principal) {
        return service.list(principal.userId());
    }

    // API 136: 요청 본문은 없거나 {}
    @PostMapping("/{noticeId}/confirm")
    public SuccessionNoticeResponse confirm(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable @Positive Long noticeId) {
        return service.confirm(principal.userId(), noticeId);
    }
}
