package com.crane.bandwebbackend.domain.vote.entity;

import com.crane.bandwebbackend.domain.participant.entity.PerformanceMember;
import com.crane.bandwebbackend.domain.song.entity.Song;
import com.crane.bandwebbackend.domain.user.entity.User;
import com.crane.bandwebbackend.global.entity.BaseTimeEntity;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// 일반 투표(가불과 분리 저장). 찬성 취소는 행 삭제
@Getter
@Entity
@Table(
        name = "song_vote",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_song_vote",
                columnNames = {"song_id", "performance_member_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SongVote extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "song_id", nullable = false)
    private Song song;

    // 공연 경계 전파 컬럼. 투표 방식 변경 시 공연 단위 일괄 삭제에도 사용
    @Column(nullable = false, updatable = false)
    private Long performanceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performance_member_id", nullable = false)
    private PerformanceMember performanceMember;

    // ck_song_vote_score: 1~5. APPROVAL = 1 고정, SCORE = 1~5
    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(nullable = false)
    private int score;

    // 실제 입력자(Club Admin 대리 입력 추적)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submitted_by_user_id", nullable = false)
    private User submittedBy;
}
