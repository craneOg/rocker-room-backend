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

	/** 공연 참여 신청한다 */
	public static PerformanceMember apply(
		Performance performance,
		Membership membership
	) {
		return create(performance, membership, PerformanceMemberSource.APPLICATION,
				PerformanceMemberStatus.APPLIED, null);
	}

	/** 공연 참여 임원 권한으로 추가한다 */
	public static PerformanceMember addByAdmin(
		Performance performance,
		Membership membership,
		LocalDateTime now
	) {
		return create(performance, membership, PerformanceMemberSource.ADMIN,
				PerformanceMemberStatus.APPROVED, now);
	}

	private static PerformanceMember create(Performance performance, Membership membership,
											PerformanceMemberSource source, PerformanceMemberStatus status,
											LocalDateTime decidedAt) {
		if (performance == null || membership == null || performance.getClub() == null
				|| !performance.getClub().getId().equals(membership.getClub().getId())) {
			throw new IllegalArgumentException("공연과 소속의 동아리가 다릅니다.");
		}
		PerformanceMember member = new PerformanceMember();
		member.performance = performance;
		member.clubId = performance.getClub().getId();
		member.membership = membership;
		member.source = source;
		member.status = status;
		member.decidedAt = decidedAt;
		return member;
	}

	/** 공연 참여 신청을 다시 시작한다 */
	public void reapply(
		PerformanceMemberSource source,
		LocalDateTime now
	) {
		if (status != PerformanceMemberStatus.REJECTED && status != PerformanceMemberStatus.WITHDRAWN) {
			throw new IllegalStateException("재신청할 수 없는 참여 상태입니다.");
		}
		if (source != PerformanceMemberSource.APPLICATION && source != PerformanceMemberSource.ADMIN) {
			throw new IllegalArgumentException("재신청 경로가 유효하지 않습니다.");
		}
		this.source = source;
		this.status = source == PerformanceMemberSource.ADMIN
				? PerformanceMemberStatus.APPROVED : PerformanceMemberStatus.APPLIED;
		this.decidedAt = source == PerformanceMemberSource.ADMIN ? now : null;
	}

	/** 공연 참여 지정한 상태로 전환한다 */
	public void transitionTo(
		PerformanceMemberStatus target,
		LocalDateTime now
	) {
		if (target == null || now == null || target == status) {
			throw new IllegalArgumentException("참여 상태 전이가 유효하지 않습니다.");
		}
		boolean allowed = status == PerformanceMemberStatus.APPLIED
				? target == PerformanceMemberStatus.APPROVED || target == PerformanceMemberStatus.REJECTED
						|| target == PerformanceMemberStatus.WITHDRAWN
				: status == PerformanceMemberStatus.APPROVED && target == PerformanceMemberStatus.WITHDRAWN;
		if (!allowed) {
			throw new IllegalStateException("허용되지 않은 참여 상태 전이입니다.");
		}
		this.status = target;
		this.decidedAt = now;
	}
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
