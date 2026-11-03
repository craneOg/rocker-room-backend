package com.crane.bandwebbackend.domain.user.entity;

import com.crane.bandwebbackend.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(name = "uk_users_handle", columnNames = "handle"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 탈퇴 시 "탈퇴한 사용자"로 교체(원래 값은 user_withdrawal_archive)
    @Column(nullable = false, length = 50)
    private String name;

    // ck_users_handle_format: NULL 또는 ^[a-z0-9_]{5,30}$. 탈퇴 시 NULL, 복구 시 되돌리지 않음
    @Column(length = 30)
    private String handle;

    @Column(length = 500)
    private String profileImageUrl;

    // FALSE면 본인·Super Admin 외에는 profileImageUrl을 NULL로 반환(임원 포함)
    @Column(nullable = false)
    private boolean profileImagePublic;

    // 요청자(viewer)에게 보일 프로필 사진 URL
    public String profileImageUrlFor(User viewer) {
        boolean self = viewer != null && viewer.getId().equals(id);
        boolean superAdmin = viewer != null && viewer.isSuperAdmin();
        return profileImagePublic || self || superAdmin ? profileImageUrl : null;
    }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @Column(nullable = false)
    private boolean isSuperAdmin;

    // 모든 요청에서 Access Token의 버전과 비교(G-02)
    @Column(nullable = false)
    private int tokenVersion;

    private LocalDateTime lastActiveAt;

    private LocalDateTime withdrawnAt;
}
