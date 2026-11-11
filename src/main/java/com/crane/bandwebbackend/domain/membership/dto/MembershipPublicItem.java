package com.crane.bandwebbackend.domain.membership.dto;

import com.crane.bandwebbackend.domain.membership.entity.MemberType;
import com.crane.bandwebbackend.domain.membership.entity.Membership;
import com.crane.bandwebbackend.domain.membership.entity.MembershipRole;
import com.crane.bandwebbackend.domain.part.dto.PartResponse;
import java.util.List;

/** API-020 부원용 공개 필드. 휴학·졸업 상태와 handle은 노출하지 않는다. */
public record MembershipPublicItem(
        String id,
        String displayName,
        String userName,
        MemberType memberType,
        MembershipRole role,
        String profileImageUrl,
        List<Part> parts) {
    public record Part(String id, String name, int displayOrder, boolean isActive) {}

    /** 소속 엔티티를 응답 데이터로 변환한다 */
    public static MembershipPublicItem from(Membership member, List<PartResponse> parts, Long viewerUserId) {
        return new MembershipPublicItem(
                member.getId().toString(),
                member.getDisplayName() == null ? member.getUser().getName() : member.getDisplayName(),
                member.getUser().getName(),
                member.getMemberType(),
                member.getRole(),
                member.getUser()
                        .profileImageUrlFor(member.getUser().getId().equals(viewerUserId) ? member.getUser() : null),
                parts.stream()
                        .map(part -> new Part(part.getId(), part.getName(), part.getDisplayOrder(), part.isActive()))
                        .toList());
    }
}
