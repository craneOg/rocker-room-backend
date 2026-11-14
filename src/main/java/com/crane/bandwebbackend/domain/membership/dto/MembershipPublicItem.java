package com.crane.bandwebbackend.domain.membership.dto;

import com.crane.bandwebbackend.domain.membership.entity.MemberType;
import com.crane.bandwebbackend.domain.membership.entity.Membership;
import com.crane.bandwebbackend.domain.membership.entity.MembershipRole;
import com.crane.bandwebbackend.domain.part.dto.PartResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** API-020 부원용 공개 필드. 휴학·졸업 상태와 handle은 노출하지 않는다. */
@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode
public class MembershipPublicItem {
    @JsonProperty("id")
    private String id;

    @JsonProperty("displayName")
    private String displayName;

    @JsonProperty("userName")
    private String userName;

    @JsonProperty("memberType")
    private MemberType memberType;

    @JsonProperty("role")
    private MembershipRole role;

    @JsonProperty("profileImageUrl")
    private String profileImageUrl;

    @JsonProperty("parts")
    private List<Part> parts;

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @EqualsAndHashCode
    public static class Part {
        @JsonProperty("id")
        private String id;

        @JsonProperty("name")
        private String name;

        @JsonProperty("displayOrder")
        private int displayOrder;

        @JsonProperty("isActive")
        @Getter(onMethod_ = @JsonProperty("isActive"))
        private boolean isActive;
    }

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
