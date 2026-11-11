package com.crane.bandwebbackend.domain.performance.entity;

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
import jakarta.persistence.Version;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "performance",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_performance_id_club",
                        columnNames = {"id", "club_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Performance extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    @Column(nullable = false, length = 200)
    private String title;

    private LocalDate performDate;

    @Column(length = 200)
    private String location;

    // 생성 시 필수 선택(기본값 없음, UI-5)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PerformancePreset preset;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PerformanceStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdBy;

    @Version
    @Column(nullable = false)
    private Long version;

    /** 공연 준비 상태로 생성한다 */
    public static Performance draft(
            Club club, String title, LocalDate performDate, String location, PerformancePreset preset, User createdBy) {
        if (club == null
                || createdBy == null
                || preset == null
                || title == null
                || title.trim().isEmpty()) {
            throw new IllegalArgumentException("공연 생성 필수 값이 없습니다.");
        }
        Performance performance = new Performance();
        performance.club = club;
        performance.title = title.trim();
        performance.performDate = performDate;
        performance.location = location;
        performance.preset = preset;
        performance.status = PerformanceStatus.DRAFT;
        performance.createdBy = createdBy;
        return performance;
    }

    /** 공연 상세 정보를 수정한다 */
    public void updateDetails(String title, LocalDate performDate, String location) {
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("공연 제목이 비어 있습니다.");
        }
        if (status == PerformanceStatus.FINISHED || status == PerformanceStatus.CANCELED) {
            throw new IllegalStateException("종료되거나 취소된 공연은 수정할 수 없습니다.");
        }
        this.title = title.trim();
        this.performDate = performDate;
        this.location = location;
    }

    /** 공연 지정한 상태로 전환한다 */
    public void transitionTo(PerformanceStatus target) {
        if (target == null || target == status) {
            throw new IllegalArgumentException("변경할 공연 상태가 유효하지 않습니다.");
        }
        boolean allowed = switch (status) {
            case DRAFT -> target == PerformanceStatus.ACTIVE || target == PerformanceStatus.CANCELED;
            case ACTIVE ->
                target == PerformanceStatus.DRAFT
                        || target == PerformanceStatus.LOCKED
                        || target == PerformanceStatus.CANCELED;
            case LOCKED ->
                target == PerformanceStatus.ACTIVE
                        || target == PerformanceStatus.FINISHED
                        || target == PerformanceStatus.CANCELED;
            case FINISHED -> target == PerformanceStatus.LOCKED;
            case CANCELED -> target == PerformanceStatus.DRAFT;
        };
        if (!allowed) {
            throw new IllegalStateException("허용되지 않은 공연 상태 전이입니다.");
        }
        this.status = target;
    }
}
