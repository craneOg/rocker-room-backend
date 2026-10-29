package com.crane.bandwebbackend.domain.song.entity;

import com.crane.bandwebbackend.domain.membership.entity.Membership;
import com.crane.bandwebbackend.domain.performance.entity.Performance;
import com.crane.bandwebbackend.domain.team.entity.Team;
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

/**
 * 공연 곡(후보곡·선정곡). 세트리스트 = CONFIRMED이면서 setlistOrder가 있는 곡.
 * CHECK
 * - ck_song_setlist_order: CONFIRMED가 아니면 setlistOrder NULL
 * - ck_song_assigned_team: CONFIRMED가 아니면 assignedTeam NULL
 * - ck_song_withdrawal_type: WITHDRAWN일 때만 withdrawalType 값 보유
 * CONFIRMED에서 벗어나면 assignedTeam·setlistOrder 해제
 */
@Getter
@Entity
@Table(
        name = "song",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_song_id_performance",
                    columnNames = {"id", "performance_id"}),
            @UniqueConstraint(
                    name = "uk_song_setlist_order",
                    columnNames = {"performance_id", "setlist_order"})
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Song extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performance_id", nullable = false)
    private Performance performance;

    // 값 있으면 팀 범위 후보, NULL이면 공연 범위 후보. 생성 후 변경하지 않음
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidate_team_id", updatable = false)
    private Team candidateTeam;

    // 담당 팀. LOCKED 전까지 배정 필요(서비스 검증)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_team_id")
    private Team assignedTeam;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 200)
    private String artist;

    // http/https만(UI-11)
    @Column(length = 500)
    private String referenceUrl;

    // 제안 후 제안자는 수정 불가, 임원만 수정(S-11)
    @Column(length = 500)
    private String memo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SongStatus status;

    // 모든 WITHDRAWN은 1인 제안 한도에서 제외(S-03)
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private WithdrawalType withdrawalType;

    // 같은 동아리인지는 서비스 검증(DB 미강제)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proposed_by_membership_id", nullable = false)
    private Membership proposedBy;

    // 공연 내 UNIQUE. 변경은 한 트랜잭션에서 NULL로 비운 뒤 재부여
    private Integer setlistOrder;

    @Column(length = 500)
    private String decisionNote;

    private LocalDateTime decidedAt;

    @Version
    @Column(nullable = false)
    private Long version;
}
