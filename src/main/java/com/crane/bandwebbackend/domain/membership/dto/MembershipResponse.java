package com.crane.bandwebbackend.domain.membership.dto;

import com.crane.bandwebbackend.domain.membership.entity.JoinSource;
import com.crane.bandwebbackend.domain.membership.entity.MemberType;
import com.crane.bandwebbackend.domain.membership.entity.Membership;
import com.crane.bandwebbackend.domain.membership.entity.MembershipRole;
import com.crane.bandwebbackend.domain.membership.entity.MembershipStatus;
import com.crane.bandwebbackend.domain.part.dto.PartResponse;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import lombok.Builder;

/** MEM-01/03의 내부 상세 계약. 공개 목록에는 사용하지 않는다. */
@Builder
public record MembershipResponse(
        String id,
        String clubId,
        String userId,
        String userName,
        String handle,
        String profileImageUrl,
        String displayName,
        MemberType memberType,
        MembershipRole role,
        MembershipStatus status,
        JoinSource joinSource,
        OffsetDateTime invitedAt,
        OffsetDateTime invitationExpiresAt,
        OffsetDateTime approvedAt,
        OffsetDateTime requestedAt,
        OffsetDateTime leftAt,
        List<PartResponse> parts,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    /** 소속 엔티티를 응답 데이터로 변환한다 */
    public static MembershipResponse from(Membership membership, List<PartResponse> parts) {
        return from(membership, parts, null);
    }

    /** 소속 엔티티를 응답 데이터로 변환한다 */
    public static MembershipResponse from(Membership membership, List<PartResponse> parts, Long viewerUserId) {
        LocalDateTime invited = membership.getInvitedAt();
        OffsetDateTime expires = invited == null
                ? null
                : invited.atZone(ZoneOffset.UTC)
                        .withZoneSameInstant(ZoneId.of("Asia/Seoul"))
                        .plusMonths(1)
                        .toOffsetDateTime();
        return MembershipResponse.builder()
                .id(membership.getId().toString())
                .clubId(membership.getClub().getId().toString())
                .userId(membership.getUser().getId().toString())
                .userName(membership.getUser().getName())
                .handle(membership.getUser().getHandle())
                .profileImageUrl(membership
                        .getUser()
                        .profileImageUrlFor(
                                membership.getUser().getId().equals(viewerUserId) ? membership.getUser() : null))
                .displayName(
                        membership.getDisplayName() == null
                                ? membership.getUser().getName()
                                : membership.getDisplayName())
                .memberType(membership.getMemberType())
                .role(membership.getRole())
                .status(membership.getStatus())
                .joinSource(membership.getJoinSource())
                .invitedAt(kst(invited))
                .invitationExpiresAt(expires)
                .approvedAt(kst(membership.getApprovedAt()))
                .requestedAt(kst(membership.getCreatedAt()))
                .leftAt(kst(membership.getLeftAt()))
                .parts(parts)
                .createdAt(kst(membership.getCreatedAt()))
                .updatedAt(kst(membership.getUpdatedAt()))
                .build();
    }

    private static OffsetDateTime kst(LocalDateTime value) {
        return value == null
                ? null
                : value.atOffset(java.time.ZoneOffset.UTC)
                        .atZoneSameInstant(ZoneId.of("Asia/Seoul"))
                        .toOffsetDateTime();
    }
}
