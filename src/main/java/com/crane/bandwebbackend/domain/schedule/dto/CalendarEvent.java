package com.crane.bandwebbackend.domain.schedule.dto;

import com.crane.bandwebbackend.domain.performance.entity.Performance;
import com.crane.bandwebbackend.domain.schedule.entity.PracticeSchedule;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** API-114·115 공통 캘린더 항목. */
public record CalendarEvent(String id, String title, OffsetDateTime startAt, OffsetDateTime endAt,
							String eventType, boolean allDay, String status,
							String clubId, String performanceId) {
	/** 일정 연결된 일정을 조회한다 */
	public static CalendarEvent schedule(PracticeSchedule schedule) {
		return new CalendarEvent(schedule.getId().toString(), schedule.getTitle(),
				kst(schedule.getStartAt()), kst(schedule.getEndAt()), schedule.getScheduleType().name(),
				false, schedule.getStatus().name(), schedule.getPerformance().getClub().getId().toString(),
				schedule.getPerformance().getId().toString());
	}

	/** 일정 연결된 공연을 조회한다 */
	public static CalendarEvent performance(Performance performance) {
		return new CalendarEvent(performance.getId().toString(), performance.getTitle(),
				performance.getPerformDate().atTime(LocalTime.MIDNIGHT).atOffset(ZoneOffset.ofHours(9)),
				performance.getPerformDate().atTime(23, 59).atOffset(ZoneOffset.ofHours(9)),
				"PERFORMANCE", true, null, performance.getClub().getId().toString(),
				performance.getId().toString());
	}

	private static OffsetDateTime kst(java.time.LocalDateTime utc) {
		return utc.atOffset(ZoneOffset.UTC).atZoneSameInstant(ZoneId.of("Asia/Seoul")).toOffsetDateTime();
	}
}
