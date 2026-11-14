package com.crane.bandwebbackend.domain.membership.dto;

import com.crane.bandwebbackend.domain.membership.entity.JoinSource;
import com.crane.bandwebbackend.domain.membership.entity.MemberType;
import com.crane.bandwebbackend.domain.membership.entity.Membership;
import com.crane.bandwebbackend.domain.membership.entity.MembershipRole;
import com.crane.bandwebbackend.domain.membership.entity.MembershipStatus;
import com.crane.bandwebbackend.domain.part.dto.PartResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** MEM-01/03의 내부 상세 계약. 공개 목록에는 사용하지 않는다. */
@Builder
@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode
public class MembershipResponse {
    @JsonProperty("id")
    private String id;

    @JsonProperty("clubId")
    private String clubId;

    @JsonProperty("userId")
    private String userId;

    @JsonProperty("userName")
    private String userName;

    @JsonProperty("handle")
    private String handle;

    @JsonProperty("profileImageUrl")
    private String profileImageUrl;

    @JsonProperty("displayName")
    private String displayName;

    @JsonProperty("memberType")
    private MemberType memberType;

    @JsonProperty("role")
    private MembershipRole role;

    @JsonProperty("status")
    private MembershipStatus status;

    @JsonProperty("joinSource")
    private JoinSource joinSource;

    @JsonProperty("invitedAt")
    private OffsetDateTime invitedAt;

    @JsonProperty("invitationExpiresAt")
    private OffsetDateTime invitationExpiresAt;

    @JsonProperty("approvedAt")
    private OffsetDateTime approvedAt;

    @JsonProperty("requestedAt")
    private OffsetDateTime requestedAt;

    @JsonProperty("leftAt")
    private OffsetDateTime leftAt;

    @JsonProperty("parts")
    private List<PartResponse> parts;

    @JsonProperty("createdAt")
    private OffsetDateTime createdAt;

    @JsonProperty("updatedAt")
    private OffsetDateTime updatedAt;

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
