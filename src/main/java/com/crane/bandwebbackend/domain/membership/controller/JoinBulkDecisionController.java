package com.crane.bandwebbackend.domain.membership.controller;

import com.crane.bandwebbackend.domain.membership.dto.JoinBulkDecisionRequest;
import com.crane.bandwebbackend.domain.membership.dto.JoinBulkDecisionResponse;
import com.crane.bandwebbackend.domain.membership.service.JoinBulkDecisionService;
import com.crane.bandwebbackend.global.response.ApiIds;
import com.crane.bandwebbackend.global.response.ApiResponse;
import com.crane.bandwebbackend.global.security.CurrentActor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/clubs/{clubId}/join-requests/bulk-decisions")
@ConditionalOnProperty(prefix = "rocker.api-v1", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class JoinBulkDecisionController {
    private final JoinBulkDecisionService service;
    private final CurrentActor actor;

    /** 소속 신청을 승인하거나 거절한다 */
    @PostMapping
    public ResponseEntity<ApiResponse<JoinBulkDecisionResponse>> decide(
            @PathVariable String clubId, @Valid @RequestBody JoinBulkDecisionRequest body, HttpServletRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok(service.decide(ApiIds.positive(clubId, "clubId"), actor.requireUserId(), body)));
    }
}
