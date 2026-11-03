package com.crane.bandwebbackend.domain.participant.entity;

import com.crane.bandwebbackend.domain.membership.entity.Membership;
import com.crane.bandwebbackend.domain.performance.entity.Performance;
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
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 공연 참여자. 희망자(APPLIED)도 후보곡 제출·가불 가능
@Getter
@Entity
@Table(
        name = "performance_member",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_performance_member",
                    columnNames = {"performance_id", "membership_id"}),
            @UniqueConstraint(
                    name = "uk_performance_member_id_performance",
                    columnNames = {"id", "performance_id"})
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PerformanceMember extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performance_id", nullable = false)
    private Performance performance;

    // 동아리 경계 전파 컬럼. 복합 FK (performance_id, club_id), (membership_id, club_id)로 같은 동아리를 DB가 보장
    @Column(nullable = false, updatable = false)
    private Long clubId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "membership_id", nullable = false)
    private Membership membership;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PerformanceMemberStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PerformanceMemberSource source;

    private LocalDateTime decidedAt;

    // 팀 신청·임원 지정으로 함께 생기는 참여자(T-03, R-1)
    public static PerformanceMember create(
            Performance performance,
            Membership membership,
            PerformanceMemberStatus status,
            PerformanceMemberSource source) {
        PerformanceMember pm = new PerformanceMember();
        pm.performance = performance;
        pm.clubId = performance.getClub().getId();
        pm.membership = membership;
        pm.status = status;
        pm.source = source;
        if (status != PerformanceMemberStatus.APPLIED) {
            pm.decidedAt = LocalDateTime.now(ZoneOffset.UTC);
        }
        return pm;
    }

    // 팀 승인·임원 팀 편성 시 희망자 승인
    public void approve() {
        if (status == PerformanceMemberStatus.APPLIED) {
            this.status = PerformanceMemberStatus.APPROVED;
            this.decidedAt = LocalDateTime.now(ZoneOffset.UTC);
        }
    }

    // 팀 반려 연쇄(T-04): 그 팀 신청으로만 들어온 희망자
    public void reject() {
        if (status == PerformanceMemberStatus.APPLIED) {
            this.status = PerformanceMemberStatus.REJECTED;
            this.decidedAt = LocalDateTime.now(ZoneOffset.UTC);
        }
    }

    public boolean isActiveParticipant() {
        return status == PerformanceMemberStatus.APPLIED || status == PerformanceMemberStatus.APPROVED;
    }
}
