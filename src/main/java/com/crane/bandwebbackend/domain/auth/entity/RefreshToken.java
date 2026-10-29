package com.crane.bandwebbackend.domain.auth.entity;

import com.crane.bandwebbackend.domain.user.entity.User;
import com.crane.bandwebbackend.global.entity.BaseCreatedTimeEntity;
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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// 원본은 DB(해시 저장), Caffeine은 캐시. expires_at + 7일 후 배치로 물리 삭제
@Getter
@Entity
@Table(
        name = "refresh_token",
        uniqueConstraints = @UniqueConstraint(name = "uk_refresh_token_hash", columnNames = "token_hash"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken extends BaseCreatedTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 로테이션해도 유지되는 로그인 묶음 UUID
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 36)
    private String familyId;

    // SHA-256 16진수. 평문 저장 금지
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 64)
    private String tokenHash;

    // min(발급 + 30일, familyExpiresAt)
    @Column(nullable = false)
    private LocalDateTime expiresAt;

    // 최초 로그인 + 90일. 로테이션 시 그대로 복사
    @Column(nullable = false)
    private LocalDateTime familyExpiresAt;

    // ck_refresh_token_revoke: revokedAt과 revokeReason은 함께 NULL이거나 함께 값 보유
    private LocalDateTime revokedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private RevokeReason revokeReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replaced_by_id")
    private RefreshToken replacedBy;

    @Column(length = 45)
    private String createdIp;

    @Column(length = 255)
    private String userAgent;
}
