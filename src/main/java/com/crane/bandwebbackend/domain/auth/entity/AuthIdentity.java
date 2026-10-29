package com.crane.bandwebbackend.domain.auth.entity;

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

@Getter
@Entity
@Table(
        name = "auth_identity",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_auth_identity_provider_uid",
                    columnNames = {"provider", "provider_uid"}),
            @UniqueConstraint(
                    name = "uk_auth_identity_user_provider",
                    columnNames = {"user_id", "provider"})
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthIdentity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthProvider provider;

    // 탈퇴 후에도 90일 보관(U-10), 파기(U-11) 시 삭제
    @Column(nullable = false, length = 255)
    private String providerUid;
}
