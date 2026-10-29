package com.crane.bandwebbackend.domain.performance.entity;

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
}
