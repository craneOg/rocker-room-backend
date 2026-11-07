package com.crane.bandwebbackend.domain.schedule.dto;

import java.util.List;

/** API-110~112 공통 변경 결과. */
public record ScheduleMutationResponse(ScheduleResponse resource, List<Object> warnings, Effects effects) {
	public record Effects(List<String> approvedPerformanceMemberIds,
						  List<String> affectedScheduleIds, List<String> affectedUserIds) {
		public static Effects none() {
			return new Effects(List.of(), List.of(), List.of());
		}
	}

	/** 일정 응답 데이터를 생성한다 */
	public static ScheduleMutationResponse of(ScheduleResponse resource) {
		return new ScheduleMutationResponse(resource, List.of(), Effects.none());
	}
}
