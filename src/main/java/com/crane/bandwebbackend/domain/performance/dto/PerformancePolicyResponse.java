package com.crane.bandwebbackend.domain.performance.dto;

import com.crane.bandwebbackend.domain.performance.entity.CandidateScope;
import com.crane.bandwebbackend.domain.performance.entity.FeatureState;
import com.crane.bandwebbackend.domain.performance.entity.PerformancePolicy;
import com.crane.bandwebbackend.domain.performance.entity.VoteMethod;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** API-041. DB의 teamSubmission 필드 이름을 공개 계약의 teamApplication으로 변환한다. */
@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode
public class PerformancePolicyResponse {
    @JsonProperty("performanceId")
    private String performanceId;

    @JsonProperty("performanceVersion")
    private String performanceVersion;

    @JsonProperty("applicationState")
    private FeatureState applicationState;

    @JsonProperty("applicationDeadline")
    private OffsetDateTime applicationDeadline;

    @JsonProperty("candidateState")
    private FeatureState candidateState;

    @JsonProperty("candidateDeadline")
    private OffsetDateTime candidateDeadline;

    @JsonProperty("playabilityState")
    private FeatureState playabilityState;

    @JsonProperty("playabilityDeadline")
    private OffsetDateTime playabilityDeadline;

    @JsonProperty("voteState")
    private FeatureState voteState;

    @JsonProperty("voteDeadline")
    private OffsetDateTime voteDeadline;

    @JsonProperty("teamApplicationState")
    private FeatureState teamApplicationState;

    @JsonProperty("teamApplicationDeadline")
    private OffsetDateTime teamApplicationDeadline;

    @JsonProperty("availabilityState")
    private FeatureState availabilityState;

    @JsonProperty("availabilityDeadline")
    private OffsetDateTime availabilityDeadline;

    @JsonProperty("candidateScope")
    private CandidateScope candidateScope;

    @JsonProperty("candidateLimitPerMember")
    private Integer candidateLimitPerMember;

    @JsonProperty("voteMethod")
    private VoteMethod voteMethod;

    @JsonProperty("voteMaxChoices")
    private Integer voteMaxChoices;

    @JsonProperty("playabilityResultPublic")
    private boolean playabilityResultPublic;

    @JsonProperty("voteResultPublic")
    private boolean voteResultPublic;

    @JsonProperty("practiceSeasonStart")
    private LocalDate practiceSeasonStart;

    @JsonProperty("practiceSeasonEnd")
    private LocalDate practiceSeasonEnd;

    @JsonProperty("maxSongsPerMember")
    private Integer maxSongsPerMember;

    @JsonProperty("updatedAt")
    private OffsetDateTime updatedAt;

    /** 공연 엔티티를 응답 데이터로 변환한다 */
    public static PerformancePolicyResponse from(PerformancePolicy policy) {
        return new PerformancePolicyResponse(
                policy.getPerformanceId().toString(),
                policy.getPerformance().getVersion().toString(),
                policy.getApplicationState(),
                kst(policy.getApplicationDeadline()),
                policy.getCandidateState(),
                kst(policy.getCandidateDeadline()),
                policy.getPlayabilityState(),
                kst(policy.getPlayabilityDeadline()),
                policy.getVoteState(),
                kst(policy.getVoteDeadline()),
                policy.getTeamSubmissionState(),
                kst(policy.getTeamSubmissionDeadline()),
                policy.getAvailabilityState(),
                kst(policy.getAvailabilityDeadline()),
                policy.getCandidateScope(),
                policy.getCandidateLimitPerMember(),
                policy.getVoteMethod(),
                policy.getVoteMaxChoices(),
                policy.isPlayabilityResultPublic(),
                policy.isVoteResultPublic(),
                policy.getPracticeSeasonStart(),
                policy.getPracticeSeasonEnd(),
                policy.getMaxSongsPerMember(),
                kst(policy.getUpdatedAt()));
    }

    private static OffsetDateTime kst(LocalDateTime value) {
        return value == null
                ? null
                : value.atOffset(ZoneOffset.UTC)
                        .atZoneSameInstant(ZoneId.of("Asia/Seoul"))
                        .toOffsetDateTime();
    }
}
