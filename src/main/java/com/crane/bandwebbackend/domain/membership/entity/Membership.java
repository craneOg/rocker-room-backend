package com.crane.bandwebbackend.domain.membership.entity;

import com.crane.bandwebbackend.domain.club.entity.Club;
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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 재가입·재초대는 같은 행을 PENDING/INVITED로 되돌림.
 * CHECK
 * - ck_membership_guest_not_admin: GUEST는 CLUB_ADMIN 불가
 * - ck_membership_admin_status: PENDING·INVITED·REJECTED·DECLINED·WITHDRAWN이면 MEMBER만
 * - ck_membership_invited_at: INVITED면 invitedAt 필수
 */
@Getter
@Entity
@Table(
        name = "membership",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_membership_club_user",
                    columnNames = {"club_id", "user_id"}),
            @UniqueConstraint(
                    name = "uk_membership_id_club",
                    columnNames = {"id", "club_id"})
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Membership extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // NULL이면 users.name 사용. 서비스 탈퇴 시 NULL
    @Column(length = 50)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberType memberType;

    // 역할은 토큰이 아니라 요청마다 이 값으로 확인(G-07)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MembershipRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MembershipStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JoinSource joinSource;

    private LocalDateTime approvedAt;

    // 유효기간 1개월(만료 시각은 조회 시 계산)
    private LocalDateTime invitedAt;

	private LocalDateTime leftAt;

    // 이 동아리 홈을 마지막으로 연 시각(HOME-002 최근 본 순). 갱신은 updated_at이 바뀌지 않도록 전용 쿼리로 처리
    private LocalDateTime lastViewedAt;

	/** 소속 초대한다 */
	public static Membership invite(
		Club club,
		User user,
		MemberType type,
		String displayName,
		LocalDateTime invitedAt
	) {
		if (club == null || user == null || type == null || invitedAt == null) {
			throw new IllegalArgumentException("초대 필수 값이 없습니다.");
		}
		Membership membership = new Membership();
		membership.club = club;
		membership.user = user;
		membership.joinSource = JoinSource.ADMIN_ADDED;
		membership.displayName = displayName;
		membership.memberType = type;
		membership.role = MembershipRole.MEMBER;
		membership.status = MembershipStatus.INVITED;
		membership.invitedAt = invitedAt;
		return membership;
	}

	/** 소속 초대를 다시 시작한다 */
	public void restartInvitation(
		MemberType type,
		String displayName,
		LocalDateTime now
	) {
		if (status != MembershipStatus.WITHDRAWN && status != MembershipStatus.REJECTED
				&& status != MembershipStatus.DECLINED && status != MembershipStatus.INVITED) {
			throw new IllegalStateException("기존 소속을 재초대할 수 없습니다.");
		}
		if (type == null || now == null) {
			throw new IllegalArgumentException("재초대 필수 값이 없습니다.");
		}
		this.joinSource = JoinSource.ADMIN_ADDED;
		this.displayName = displayName;
		this.memberType = type;
		this.role = MembershipRole.MEMBER;
		this.status = MembershipStatus.INVITED;
		this.invitedAt = now;
		this.approvedAt = null;
		this.leftAt = null;
	}

	/** 소속 지정한 상태로 전환한다 */
	public void transitionTo(
		MembershipStatus target,
		LocalDateTime now
	) {
		if (target == null || now == null || target == status) {
			throw new IllegalArgumentException("소속 상태 전이가 유효하지 않습니다.");
		}
		boolean allowed = switch (status) {
			case PENDING -> target == MembershipStatus.ACTIVE || target == MembershipStatus.REJECTED
					|| target == MembershipStatus.WITHDRAWN;
			case INVITED -> target == MembershipStatus.ACTIVE || target == MembershipStatus.DECLINED;
			case ACTIVE, ON_LEAVE, GRADUATED -> target == MembershipStatus.WITHDRAWN
					|| (target != MembershipStatus.PENDING && target != MembershipStatus.INVITED
					&& target != MembershipStatus.REJECTED && target != MembershipStatus.DECLINED);
			case REJECTED, DECLINED, WITHDRAWN -> false;
		};
		if (!allowed) {
			throw new IllegalStateException("허용되지 않은 소속 상태 전이입니다.");
		}
		MembershipStatus previous = this.status;
		this.status = target;
		if (target == MembershipStatus.ACTIVE
				&& (previous == MembershipStatus.PENDING || previous == MembershipStatus.INVITED)) {
			this.approvedAt = now;
		}
		if (target == MembershipStatus.GRADUATED) {
			this.role = MembershipRole.MEMBER;
		}
		if (target == MembershipStatus.WITHDRAWN) {
			this.role = MembershipRole.MEMBER;
			this.leftAt = now;
		}
	}

	/** 소속 역할을 변경한다 */
	public void changeRole(MembershipRole role) {
		if (role == null || (role == MembershipRole.CLUB_ADMIN && (memberType != MemberType.REGULAR
				|| (status != MembershipStatus.ACTIVE && status != MembershipStatus.ON_LEAVE)))) {
			throw new IllegalArgumentException("관리자 역할을 지정할 수 없습니다.");
		}
		this.role = role;
	}

	/** 소속 표시 이름을 변경한다 */
	public void changeDisplayName(String displayName) {
		this.displayName = displayName == null ? null : displayName.trim();
	}

	/** 소속 부원 유형을 변경한다 */
	public void changeMemberType(MemberType type) {
		if (type == null || (type == MemberType.GUEST && role == MembershipRole.CLUB_ADMIN)) {
			throw new IllegalArgumentException("소속 유형을 변경할 수 없습니다.");
		}
		this.memberType = type;
	}
}
