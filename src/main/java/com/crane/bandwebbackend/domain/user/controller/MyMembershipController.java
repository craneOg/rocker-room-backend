package com.crane.bandwebbackend.domain.user.controller;

import com.crane.bandwebbackend.domain.user.dto.MyMembershipsResponse;
import com.crane.bandwebbackend.domain.user.service.MyMembershipService;
import com.crane.bandwebbackend.global.security.AuthPrincipal;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
public class MyMembershipController {

    private final MyMembershipService myMembershipService;

    // API 10: status는 쉼표로 여러 값(없으면 전체). 클래스 수준 @RequestMapping은 API 140 쪽과 겹치지 않게 두지 않는다
    @GetMapping("/api/v1/me/memberships")
    public MyMembershipsResponse list(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "100") @Min(1) @Max(100) int size) {
        return myMembershipService.list(principal.userId(), status, page, size);
    }
}
