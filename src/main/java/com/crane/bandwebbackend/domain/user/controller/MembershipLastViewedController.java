package com.crane.bandwebbackend.domain.user.controller;

import com.crane.bandwebbackend.domain.user.dto.LastViewedResponse;
import com.crane.bandwebbackend.domain.user.service.MembershipLastViewedService;
import com.crane.bandwebbackend.global.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MembershipLastViewedController {

    private final MembershipLastViewedService service;

    // API 140: 본문 없음. 동아리 전환(HOME-002) 때 호출해 last_viewed_at만 갱신한다(updated_at 불변)
    @PutMapping("/me/memberships/{membershipId}/last-viewed")
    public LastViewedResponse touch(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable String membershipId) {
        return service.touch(principal.userId(), membershipId);
    }
}
