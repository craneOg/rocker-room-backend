package com.crane.bandwebbackend.domain.audition.entity;

import com.crane.bandwebbackend.domain.part.entity.Part;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMember;
import com.crane.bandwebbackend.domain.schedule.entity.PracticeSchedule;
import com.crane.bandwebbackend.domain.song.entity.Song;
import com.crane.bandwebbackend.domain.user.entity.User;
import com.crane.bandwebbackend.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 오디션 지원·심사 기록. 곡 심사 = 지원자 NULL, 세션 심사 = 지원자·파트 지정. 재심은 새 행.
 * 같은 대상의 심사 대기 행은 1개(서비스 검증).
 * CHECK
 * - ck_audition_entry_session_pair: performanceMember와 part는 함께 NULL이거나 함께 값 보유
 * - ck_audition_entry_review: result·reviewedBy·reviewedAt은 함께 NULL이거나 함께 값 보유
 */
@Getter
@Entity
@Table(name = "audition_entry")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditionEntry extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 공연 경계 전파 컬럼. 복합 FK로 곡·지원자·일정이 같은 공연임을 DB가 보장
    @Column(nullable = false, updatable = false)
    private Long performanceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "song_id", nullable = false)
    private Song song;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performance_member_id")
    private PerformanceMember performanceMember;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "part_id")
    private Part part;

    // schedule_type = AUDITION인 일정. 일정 없이도 결과 기록 가능
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "practice_schedule_id")
    private PracticeSchedule practiceSchedule;

    @Column(length = 500)
    private String memo;

    // NULL = 심사 대기. 결과가 곡·연주자를 자동 변경하지 않음
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private AuditionResult result;

    @Column(length = 500)
    private String comment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_user_id")
    private User reviewedBy;

    private LocalDateTime reviewedAt;

    // MVP는 Club Admin만 등록
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdBy;
}
