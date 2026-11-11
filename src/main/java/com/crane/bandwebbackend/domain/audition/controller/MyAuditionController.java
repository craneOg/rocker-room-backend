package com.crane.bandwebbackend.domain.audition.controller;

import com.crane.bandwebbackend.domain.audition.dto.MyAuditionsResponse;
import com.crane.bandwebbackend.domain.audition.service.AuditionService;
import com.crane.bandwebbackend.global.response.ApiIds;
import com.crane.bandwebbackend.global.response.ApiResponse;
import com.crane.bandwebbackend.global.security.CurrentActor;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API-150. User 도메인의 컨트롤러와 독립된 오디션 조회 API이다. */
@RestController
@RequestMapping("/api/v1/me/auditions")
@ConditionalOnProperty(prefix = "rocker.api-v1", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class MyAuditionController {
    private final AuditionService service;
    private final CurrentActor actor;

    /** 오디션 목록을 조회한다 */
    @GetMapping
    public ResponseEntity<ApiResponse<MyAuditionsResponse>> list(
            @RequestParam(required = false) String clubId,
            @RequestParam(required = false) String performanceId,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(service.mine(
                actor.requireUserId(),
                clubId == null ? null : ApiIds.positive(clubId, "clubId"),
                performanceId == null ? null : ApiIds.positive(performanceId, "performanceId"))));
    }
}
