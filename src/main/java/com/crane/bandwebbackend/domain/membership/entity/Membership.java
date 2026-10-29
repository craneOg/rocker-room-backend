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
}
