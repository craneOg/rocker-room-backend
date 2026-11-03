package com.crane.bandwebbackend.domain.auth.repository;

import com.crane.bandwebbackend.domain.auth.entity.RefreshToken;
import com.crane.bandwebbackend.domain.auth.entity.RevokeReason;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    @Query("select t from RefreshToken t join fetch t.user where t.tokenHash = :hash")
    Optional<RefreshToken> findWithUserByTokenHash(@Param("hash") byte[] hash);

    // 회전 선점: 영향 행이 1개일 때만 이 요청이 토큰을 교체할 수 있다(동시 요청 중 한 번만)
    @Modifying(flushAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now, t.revokeReason = :reason"
            + " where t.id = :id and t.revokedAt is null")
    int revoke(@Param("id") long id, @Param("now") LocalDateTime now, @Param("reason") RevokeReason reason);

    // 같은 로그인 묶음의 미폐기 토큰 전체(재사용 탐지, 현재 기기 로그아웃)
    @Modifying(flushAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now, t.revokeReason = :reason"
            + " where t.familyId = :familyId and t.revokedAt is null")
    int revokeFamily(
            @Param("familyId") String familyId, @Param("now") LocalDateTime now, @Param("reason") RevokeReason reason);

    // 사용자의 미폐기 토큰 전체(모든 기기 로그아웃)
    @Modifying(flushAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now, t.revokeReason = :reason"
            + " where t.user.id = :userId and t.revokedAt is null")
    int revokeAllByUser(
            @Param("userId") long userId, @Param("now") LocalDateTime now, @Param("reason") RevokeReason reason);

    @Modifying(flushAutomatically = true)
    @Query("update RefreshToken t set t.replacedBy = :next where t.id = :id")
    int linkReplacement(@Param("id") long id, @Param("next") RefreshToken next);
}
