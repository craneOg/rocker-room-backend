package com.crane.bandwebbackend.global.audit;

import com.crane.bandwebbackend.global.entity.BaseCreatedTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// 대상 삭제와 무관하게 남도록 FK 없음 → 모든 참조를 ID로 보관
@Getter
@Entity
@Table(name = "audit_log")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLog extends BaseCreatedTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long clubId;

    private Long actorUserId;

    // 값 목록 미확정 → String (확정 시 enum 전환)
    @Column(nullable = false, length = 50)
    private String action;

    @Column(nullable = false, length = 30)
    private String targetType;

    private Long targetId;

    @Column(length = 500)
    private String reason;

    // ID와 변경 전·후 값만. 이름 등 개인정보·토큰 저장 금지(U-09)
    @JdbcTypeCode(SqlTypes.JSON)
    private String detail;
}
