package com.crane.bandwebbackend.domain.playability.entity;

import com.crane.bandwebbackend.domain.part.entity.Part;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMember;
import com.crane.bandwebbackend.domain.song.entity.Song;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 가불 표기(파트 단위). 최신 결과 1건만 유지(upsert)
@Getter
@Entity
@Table(
        name = "song_playability",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_song_playability",
                columnNames = {"song_id", "performance_member_id", "part_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SongPlayability extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "song_id", nullable = false)
    private Song song;

    // 공연 경계 전파 컬럼
    @Column(nullable = false, updatable = false)
    private Long performanceId;

    // 응답자
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performance_member_id", nullable = false)
    private PerformanceMember performanceMember;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "part_id", nullable = false)
    private Part part;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlayabilityAnswer answer;

    // 불가능·보류 사유
    @Column(length = 500)
    private String reason;

    // 실제 입력자(Club Admin 대리 입력 추적)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submitted_by_user_id", nullable = false)
    private User submittedBy;
}
