package com.crane.bandwebbackend.domain.team.entity;

import com.crane.bandwebbackend.domain.participant.entity.PerformanceMember;
import com.crane.bandwebbackend.domain.performance.entity.Performance;
import com.crane.bandwebbackend.domain.user.entity.User;
import com.crane.bandwebbackend.global.entity.BaseTimeEntity;
import com.crane.bandwebbackend.global.error.BusinessException;
import com.crane.bandwebbackend.global.error.ErrorCode;
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
import java.time.ZoneOffset;
import java.util.Objects;
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

    /** BAND 공연에 사용할 승인된 기본 팀을 생성한다. */
    public static Team createDefault(Performance performance, User creator, LocalDateTime now) {
        if (performance == null || creator == null || now == null) {
            throw new IllegalArgumentException("기본 팀 생성 값이 유효하지 않습니다.");
        }
        Team team = new Team();
        team.performance = performance;
        team.name = "기본 팀";
        team.isDefault = true;
        team.status = TeamStatus.APPROVED;
        team.source = TeamSource.ADMIN;
        team.createdBy = creator;
        team.submittedAt = now;
        team.decidedAt = now;
        return team;
    }

    /** 기본 팀의 팀장을 이미 편성된 공연 참여자로 지정한다. */
    public void assignLeader(PerformanceMember leader) {
        if (leader == null || leader.getPerformance() == null
                || !leader.getPerformance().getId().equals(performance.getId())) {
            throw new IllegalArgumentException("같은 공연의 참여자만 팀장으로 지정할 수 있습니다.");
        }
        this.leader = leader;
    }

    // 임원이 만든 팀은 바로 APPROVED
    public static Team createApproved(Performance performance, String name, TeamSource source, User createdBy) {
        Team team = create(performance, name, source, createdBy);
        team.status = TeamStatus.APPROVED;
        team.decidedAt = now();
        return team;
    }

    // 부원 팀 신청(T-02). 제출 전 DRAFT
    public static Team createDraft(Performance performance, String name, User createdBy) {
        Team team = create(performance, name, TeamSource.SELF, createdBy);
        team.status = TeamStatus.DRAFT;
        return team;
    }

    private static Team create(Performance performance, String name, TeamSource source, User createdBy) {
        Team team = new Team();
        team.performance = performance;
        team.name = name;
        team.source = source;
        team.createdBy = createdBy;
        return team;
    }

    public void submit() {
        requireStatus(TeamStatus.DRAFT);
        this.status = TeamStatus.SUBMITTED;
        this.submittedAt = now();
    }

    // SUBMITTED → DRAFT(제출 취소)
    public void unsubmit() {
        requireStatus(TeamStatus.SUBMITTED);
        this.status = TeamStatus.DRAFT;
        this.submittedAt = null;
    }

    public void approve() {
        requireStatus(TeamStatus.SUBMITTED);
        this.status = TeamStatus.APPROVED;
        this.decidedAt = now();
    }

    // T-05: 반려 사유는 팀원에게 보임
    public void reject(String reason) {
        requireStatus(TeamStatus.SUBMITTED);
        this.status = TeamStatus.REJECTED;
        this.rejectReason = reason;
        this.decidedAt = now();
    }

    // DRAFT·SUBMITTED·APPROVED → WITHDRAWN
    public void withdraw() {
        if (status != TeamStatus.DRAFT && status != TeamStatus.SUBMITTED && status != TeamStatus.APPROVED) {
            throw invalidTransition();
        }
        this.status = TeamStatus.WITHDRAWN;
        this.decidedAt = now();
    }

    // REJECTED·WITHDRAWN → DRAFT(재개)
    public void reopen() {
        if (status != TeamStatus.REJECTED && status != TeamStatus.WITHDRAWN) {
            throw invalidTransition();
        }
        this.status = TeamStatus.DRAFT;
        this.rejectReason = null;
        this.decidedAt = null;
        this.submittedAt = null;
    }

    public void rename(String name) {
        this.name = name;
    }

    // F-03: 공연당 1개. 교체는 서비스가 기존 해제 후 지정
    public void markDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }

    // 팀원인지는 서비스 검증 + fk_team_leader_is_member. NULL = 공석
    public void changeLeader(PerformanceMember leader) {
        if (status == TeamStatus.WITHDRAWN) {
            throw invalidTransition();
        }
        this.leader = leader;
    }

    public boolean isLeader(Long performanceMemberId) {
        return leader != null && performanceMemberId != null && leader.getId().equals(performanceMemberId);
    }

    // 화면이 본 버전과 다르면 409(낙관적 락 대상). 실제 갱신 충돌은 @Version이 한 번 더 막음
    public void verifyVersion(Long expected) {
        if (!Objects.equals(version, expected)) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT, "다른 사람이 먼저 수정했습니다. 새로고침 후 다시 시도해 주세요.");
        }
    }

    private void requireStatus(TeamStatus expected) {
        if (status != expected) {
            throw invalidTransition();
        }
    }

    private BusinessException invalidTransition() {
        return new BusinessException(ErrorCode.STATE_CONFLICT, "INVALID_STATE", "현재 팀 상태(" + status + ")에서는 할 수 없습니다.");
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
