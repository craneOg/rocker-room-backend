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
        uniqueConstraints =
                @UniqueConstraint(
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

    public static Part create(Club club, String name, int displayOrder) {
        if (club == null || name == null || name.trim().isEmpty() || name.trim().length() > 50 || displayOrder < 0) {
            throw new IllegalArgumentException("파트 생성 값이 유효하지 않습니다.");
        }
        Part part = new Part();
        part.club = club;
        part.name = name.trim();
        part.displayOrder = displayOrder;
        part.isActive = true;
        return part;
    }

    public void changeDetails(String name, Integer displayOrder) {
        if (name == null && displayOrder == null) {
            throw new IllegalArgumentException("변경할 파트 정보가 없습니다.");
        }
        if (name != null) {
            if (name.trim().isEmpty() || name.trim().length() > 50) {
                throw new IllegalArgumentException("파트 이름이 유효하지 않습니다.");
            }
            this.name = name.trim();
        }
        if (displayOrder != null) {
            if (displayOrder < 0) {
                throw new IllegalArgumentException("파트 순서가 유효하지 않습니다.");
            }
            this.displayOrder = displayOrder;
        }
    }

    public void changeActive(boolean active) {
        this.isActive = active;
    }
}
