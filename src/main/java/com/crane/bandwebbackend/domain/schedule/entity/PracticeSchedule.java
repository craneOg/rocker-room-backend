package com.crane.bandwebbackend.domain.schedule.entity;

import com.crane.bandwebbackend.domain.performance.entity.Performance;
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
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 참석자 = 연결 팀의 팀원 ∪ 연결 곡의 연주자(객원 포함), forAllParticipants면 승인된 참여자 전원
@Getter
@Entity
@Table(
        name = "practice_schedule",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_practice_schedule_id_performance",
                columnNames = {"id", "performance_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PracticeSchedule extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performance_id", nullable = false)
    private Performance performance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ScheduleType scheduleType;

    // TENTATIVE는 임원만 열람
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ScheduleStatus status;

    @Column(length = 100)
    private String title;

    // ck_practice_schedule_range: startAt < endAt
    @Column(nullable = false)
    private LocalDateTime startAt;

    @Column(nullable = false)
    private LocalDateTime endAt;

    @Column(length = 200)
    private String location;

    @Column(nullable = false)
    private boolean forAllParticipants;

    @Column(length = 500)
    private String memo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdBy;

    @Version
    @Column(nullable = false)
    private Long version;
}
