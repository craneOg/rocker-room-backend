package com.crane.bandwebbackend.domain.participant.dto;

import java.util.List;

public record ParticipantMutationResult(ParticipantResponse resource, List<Object> warnings, Effects effects) {
	public record Effects(List<String> approvedPerformanceMemberIds,
						  List<String> affectedScheduleIds, List<String> affectedUserIds) {
	}
}
