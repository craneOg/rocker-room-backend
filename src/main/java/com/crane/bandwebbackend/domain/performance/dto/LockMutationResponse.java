package com.crane.bandwebbackend.domain.performance.dto;

import java.util.List;

public record LockMutationResponse(PerformanceResponse resource, List<LockChecklistItem> warnings,
								   Effects effects) {
	public record Effects(List<String> approvedPerformanceMemberIds,
						  List<String> affectedScheduleIds, List<String> affectedUserIds) {
		public static Effects none() { return new Effects(List.of(), List.of(), List.of()); }
	}
}
