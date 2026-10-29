package com.crane.bandwebbackend.domain.team.entity;

import com.crane.bandwebbackend.domain.part.entity.Part;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMember;
import com.crane.bandwebbackend.global.entity.BaseCreatedTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 팀장인 팀원 행은 삭제 불가(fk_team_leader_is_member) → 팀장 교체 후 삭제
@Getter
@Entity
@Table(
        name = "team_member",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_team_member",
                columnNames = {"team_id", "performance_member_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TeamMember extends BaseCreatedTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    // 공연 경계 전파 컬럼. 복합 FK로 팀·참여자가 같은 공연임을 DB가 보장
    @Column(nullable = false, updatable = false)
    private Long performanceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performance_member_id", nullable = false)
    private PerformanceMember performanceMember;

    // 팀에서의 대표 파트. 곡별 파트는 song_session_member
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "part_id")
    private Part part;
}
