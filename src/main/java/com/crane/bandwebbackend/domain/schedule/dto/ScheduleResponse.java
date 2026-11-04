package com.crane.bandwebbackend.domain.schedule.dto;

import com.crane.bandwebbackend.domain.schedule.entity.PracticeSchedule;
import com.crane.bandwebbackend.domain.schedule.entity.ScheduleStatus;
import com.crane.bandwebbackend.domain.schedule.entity.ScheduleType;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.Builder;

/** SCH-01 목록 객체. 연결 팀·곡 ID는 ScheduleTargetReader가 페이지 단위로 공급한다. */
@Builder
public record ScheduleResponse(
		String id,
		String performanceId,
		ScheduleType eventType,
		ScheduleStatus status,
		String title,
		OffsetDateTime startAt,
		OffsetDateTime endAt,
		String location,
		String memo,
		boolean forAllParticipants,
		List<String> teamIds,
		List<String> songIds,
		String performanceTitle,
		String version,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt
) {

	/** 일정 엔티티를 응답 데이터로 변환한다 */
	public static ScheduleResponse from(
		PracticeSchedule schedule,
		List<String> teamIds,
		List<String> songIds
	) {
		if (schedule.getTitle() == null || schedule.getTitle().isBlank()) {
			throw new IllegalStateException("제목 없는 일정은 API 응답 계약을 만족하지 않습니다.");
		}
		return ScheduleResponse.builder()
				.id(schedule.getId().toString())
				.performanceId(schedule.getPerformance().getId().toString())
				.eventType(schedule.getScheduleType())
				.status(schedule.getStatus())
				.title(schedule.getTitle())
				.startAt(kst(schedule.getStartAt()))
				.endAt(kst(schedule.getEndAt()))
				.location(schedule.getLocation())
				.memo(schedule.getMemo())
				.forAllParticipants(schedule.isForAllParticipants())
				.teamIds(teamIds)
				.songIds(songIds)
				.performanceTitle(schedule.getPerformance().getTitle())
				.version(schedule.getVersion().toString())
				.createdAt(kst(schedule.getCreatedAt()))
				.updatedAt(kst(schedule.getUpdatedAt()))
				.build();
	}

	private static OffsetDateTime kst(java.time.LocalDateTime value) {
		return value.atOffset(ZoneOffset.UTC)
				.atZoneSameInstant(java.time.ZoneId.of("Asia/Seoul")).toOffsetDateTime();
	}
}
