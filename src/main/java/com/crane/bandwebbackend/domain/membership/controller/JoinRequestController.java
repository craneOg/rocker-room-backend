package com.crane.bandwebbackend.domain.membership.controller;

import com.crane.bandwebbackend.domain.membership.dto.JoinRequestCreateRequest;
import com.crane.bandwebbackend.domain.membership.dto.MembershipResponse;
import com.crane.bandwebbackend.domain.membership.entity.Membership;
import com.crane.bandwebbackend.domain.membership.service.MembershipService;
import com.crane.bandwebbackend.domain.part.service.MembershipPartReader;
import com.crane.bandwebbackend.global.response.ApiIds;
import com.crane.bandwebbackend.global.response.ApiResponse;
import com.crane.bandwebbackend.global.security.CurrentActor;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API-014·028: 본인의 가입 신청과 취소. */
@RestController
@RequestMapping("/api/v1/clubs/{clubId}/join-requests")
@RequiredArgsConstructor
public class JoinRequestController {
    private final MembershipService service;
    private final MembershipPartReader partReader;
    private final CurrentActor currentActor;

    /** 가입을 신청한다 */
    @PostMapping
    public ResponseEntity<ApiResponse<MembershipResponse>> apply(
            @PathVariable String clubId, @Valid @RequestBody JoinRequestCreateRequest body) {
        Long actor = currentActor.requireUserId();
        Membership applied = service.applyToJoin(
                ApiIds.positive(clubId, "clubId"), actor, body.getSource(), body.getJoinCode(), body.getDisplayName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response(applied, actor));
    }

    /** 대기 중인 가입 신청을 취소한다 */
    @PostMapping("/{requestId}/cancel")
    public ResponseEntity<ApiResponse<MembershipResponse>> cancel(
            @PathVariable String clubId, @PathVariable String requestId) {
        Long actor = currentActor.requireUserId();
        Membership canceled = service.cancelJoinRequest(
                ApiIds.positive(clubId, "clubId"), ApiIds.positive(requestId, "requestId"), actor);
        return ResponseEntity.ok(response(canceled, actor));
    }

    private ApiResponse<MembershipResponse> response(Membership member, Long actor) {
        return ApiResponse.ok(MembershipResponse.from(member, partReader.partsOf(member.getId()), actor));
    }
}
