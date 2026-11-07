package com.crane.bandwebbackend.domain.participant.dto;

import com.crane.bandwebbackend.domain.participant.entity.PerformanceMemberSource;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMemberStatus;
import java.time.OffsetDateTime;
import java.util.List;

public record MyParticipationResponse(String id, String performanceId, String membershipId,
									  PerformanceMemberStatus status, PerformanceMemberSource source,
									  OffsetDateTime createdAt, OffsetDateTime updatedAt,
									  List<MyTeam> myTeams, List<MySong> myPlayingSongs,
									  Progress progress) {
	public record MyTeam(String teamId, String name, boolean isLeader) {
	}

	public record MySong(String songId, String title, String partId, String partName,
						 Integer setlistOrder) {
	}

	public record Usage(long used, Integer limit) {
	}

	public record Progress(Usage candidateSongs, Long unansweredPlayabilityCount,
						   Usage votes, Long availabilitySlotCount) {
	}
}
