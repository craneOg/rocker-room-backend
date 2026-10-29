package com.crane.bandwebbackend.domain.team.entity;

import com.crane.bandwebbackend.domain.participant.entity.PerformanceMember;
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

@Getter
@Entity
@Table(
        name = "team",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_team_id_performance",
                    columnNames = {"id", "performance_id"}),
            @UniqueConstraint(
                    name = "uk_team_performance_name",
                    columnNames = {"performance_id", "name"})
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Team extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performance_id", nullable = false)
    private Performance performance;

    @Column(nullable = false, length = 100)
    private String name;

    // 단일 팀 밴드의 기본 팀. 공연당 1개(서비스 검증)
    @Column(nullable = false)
    private boolean isDefault;

    // fk_team_leader_is_member: (id, leader_member_id) → team_member. 이 팀의 팀원이어야 함
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leader_member_id")
    private PerformanceMember leader;

    // 임원이 만든 팀은 바로 APPROVED
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TeamStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TeamSource source;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdBy;

    private LocalDateTime submittedAt;

    private LocalDateTime decidedAt;

    @Column(length = 500)
    private String rejectReason;

    @Version
    @Column(nullable = false)
    private Long version;
}
