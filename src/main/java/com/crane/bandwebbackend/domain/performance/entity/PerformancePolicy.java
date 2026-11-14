package com.crane.bandwebbackend.domain.performance.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * 공연별 운영 정책(1:1). *State: DISABLED / CLOSED / OPEN, *Deadline: 지정 시 이후 입력 거부(NULL이면 수동 마감).
 * CHECK
 * - candidateLimitPerMember, voteMaxChoices, maxSongsPerMember: NULL 또는 0보다 큼
 * - ck_policy_season: practiceSeasonStart <= practiceSeasonEnd
 */
@Getter
@Entity
@Table(name = "performance_policy")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PerformancePolicy {

    @Id
    private Long performanceId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performance_id")
    private Performance performance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private FeatureState applicationState;

    private LocalDateTime applicationDeadline;

    // DISABLED면 Club Admin만 후보곡 등록
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private FeatureState candidateState;

    private LocalDateTime candidateDeadline;

    // TEAM_MEMBERS면 song.candidate_team_id 필수(서비스 검증)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CandidateScope candidateScope;

    // NULL = 제한 없음
    private Integer candidateLimitPerMember;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private FeatureState playabilityState;

    private LocalDateTime playabilityDeadline;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private FeatureState voteState;

    private LocalDateTime voteDeadline;

    // 투표가 있는 상태에서 변경하면 해당 공연의 기존 투표 전체 삭제(audit_log 기록)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private VoteMethod voteMethod;

    // APPROVAL일 때 1인 최대 선택 곡 수. NULL = 제한 없음
    private Integer voteMaxChoices;

    // FALSE면 부원에게 집계 숫자만(임원은 항상 전체, V-09)
    @Column(nullable = false)
    private boolean playabilityResultPublic;

    @Column(nullable = false)
    private boolean voteResultPublic;

    // 부원 팀 신청(T-02 "정책상 허용" 판단)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private FeatureState teamSubmissionState;

    private LocalDateTime teamSubmissionDeadline;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private FeatureState availabilityState;

    private LocalDateTime availabilityDeadline;

    private LocalDate practiceSeasonStart;

    private LocalDate practiceSeasonEnd;

    // 1인 권장 출연 곡 수. 초과 시 경고만
    private Integer maxSongsPerMember;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /** PERF-002 프리셋 초기값. CUSTOM은 서비스에서 모든 상태 필드 입력을 검증한다. */
    public static PerformancePolicy initial(Performance performance, PerformancePreset preset) {
        if (performance == null || preset == null) {
            throw new IllegalArgumentException("공연 정책 초기값이 유효하지 않습니다.");
        }
        PerformancePolicy policy = new PerformancePolicy();
        policy.performance = performance;
        switch (preset) {
            case TEAM_FIRST ->
                policy.states(
                        FeatureState.OPEN,
                        FeatureState.CLOSED,
                        FeatureState.OPEN,
                        CandidateScope.TEAM_MEMBERS,
                        FeatureState.OPEN,
                        FeatureState.CLOSED,
                        FeatureState.CLOSED);
            case SONG_FIRST ->
                policy.states(
                        FeatureState.OPEN,
                        FeatureState.CLOSED,
                        FeatureState.OPEN,
                        CandidateScope.ALL_PARTICIPANTS,
                        FeatureState.OPEN,
                        FeatureState.OPEN,
                        FeatureState.CLOSED);
            case SONG_FIXED ->
                policy.states(
                        FeatureState.CLOSED,
                        FeatureState.DISABLED,
                        FeatureState.DISABLED,
                        CandidateScope.ALL_PARTICIPANTS,
                        FeatureState.CLOSED,
                        FeatureState.DISABLED,
                        FeatureState.CLOSED);
            case SELF_TEAM ->
                policy.states(
                        FeatureState.OPEN,
                        FeatureState.OPEN,
                        FeatureState.OPEN,
                        CandidateScope.TEAM_MEMBERS,
                        FeatureState.OPEN,
                        FeatureState.CLOSED,
                        FeatureState.CLOSED);
            case BAND ->
                policy.states(
                        FeatureState.CLOSED,
                        FeatureState.DISABLED,
                        FeatureState.OPEN,
                        CandidateScope.ALL_PARTICIPANTS,
                        FeatureState.OPEN,
                        FeatureState.OPEN,
                        FeatureState.CLOSED);
            case CUSTOM ->
                policy.states(
                        FeatureState.DISABLED,
                        FeatureState.DISABLED,
                        FeatureState.DISABLED,
                        CandidateScope.ALL_PARTICIPANTS,
                        FeatureState.DISABLED,
                        FeatureState.DISABLED,
                        FeatureState.DISABLED);
        }
        policy.voteMethod = VoteMethod.APPROVAL;
        policy.playabilityResultPublic = true;
        policy.voteResultPublic = true;
        return policy;
    }

    private void states(
            FeatureState application,
            FeatureState team,
            FeatureState candidate,
            CandidateScope scope,
            FeatureState playability,
            FeatureState vote,
            FeatureState availability) {
        this.applicationState = application;
        this.teamSubmissionState = team;
        this.candidateState = candidate;
        this.candidateScope = scope;
        this.playabilityState = playability;
        this.voteState = vote;
        this.availabilityState = availability;
    }

    /** 필드별 부분 수정은 서비스가 기존값을 보존해 완성한 Values로 전달한다. */
    public void replace(Values values) {
        if (values.getApplicationState() == null
                || values.getCandidateState() == null
                || values.getPlayabilityState() == null
                || values.getVoteState() == null
                || values.getTeamSubmissionState() == null
                || values.getAvailabilityState() == null
                || values.getCandidateScope() == null
                || values.getVoteMethod() == null) {
            throw new IllegalArgumentException("정책 상태가 누락되었습니다.");
        }
        if ((values.getPracticeSeasonStart() == null) != (values.getPracticeSeasonEnd() == null)
                || (values.getPracticeSeasonStart() != null
                        && values.getPracticeSeasonStart().isAfter(values.getPracticeSeasonEnd()))) {
            throw new IllegalArgumentException("합주 시즌 범위가 유효하지 않습니다.");
        }
        if (values.getAvailabilityState() == FeatureState.OPEN && values.getPracticeSeasonStart() == null) {
            throw new IllegalStateException("가능 시간을 열려면 합주 시즌이 필요합니다.");
        }
        if (nonPositive(values.getCandidateLimitPerMember())
                || nonPositive(values.getVoteMaxChoices())
                || nonPositive(values.getMaxSongsPerMember())) {
            throw new IllegalArgumentException("정책 제한 수는 양수여야 합니다.");
        }
        applicationState = values.getApplicationState();
        applicationDeadline = values.getApplicationDeadline();
        candidateState = values.getCandidateState();
        candidateDeadline = values.getCandidateDeadline();
        candidateScope = values.getCandidateScope();
        candidateLimitPerMember = values.getCandidateLimitPerMember();
        playabilityState = values.getPlayabilityState();
        playabilityDeadline = values.getPlayabilityDeadline();
        voteState = values.getVoteState();
        voteDeadline = values.getVoteDeadline();
        voteMethod = values.getVoteMethod();
        voteMaxChoices = values.getVoteMaxChoices();
        playabilityResultPublic = values.isPlayabilityResultPublic();
        voteResultPublic = values.isVoteResultPublic();
        teamSubmissionState = values.getTeamSubmissionState();
        teamSubmissionDeadline = values.getTeamSubmissionDeadline();
        availabilityState = values.getAvailabilityState();
        availabilityDeadline = values.getAvailabilityDeadline();
        practiceSeasonStart = values.getPracticeSeasonStart();
        practiceSeasonEnd = values.getPracticeSeasonEnd();
        maxSongsPerMember = values.getMaxSongsPerMember();
    }

    private static boolean nonPositive(Integer value) {
        return value != null && value <= 0;
    }

    /** 공연 투표 방식을 변경한다 */
    public void changeVoteMethod(VoteMethod method) {
        if (method == null) {
            throw new IllegalArgumentException("투표 방식이 필요합니다.");
        }
        this.voteMethod = method;
    }

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @EqualsAndHashCode
    public static class Values {
        @JsonProperty("applicationState")
        private FeatureState applicationState;

        @JsonProperty("applicationDeadline")
        private LocalDateTime applicationDeadline;

        @JsonProperty("candidateState")
        private FeatureState candidateState;

        @JsonProperty("candidateDeadline")
        private LocalDateTime candidateDeadline;

        @JsonProperty("candidateScope")
        private CandidateScope candidateScope;

        @JsonProperty("candidateLimitPerMember")
        private Integer candidateLimitPerMember;

        @JsonProperty("playabilityState")
        private FeatureState playabilityState;

        @JsonProperty("playabilityDeadline")
        private LocalDateTime playabilityDeadline;

        @JsonProperty("voteState")
        private FeatureState voteState;

        @JsonProperty("voteDeadline")
        private LocalDateTime voteDeadline;

        @JsonProperty("voteMethod")
        private VoteMethod voteMethod;

        @JsonProperty("voteMaxChoices")
        private Integer voteMaxChoices;

        @JsonProperty("playabilityResultPublic")
        private boolean playabilityResultPublic;

        @JsonProperty("voteResultPublic")
        private boolean voteResultPublic;

        @JsonProperty("teamSubmissionState")
        private FeatureState teamSubmissionState;

        @JsonProperty("teamSubmissionDeadline")
        private LocalDateTime teamSubmissionDeadline;

        @JsonProperty("availabilityState")
        private FeatureState availabilityState;

        @JsonProperty("availabilityDeadline")
        private LocalDateTime availabilityDeadline;

        @JsonProperty("practiceSeasonStart")
        private LocalDate practiceSeasonStart;

        @JsonProperty("practiceSeasonEnd")
        private LocalDate practiceSeasonEnd;

        @JsonProperty("maxSongsPerMember")
        private Integer maxSongsPerMember;
    }
}
