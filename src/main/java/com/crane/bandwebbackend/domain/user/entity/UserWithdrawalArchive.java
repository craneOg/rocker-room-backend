package com.crane.bandwebbackend.domain.user.entity;

import com.crane.bandwebbackend.global.entity.BaseCreatedTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 탈퇴 후 90일 보관. 복구(U-10) 또는 파기(U-11) 시 행 삭제
@Getter
@Entity
@Table(name = "user_withdrawal_archive")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserWithdrawalArchive extends BaseCreatedTimeEntity {

    @Id
    private Long userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 500)
    private String profileImageUrl;

    // 파기 기준 시각(withdrawn_at + 90일)
    @Column(nullable = false)
    private LocalDateTime withdrawnAt;
}
