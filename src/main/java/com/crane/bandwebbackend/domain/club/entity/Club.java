package com.crane.bandwebbackend.domain.club.entity;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(
        name = "club",
        uniqueConstraints = {
            @UniqueConstraint(name = "uk_club_join_code", columnNames = "join_code"),
            @UniqueConstraint(name = "uk_club_name_key", columnNames = "name_key")
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Club extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    // DB 생성 컬럼: LOWER(공백 제거한 name). 서비스 전체 고유(C-15)
    @Column(insertable = false, updatable = false, length = 100)
    private String nameKey;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ClubType clubType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ClubStatus status;

    // NULL = 코드 가입 미사용. 재발급 = 값 교체. 서버 발급 10자 A-Z2-9(ck_club_join_code_format)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 10)
    private String joinCode;

    // NULL이면 기본 이미지
    @Column(length = 500)
    private String profileImageUrl;

    // #RRGGBB 대문자 16진수(ck_club_theme_color_format). NULL이면 기본 색상
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 7)
    private String themeColor;

    @Column(nullable = false)
    private boolean isSearchable;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    /** API-011: NOT NULL 컬럼(이름·유형·상태·공개 여부·생성자)을 모두 여기서 채운다. */
    public static Club create(
            String name,
            String description,
            ClubType clubType,
            boolean isSearchable,
            String themeColor,
            String joinCode,
            User createdBy) {
        Club club = new Club();
        club.name = name;
        club.description = description;
        club.clubType = clubType;
        club.status = ClubStatus.ACTIVE;
        club.joinCode = joinCode;
        club.themeColor = themeColor;
        club.isSearchable = isSearchable;
        club.createdBy = createdBy;
        return club;
    }

    public void changeProfileImage(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    /** API-016: 이름·설명·공개 여부·유형·색상을 바꾼다(바꾸지 않을 값은 현재 값을 넘긴다). */
    public void changeSettings(
            String name, String description, ClubType clubType, boolean isSearchable, String themeColor) {
        this.name = name;
        this.description = description;
        this.clubType = clubType;
        this.isSearchable = isSearchable;
        this.themeColor = themeColor;
    }

    /** API-018·019: 가입 코드를 교체한다. null이면 코드 가입을 끈다. */
    public void changeJoinCode(String joinCode) {
        this.joinCode = joinCode;
    }

    /** 후임 임원이 없는 동아리를 비활성 상태로 전환한다. */
    public void deactivate() {
        if (status != ClubStatus.ACTIVE) {
            throw new IllegalStateException("활성 동아리만 비활성화할 수 있습니다.");
        }
        status = ClubStatus.INACTIVE;
    }
}
