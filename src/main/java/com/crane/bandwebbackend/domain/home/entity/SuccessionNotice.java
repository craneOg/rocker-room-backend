package com.crane.bandwebbackend.domain.home.entity;

import com.crane.bandwebbackend.domain.club.entity.Club;
import com.crane.bandwebbackend.domain.team.entity.Team;
import com.crane.bandwebbackend.domain.user.entity.User;
import com.crane.bandwebbackend.global.entity.BaseCreatedTimeEntity;
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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 임원 권한·팀장을 넘겨받은 사람에게 홈 진입 시 한 번 보여 주는 안내(U-05 ②·④, U-06).
 * 행은 지우지 않고 confirmedAt으로만 상태를 바꾼다(확인은 되돌릴 수 없음).
 * CHECK
 * - ck_succession_notice_team: TEAM_LEADER일 때만 team·performanceId 보유
 */
@Getter
@Entity
@Table(name = "succession_notice")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SuccessionNotice extends BaseCreatedTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 안내를 받을 사람(권한·팀장을 넘겨받은 사용자)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SuccessionNoticeType noticeType;

    // 공연 경계 전파 컬럼. 복합 FK로 team과 같은 공연임을 DB가 보장. 팀이 속한 동아리와 club의 일치는 서비스 검증
    @Column(updatable = false)
    private Long performanceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", updatable = false)
    private Team team;

    // NULL이면 미확인
    private LocalDateTime confirmedAt;

    /** U-06: 이전 담당자의 개인정보 없이 후임과 승계 대상만 저장한다. */
    public static SuccessionNotice admin(User successor, Club club) {
        SuccessionNotice notice = new SuccessionNotice();
        notice.user = successor;
        notice.club = club;
        notice.noticeType = SuccessionNoticeType.ADMIN;
        return notice;
    }

    public static SuccessionNotice teamLeader(User successor, Club club, Team team, Long performanceId) {
        SuccessionNotice notice = new SuccessionNotice();
        notice.user = successor;
        notice.club = club;
        notice.noticeType = SuccessionNoticeType.TEAM_LEADER;
        notice.team = team;
        notice.performanceId = performanceId;
        return notice;
    }
}
