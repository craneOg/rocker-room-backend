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
}
