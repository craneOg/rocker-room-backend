package com.crane.bandwebbackend.domain.song.entity;

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

// 곡별 실제 연주자. 객원 여부는 저장하지 않고 조회 시 계산(담당 팀의 team_member가 아니면 객원)
@Getter
@Entity
@Table(
        name = "song_session_member",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_song_session_member",
                columnNames = {"song_id", "performance_member_id", "part_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SongSessionMember extends BaseCreatedTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "song_id", nullable = false)
    private Song song;

    // 공연 경계 전파 컬럼. 복합 FK로 곡·참여자가 같은 공연임을 DB가 보장
    @Column(nullable = false, updatable = false)
    private Long performanceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performance_member_id", nullable = false)
    private PerformanceMember performanceMember;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "part_id", nullable = false)
    private Part part;
}
