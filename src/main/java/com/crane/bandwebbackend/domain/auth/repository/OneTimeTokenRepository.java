package com.crane.bandwebbackend.domain.auth.repository;

import com.crane.bandwebbackend.domain.auth.entity.OneTimeToken;
import com.crane.bandwebbackend.domain.auth.entity.OneTimeTokenPurpose;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OneTimeTokenRepository extends JpaRepository<OneTimeToken, Long> {

    // 0.5: 영향 행이 1개일 때만 사용에 성공한 것으로 본다(인스턴스 수와 무관하게 한 번만)
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update OneTimeToken t set t.usedAt = :now, t.profileName = null, t.profileImageUrl = null"
            + " where t.tokenHash = :hash and t.purpose = :purpose and t.usedAt is null and t.expiresAt > :now")
    int consume(
            @Param("hash") byte[] hash, @Param("purpose") OneTimeTokenPurpose purpose, @Param("now") LocalDateTime now);

    Optional<OneTimeToken> findByTokenHash(byte[] tokenHash);

    // 탈퇴 계정을 파기하거나 "새로 시작"할 때 그 사용자의 미사용 토큰을 지운다(§16)
    @Modifying(flushAutomatically = true)
    @Query("delete from OneTimeToken t where t.user.id = :userId and t.usedAt is null")
    int deleteUnusedByUser(@Param("userId") long userId);
}
