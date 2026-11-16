package com.crane.bandwebbackend.domain.participant.dto;

import com.crane.bandwebbackend.domain.membership.entity.MemberType;
import com.crane.bandwebbackend.domain.membership.entity.Membership;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMember;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMemberSource;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMemberStatus;
import com.crane.bandwebbackend.global.response.RevisionTag;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** PAR-02/03의 본인·임원용 상세 응답. 일반 공개 목록에는 source/revision을 내보내지 않는다. */
@Builder
@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode
public class ParticipantResponse {
    @JsonProperty("id")
    private String id;

    @JsonProperty("performanceId")
    private String performanceId;

    @JsonProperty("membershipId")
    private String membershipId;

    @JsonProperty("displayName")
    private String displayName;

    @JsonProperty("memberType")
    private MemberType memberType;

    @JsonProperty("status")
    private PerformanceMemberStatus status;

    @JsonProperty("source")
    private PerformanceMemberSource source;

    @JsonProperty("decidedAt")
    private OffsetDateTime decidedAt;

    @JsonProperty("revision")
    private String revision;

    @JsonProperty("createdAt")
    private OffsetDateTime createdAt;

    @JsonProperty("updatedAt")
    private OffsetDateTime updatedAt;

    /** 공연 참여 엔티티를 응답 데이터로 변환한다 */
    public static ParticipantResponse from(PerformanceMember member) {
        Membership membership = member.getMembership();
        return ParticipantResponse.builder()
                .id(member.getId().toString())
                .performanceId(member.getPerformance().getId().toString())
                .membershipId(membership.getId().toString())
                .displayName(
                        membership.getDisplayName() == null
                                ? membership.getUser().getName()
                                : membership.getDisplayName())
                .memberType(membership.getMemberType())
                .status(member.getStatus())
                .source(member.getSource())
                .decidedAt(utc(member.getDecidedAt()))
                .revision(RevisionTag.from("p", member.getId(), member.getUpdatedAt()))
                .createdAt(utc(member.getCreatedAt()))
                .updatedAt(utc(member.getUpdatedAt()))
                .build();
    }

    private static OffsetDateTime utc(LocalDateTime value) {
        return value == null
                ? null
                : value.atOffset(ZoneOffset.UTC)
                        .atZoneSameInstant(java.time.ZoneId.of("Asia/Seoul"))
                        .toOffsetDateTime();
    }
}
