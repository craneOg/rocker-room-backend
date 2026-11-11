package com.crane.bandwebbackend.domain.membership.controller;

import com.crane.bandwebbackend.domain.membership.dto.MembershipInviteRequest;
import com.crane.bandwebbackend.domain.membership.dto.MembershipProfileRequest;
import com.crane.bandwebbackend.domain.membership.dto.MembershipReasonRequest;
import com.crane.bandwebbackend.domain.membership.dto.MembershipResponse;
import com.crane.bandwebbackend.domain.membership.dto.MembershipTypeRequest;
import com.crane.bandwebbackend.domain.membership.dto.MembershipWithdrawalRequest;
import com.crane.bandwebbackend.domain.membership.dto.MembershipWithdrawalResult;
import com.crane.bandwebbackend.domain.membership.dto.WithdrawalPreviewResponse;
import com.crane.bandwebbackend.domain.membership.entity.MemberType;
import com.crane.bandwebbackend.domain.membership.entity.Membership;
import com.crane.bandwebbackend.domain.membership.entity.MembershipRole;
import com.crane.bandwebbackend.domain.membership.entity.MembershipStatus;
import com.crane.bandwebbackend.domain.membership.service.MemberDirectoryService;
import com.crane.bandwebbackend.domain.membership.service.MembershipDecisionService;
import com.crane.bandwebbackend.domain.membership.service.MembershipService;
import com.crane.bandwebbackend.domain.membership.service.MembershipStatusService;
import com.crane.bandwebbackend.domain.membership.service.MembershipWithdrawalPreviewService;
import com.crane.bandwebbackend.domain.membership.service.MembershipWithdrawalService;
import com.crane.bandwebbackend.domain.part.service.MembershipPartReader;
import com.crane.bandwebbackend.global.error.BusinessException;
import com.crane.bandwebbackend.global.error.ErrorCode;
import com.crane.bandwebbackend.global.response.ApiIds;
import com.crane.bandwebbackend.global.response.ApiResponse;
import com.crane.bandwebbackend.global.response.PageResponse;
import com.crane.bandwebbackend.global.security.CurrentActor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * MEM-01/03. 인증이 없는 현재 상태에서는 이 컨트롤러를 등록하지 않는다.
 *
 * <p>CurrentActor 구현과 인증 필터의 토큰 검증/CSRF 헤더 검사 → 변경 POST의
 * Idempotency-Key 저장 및 재시도
 * 응답 처리 → rocker.api-v1.enabled=true.
 * 현재는 헤더의 형식과 존재만 검사한다. 멱등 저장소를 연결하기 전에 플래그를 켜면 안 된다.
 * actorUserId는 반드시 CurrentActor에서만 받는다. 서비스는 요청마다 membership.role을 조회한다.
 */
