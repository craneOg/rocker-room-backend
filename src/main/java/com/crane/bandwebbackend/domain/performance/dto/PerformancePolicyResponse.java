package com.crane.bandwebbackend.domain.performance.dto;

import com.crane.bandwebbackend.domain.performance.entity.CandidateScope;
import com.crane.bandwebbackend.domain.performance.entity.FeatureState;
import com.crane.bandwebbackend.domain.performance.entity.PerformancePolicy;
import com.crane.bandwebbackend.domain.performance.entity.VoteMethod;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** API-041. DB의 teamSubmission 필드 이름을 공개 계약의 teamApplication으로 변환한다. */
public record PerformancePolicyResponse(
		String performanceId, String performanceVersion,
		FeatureState applicationState, OffsetDateTime applicationDeadline,
		FeatureState candidateState, OffsetDateTime candidateDeadline,
		FeatureState playabilityState, OffsetDateTime playabilityDeadline,
		FeatureState voteState, OffsetDateTime voteDeadline,
		FeatureState teamApplicationState, OffsetDateTime teamApplicationDeadline,
		FeatureState availabilityState, OffsetDateTime availabilityDeadline,
		CandidateScope candidateScope, Integer candidateLimitPerMember, VoteMethod voteMethod,
		Integer voteMaxChoices, boolean playabilityResultPublic, boolean voteResultPublic,
		LocalDate practiceSeasonStart, LocalDate practiceSeasonEnd, Integer maxSongsPerMember,
		OffsetDateTime updatedAt) {
	/** 공연 엔티티를 응답 데이터로 변환한다 */
	public static PerformancePolicyResponse from(PerformancePolicy policy) {
		return new PerformancePolicyResponse(policy.getPerformanceId().toString(),
				policy.getPerformance().getVersion().toString(),
				policy.getApplicationState(), kst(policy.getApplicationDeadline()),
				policy.getCandidateState(), kst(policy.getCandidateDeadline()),
				policy.getPlayabilityState(), kst(policy.getPlayabilityDeadline()),
				policy.getVoteState(), kst(policy.getVoteDeadline()),
				policy.getTeamSubmissionState(), kst(policy.getTeamSubmissionDeadline()),
				policy.getAvailabilityState(), kst(policy.getAvailabilityDeadline()),
				policy.getCandidateScope(), policy.getCandidateLimitPerMember(), policy.getVoteMethod(),
				policy.getVoteMaxChoices(), policy.isPlayabilityResultPublic(), policy.isVoteResultPublic(),
				policy.getPracticeSeasonStart(), policy.getPracticeSeasonEnd(), policy.getMaxSongsPerMember(),
				kst(policy.getUpdatedAt()));
	}

	private static OffsetDateTime kst(LocalDateTime value) {
		return value == null ? null : value.atOffset(ZoneOffset.UTC)
				.atZoneSameInstant(ZoneId.of("Asia/Seoul")).toOffsetDateTime();
	}
}
