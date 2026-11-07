package com.crane.bandwebbackend.domain.audition.dto;

import com.crane.bandwebbackend.domain.audition.entity.AuditionEntry;
import com.crane.bandwebbackend.domain.audition.entity.AuditionResult;
import com.crane.bandwebbackend.domain.song.entity.SongStatus;
import java.time.OffsetDateTime;
import lombok.Builder;

@Builder
public record AuditionReviewResponse(
		String id,
		String type,
		String songId,
		SongStatus songStatus,
		String partId,
		String partName,
		String applicantPerformanceMemberId,
		String applicantName,
		AuditionResult result,
		String comment,
		String judgedBy,
		String judgedByName,
		OffsetDateTime judgedAt,
		String revision
) {

	/** 오디션 엔티티를 응답 데이터로 변환한다 */
	public static AuditionReviewResponse from(AuditionEntry entry) {
		AuditionItemResponse item = AuditionItemResponse.from(entry);
		return AuditionReviewResponse.builder()
				.id(item.id()).type(item.type()).songId(item.songId())
				.songStatus(item.songStatus()).partId(item.partId())
				.partName(item.partName())
				.applicantPerformanceMemberId(item.applicantPerformanceMemberId())
				.applicantName(item.applicantName())
				.result(item.result()).comment(item.comment())
				.judgedBy(item.judgedBy()).judgedByName(item.judgedByName())
				.judgedAt(item.judgedAt()).revision(item.revision()).build();
	}
}
