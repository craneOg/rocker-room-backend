package com.crane.bandwebbackend.domain.performance.dto;

import com.crane.bandwebbackend.domain.participant.entity.PerformanceMemberStatus;
import com.crane.bandwebbackend.domain.performance.entity.Performance;
import com.crane.bandwebbackend.domain.performance.entity.PerformancePolicy;
import com.crane.bandwebbackend.domain.performance.entity.FeatureState;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/** API-037 목록 항목. */
public record PerformanceListItem(String id, String clubId, String title, LocalDate performDate,
								  String location, String preset, String status, String createdByUserId,
								  String version, OffsetDateTime createdAt, OffsetDateTime updatedAt,
								  PerformanceMemberStatus myParticipationStatus, List<OpenFeature> openFeatures) {
	public record OpenFeature(String feature, OffsetDateTime deadlineAt) {
	}

	/** 공연 엔티티를 응답 데이터로 변환한다 */
	public static PerformanceListItem from(
		Performance performance,
		PerformancePolicy policy,
		PerformanceMemberStatus participation
	) {
		List<OpenFeature> features = new ArrayList<>();
		add(features, "APPLICATION", policy.getApplicationState(), policy.getApplicationDeadline());
		add(features, "CANDIDATE", policy.getCandidateState(), policy.getCandidateDeadline());
		add(features, "PLAYABILITY", policy.getPlayabilityState(), policy.getPlayabilityDeadline());
		add(features, "VOTE", policy.getVoteState(), policy.getVoteDeadline());
		add(features, "TEAM_APPLICATION", policy.getTeamSubmissionState(), policy.getTeamSubmissionDeadline());
		add(features, "AVAILABILITY", policy.getAvailabilityState(), policy.getAvailabilityDeadline());
		return new PerformanceListItem(performance.getId().toString(), performance.getClub().getId().toString(),
				performance.getTitle(), performance.getPerformDate(), performance.getLocation(),
				performance.getPreset().name(), performance.getStatus().name(),
				performance.getCreatedBy().getId().toString(), performance.getVersion().toString(),
				kst(performance.getCreatedAt()), kst(performance.getUpdatedAt()), participation, features);
	}

	private static void add(List<OpenFeature> features, String name, FeatureState state, LocalDateTime deadline) {
		if (state == FeatureState.OPEN && (deadline == null || LocalDateTime.now(ZoneOffset.UTC).isBefore(deadline))) {
			features.add(new OpenFeature(name, kst(deadline)));
		}
	}

	private static OffsetDateTime kst(LocalDateTime value) {
		return value == null ? null : value.atOffset(ZoneOffset.UTC)
				.atZoneSameInstant(ZoneId.of("Asia/Seoul")).toOffsetDateTime();
	}
}
