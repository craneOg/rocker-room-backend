package com.crane.bandwebbackend.domain.schedule.entity;

import com.crane.bandwebbackend.domain.team.entity.Team;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "practice_schedule_team")
@IdClass(PracticeScheduleTeamId.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PracticeScheduleTeam {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "practice_schedule_id")
    private PracticeSchedule practiceSchedule;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private Team team;

    // 공연 경계 전파 컬럼. 복합 FK로 일정·팀이 같은 공연임을 DB가 보장
    @Column(nullable = false, updatable = false)
    private Long performanceId;
}
