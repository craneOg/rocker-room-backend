package com.crane.bandwebbackend.domain.participant.dto;

import com.crane.bandwebbackend.domain.membership.entity.MemberType;
import com.crane.bandwebbackend.domain.membership.entity.Membership;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMember;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMemberSource;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMemberStatus;
import com.crane.bandwebbackend.global.response.RevisionTag;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.Builder;

/** PAR-02/03의 본인·임원용 상세 응답. 일반 공개 목록에는 source/revision을 내보내지 않는다. */
@Builder
public record ParticipantResponse(
		String id,
		String performanceId,
		String membershipId,
		String displayName,
		MemberType memberType,
		PerformanceMemberStatus status,
		PerformanceMemberSource source,
		OffsetDateTime decidedAt,
		String revision,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt
) {

	/** 공연 참여 엔티티를 응답 데이터로 변환한다 */
	public static ParticipantResponse from(PerformanceMember member) {
		Membership membership = member.getMembership();
		return ParticipantResponse.builder()
				.id(member.getId().toString())
				.performanceId(member.getPerformance().getId().toString())
				.membershipId(membership.getId().toString())
				.displayName(membership.getDisplayName() == null
						? membership.getUser().getName() : membership.getDisplayName())
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
		return value == null ? null : value.atOffset(ZoneOffset.UTC)
				.atZoneSameInstant(java.time.ZoneId.of("Asia/Seoul")).toOffsetDateTime();
	}
}
