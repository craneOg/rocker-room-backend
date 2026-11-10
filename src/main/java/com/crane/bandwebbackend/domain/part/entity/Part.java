package com.crane.bandwebbackend.domain.part.entity;

import com.crane.bandwebbackend.domain.club.entity.Club;
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

// 참조하는 행의 동아리와 같은 동아리 파트인지는 서비스 검증(DB 미강제)
@Getter
@Entity
@Table(
        name = "part",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_part_club_name",
                columnNames = {"club_id", "name"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Part extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false)
    private int displayOrder;

    // FALSE = 새 선택 목록에서 숨김(과거 기록은 유지)
    @Column(nullable = false)
    private boolean isActive;
}
