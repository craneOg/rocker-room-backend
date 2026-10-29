package com.crane.bandwebbackend.domain.song.entity;

import com.crane.bandwebbackend.domain.part.entity.Part;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 곡별 필요 편성(입력 선택). 행이 없으면 편성 미지정
@Getter
@Entity
@Table(name = "song_part_requirement")
@IdClass(SongPartRequirementId.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SongPartRequirement {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "song_id")
    private Song song;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "part_id")
    private Part part;

    // ck_song_part_requirement_count: 0보다 큼
    @Column(nullable = false)
    private int requiredCount;
}
