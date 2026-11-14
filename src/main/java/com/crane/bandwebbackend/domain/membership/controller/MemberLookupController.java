package com.crane.bandwebbackend.domain.membership.controller;

import com.crane.bandwebbackend.domain.membership.entity.Membership;
import com.crane.bandwebbackend.domain.membership.service.MemberDirectoryService;
import com.crane.bandwebbackend.global.response.ApiIds;
import com.crane.bandwebbackend.global.response.ApiResponse;
import com.crane.bandwebbackend.global.security.CurrentActor;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API-022: handle 완전 일치 검색. */
@RestController
@RequestMapping("/api/v1/clubs/{clubId}/users/lookup")
@ConditionalOnProperty(prefix = "rocker.api-v1", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class MemberLookupController {
    private final MemberDirectoryService directory;
    private final CurrentActor currentActor;

    /** 소속 사용자를 조회한다 */
    @GetMapping
    public ResponseEntity<ApiResponse<LookupResponse>> lookup(
            @PathVariable String clubId, @RequestParam String handle, HttpServletRequest request) {
        Long actor = currentActor.requireUserId();
        var result = directory.lookup(ApiIds.positive(clubId, "clubId"), actor, handle);
        Membership member = result.getMembership();
        MembershipInfo info = member == null
                ? null
                : new MembershipInfo(
                        member.getId().toString(),
                        member.getStatus().name(),
                        member.getInvitedAt() == null
                                ? null
                                : member.getInvitedAt()
                                        .atOffset(ZoneOffset.UTC)
                                        .atZoneSameInstant(ZoneId.of("Asia/Seoul"))
                                        .plusMonths(1)
                                        .toOffsetDateTime());
        return ResponseEntity.ok(ApiResponse.ok(new LookupResponse(
                result.getUser().getId().toString(),
                result.getUser().getHandle(),
                result.getUser().getName(),
                result.getUser().profileImageUrlFor(result.getUser().getId().equals(actor) ? result.getUser() : null),
                info)));
    }

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @EqualsAndHashCode
    public static class LookupResponse {
        @JsonProperty("userId")
        private String userId;

        @JsonProperty("handle")
        private String handle;

        @JsonProperty("name")
        private String name;

        @JsonProperty("profileImageUrl")
        private String profileImageUrl;

        @JsonProperty("membership")
        private MembershipInfo membership;
    }

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @EqualsAndHashCode
    public static class MembershipInfo {
        @JsonProperty("id")
        private String id;

        @JsonProperty("status")
        private String status;

        @JsonProperty("invitationExpiresAt")
        private OffsetDateTime invitationExpiresAt;
    }
}
