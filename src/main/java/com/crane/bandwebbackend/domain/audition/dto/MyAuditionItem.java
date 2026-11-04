package com.crane.bandwebbackend.domain.audition.dto;

import com.crane.bandwebbackend.domain.audition.entity.AuditionEntry;
import com.crane.bandwebbackend.domain.audition.entity.AuditionResult;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import lombok.Builder;

@Builder
public record MyAuditionItem(
		String id,
		String clubId,
		String performanceId,
		String type,
		String songId,
		String songTitle,
		String partId,
		String partName,
		OffsetDateTime scheduledAt,
		AuditionResult result,
		String comment,
		OffsetDateTime judgedAt,
		boolean isLatest
) {

	/** 오디션 엔티티를 응답 데이터로 변환한다 */
	public static MyAuditionItem from(
		AuditionEntry entry,
		boolean latest
	) {
		boolean session = entry.getPerformanceMember() != null;
		return MyAuditionItem.builder()
				.id(entry.getId().toString())
				.clubId(entry.getSong().getPerformance().getClub().getId().toString())
				.performanceId(entry.getPerformanceId().toString())
				.type(session ? "SESSION" : "SONG")
				.songId(entry.getSong().getId().toString())
				.songTitle(entry.getSong().getTitle())
				.partId(entry.getPart() == null ? null : entry.getPart().getId().toString())
				.partName(entry.getPart() == null ? null : entry.getPart().getName())
				.scheduledAt(entry.getPracticeSchedule() == null ? null
						: kst(entry.getPracticeSchedule().getStartAt()))
				.result(entry.getResult())
				.comment(session ? entry.getComment() : null)
				.judgedAt(kst(entry.getReviewedAt()))
				.isLatest(latest).build();
	}

	private static OffsetDateTime kst(LocalDateTime value) {
		return value == null ? null : value.atOffset(ZoneOffset.UTC)
				.atZoneSameInstant(ZoneId.of("Asia/Seoul")).toOffsetDateTime();
	}
}
