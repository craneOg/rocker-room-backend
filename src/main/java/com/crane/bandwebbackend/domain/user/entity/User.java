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

    // U-13: false면 본인만 사진을 볼 수 있고 다른 사용자(임원·Super Admin 포함)에게는 null로 응답
    @Column(nullable = false)
    private boolean profileImagePublic = true;

    // U-13: 요청자(viewer)에게 보일 프로필 사진 URL. 비공개면 본인만
    public String profileImageUrlFor(User viewer) {
        boolean self = viewer != null && viewer.getId().equals(id);
        return profileImagePublic || self ? profileImageUrl : null;
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

    // 탈퇴 복구(U-10): 보관한 이름·사진을 되돌리고 토큰 버전을 올린다. handle은 복구하지 않는다
    public void restore(String name, String profileImageUrl) {
        this.name = name;
        this.profileImageUrl = profileImageUrl;
        this.status = UserStatus.ACTIVE;
        this.withdrawnAt = null;
        this.tokenVersion++;
    }

    // 카카오 최초 로그인 시 생성. handle은 온보딩에서 정한다
    public static User createFromProvider(String name, String profileImageUrl) {
        User user = new User();
        user.name = name;
        user.profileImageUrl = profileImageUrl;
        user.status = UserStatus.ACTIVE;
        return user;
    }
}
