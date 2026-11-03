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

// 로그인 교환(60초)·복구 선택(10분). 원문은 HttpOnly 쿠키에만, DB에는 해시만. expires_at + 1일 후 배치로 물리 삭제
@Getter
@Entity
@Table(
        name = "one_time_token",
        uniqueConstraints = @UniqueConstraint(name = "uk_one_time_token_hash", columnNames = "token_hash"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OneTimeToken extends BaseCreatedTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 원문의 SHA-256(32바이트). 평문 저장 금지
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(nullable = false, length = 32)
    private byte[] tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OneTimeTokenPurpose purpose;

    // 사용자 없는 용도(OAUTH_STATE 등)는 NULL
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    // 로그인 후 돌아갈 FE 상대 경로(OAUTH_STATE일 때만)
    @Column(length = 200)
    private String returnTo;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    // NULL이면 미사용. 소비는 조건부 UPDATE 영향 행 1개로 판정
    private LocalDateTime usedAt;
}