@RestController
@RequestMapping("/api/v1/clubs/{clubId}/memberships")
@ConditionalOnProperty(prefix = "rocker.api-v1", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class MembershipController {
    private final MembershipService service;
    private final MembershipStatusService statusService;
    private final MembershipDecisionService decisionService;
    private final MembershipPartReader partReader;
    private final MemberDirectoryService directory;
    private final MembershipWithdrawalPreviewService withdrawalPreview;
    private final MembershipWithdrawalService withdrawalService;
    private final CurrentActor currentActor;

    /** 소속 목록을 조회한다 */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<Object>>> list(
            @PathVariable String clubId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) MembershipRole role,
            @RequestParam(required = false) MemberType memberType,
            @RequestParam(required = false) String partId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "page 또는 size가 유효하지 않습니다.");
        }
        Long actorId = currentActor.requireUserId();
        Page<Object> items = directory.list(
                ApiIds.positive(clubId, "clubId"),
                actorId,
                q,
                status,
                role,
                memberType,
                partId,
                PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(items)));
    }

    /** 소속 초대한다 */
    @PostMapping("/invite")
    public ResponseEntity<ApiResponse<MembershipResponse>> invite(
            @PathVariable String clubId, @Valid @RequestBody MembershipInviteRequest body, HttpServletRequest request) {
        Long club = ApiIds.positive(clubId, "clubId");
        Long actor = currentActor.requireUserId();
        var user = directory.lookup(club, actor, body.handle()).user();
        boolean existed = service.exists(club, user.getId());
        Membership invited = service.invite(
                club,
                actor,
                body.handle(),
                body.memberType() == null ? MemberType.REGULAR : body.memberType(),
                body.displayName());
        return ResponseEntity.status(existed ? HttpStatus.OK : HttpStatus.CREATED)
                .body(ApiResponse.ok(MembershipResponse.from(invited, partReader.partsOf(invited.getId()), actor)));
    }

    /** 소속 상세 정보를 조회한다 */
    @GetMapping("/{membershipId}")
    public ResponseEntity<ApiResponse<Object>> detail(
            @PathVariable String clubId, @PathVariable String membershipId, HttpServletRequest request) {
        Long club = ApiIds.positive(clubId, "clubId");
        Long id = ApiIds.positive(membershipId, "membershipId");
        return ResponseEntity.ok(ApiResponse.ok(directory.detail(club, id, currentActor.requireUserId())));
    }

    /** 소속 탈퇴 영향을 미리 조회한다 */
    @GetMapping("/{membershipId}/withdraw-preview")
    public ResponseEntity<ApiResponse<WithdrawalPreviewResponse>> withdrawalPreview(
            @PathVariable String clubId, @PathVariable String membershipId, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(withdrawalPreview.preview(
                ApiIds.positive(clubId, "clubId"),
                ApiIds.positive(membershipId, "membershipId"),
                currentActor.requireUserId())));
    }

    /** 소속 철회하거나 탈퇴한다 */
    @PostMapping("/{membershipId}/withdraw")
    public ResponseEntity<ApiResponse<MembershipWithdrawalResult>> withdraw(
            @PathVariable String clubId,
            @PathVariable String membershipId,
            @Valid @RequestBody(required = false) MembershipWithdrawalRequest body,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(withdrawalService.withdraw(
                ApiIds.positive(clubId, "clubId"),
                ApiIds.positive(membershipId, "membershipId"),
                currentActor.requireUserId(),
                body)));
    }

    /** 소속 승인한다 */
    @PostMapping("/{membershipId}/approve")
    public ResponseEntity<ApiResponse<MembershipResponse>> approve(
            @PathVariable String clubId,
            @PathVariable String membershipId,
            @Valid @RequestBody(required = false) MembershipReasonRequest body,
            HttpServletRequest request) {
        return ResponseEntity.ok(decide(
                clubId,
                membershipId,
                MembershipStatus.PENDING,
                MembershipStatus.ACTIVE,
                body == null ? null : body.reason(),
                request));
    }

    /** 소속 거절한다 */
    @PostMapping("/{membershipId}/reject")
    public ResponseEntity<ApiResponse<MembershipResponse>> reject(
            @PathVariable String clubId,
            @PathVariable String membershipId,
            @Valid @RequestBody MembershipReasonRequest body,
            HttpServletRequest request) {
        return ResponseEntity.ok(decide(
                clubId, membershipId, MembershipStatus.PENDING, MembershipStatus.REJECTED, body.reason(), request));
    }

    /** 소속 초대를 수락한다 */
    @PostMapping("/{membershipId}/accept")
    public ResponseEntity<ApiResponse<MembershipResponse>> accept(
            @PathVariable String clubId,
            @PathVariable String membershipId,
            @Valid @RequestBody(required = false) MembershipReasonRequest body,
            HttpServletRequest request) {
        return ResponseEntity.ok(
                decide(clubId, membershipId, MembershipStatus.INVITED, MembershipStatus.ACTIVE, null, request));
    }

    /** 소속 초대를 거절한다 */
    @PostMapping("/{membershipId}/decline")
    public ResponseEntity<ApiResponse<MembershipResponse>> decline(
            @PathVariable String clubId,
            @PathVariable String membershipId,
            @Valid @RequestBody MembershipReasonRequest body,
            HttpServletRequest request) {
        return ResponseEntity.ok(decide(
                clubId, membershipId, MembershipStatus.INVITED, MembershipStatus.DECLINED, body.reason(), request));
    }

    /** 가입 신청 또는 초대에 대한 단건 결정을 공통 처리한다. */
    private ApiResponse<MembershipResponse> decide(
            String clubId,
            String membershipId,
            MembershipStatus expectedSource,
            MembershipStatus target,
            String reason,
            HttpServletRequest request) {
        return response(
                decisionService.decide(
                        ApiIds.positive(clubId, "clubId"),
                        ApiIds.positive(membershipId, "membershipId"),
                        currentActor.requireUserId(),
                        expectedSource,
                        target,
                        reason),
                request);
    }

    /** 소속 프로필을 변경한다 */
    @PatchMapping("/{membershipId}")
    public ResponseEntity<ApiResponse<MembershipResponse>> profile(
            @PathVariable String clubId,
            @PathVariable String membershipId,
            @Valid @RequestBody MembershipProfileRequest body,
            HttpServletRequest request) {
        return ResponseEntity.ok(response(
                statusService.updateDisplayName(
                        ApiIds.positive(clubId, "clubId"),
                        ApiIds.positive(membershipId, "membershipId"),
                        currentActor.requireUserId(),
                        body.displayName()),
                request));
    }

    /** 소속 휴학 상태로 전환한다 */
    @PostMapping("/{membershipId}/take-leave")
    public ResponseEntity<ApiResponse<MembershipResponse>> takeLeave(
            @PathVariable String clubId,
            @PathVariable String membershipId,
            @Valid @RequestBody MembershipReasonRequest body,
            HttpServletRequest request) {
        return ResponseEntity.ok(transition(clubId, membershipId, body, MembershipStatus.ON_LEAVE, request));
    }

    /** 소속 졸업 상태로 전환한다 */
    @PostMapping("/{membershipId}/graduate")
    public ResponseEntity<ApiResponse<MembershipResponse>> graduate(
            @PathVariable String clubId,
            @PathVariable String membershipId,
            @Valid @RequestBody MembershipReasonRequest body,
            HttpServletRequest request) {
        return ResponseEntity.ok(transition(clubId, membershipId, body, MembershipStatus.GRADUATED, request));
    }

    /** 소속 활동 상태로 복귀한다 */
    @PostMapping("/{membershipId}/resume")
    public ResponseEntity<ApiResponse<MembershipResponse>> resume(
            @PathVariable String clubId,
            @PathVariable String membershipId,
            @Valid @RequestBody MembershipReasonRequest body,
            HttpServletRequest request) {
        return ResponseEntity.ok(transition(clubId, membershipId, body, MembershipStatus.ACTIVE, request));
    }

    /** 소속 부원 유형을 변경한다 */
    @PutMapping("/{membershipId}/member-type")
    public ResponseEntity<ApiResponse<MembershipResponse>> memberType(
            @PathVariable String clubId,
            @PathVariable String membershipId,
            @Valid @RequestBody MembershipTypeRequest body,
            HttpServletRequest request) {
        return ResponseEntity.ok(response(
                statusService.changeType(
                        ApiIds.positive(clubId, "clubId"),
                        ApiIds.positive(membershipId, "membershipId"),
                        currentActor.requireUserId(),
                        body.memberType(),
                        body.reason()),
                request));
    }

    /** 소속 상태 변경 요청을 공통 처리한다. */
    private ApiResponse<MembershipResponse> transition(
            String clubId,
            String membershipId,
            MembershipReasonRequest body,
            MembershipStatus target,
            HttpServletRequest request) {
        return response(
                statusService.transition(
                        ApiIds.positive(clubId, "clubId"),
                        ApiIds.positive(membershipId, "membershipId"),
                        currentActor.requireUserId(),
                        target,
                        body.reason()),
                request);
    }

    /** 소속 엔티티와 보유 파트를 표준 API 응답으로 변환한다. */
    private ApiResponse<MembershipResponse> response(Membership member, HttpServletRequest request) {
        return ApiResponse.ok(
                MembershipResponse.from(member, partReader.partsOf(member.getId()), currentActor.requireUserId()));
    }
}
