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

    // 삭제 대상 행이 다음 토큰을 가리키는 FK를 먼저 끊어, 같은 문장에서 어떤 순서로 지워도 FK 위반이 없게 한다
    @Modifying(flushAutomatically = true)
    @Query("update RefreshToken t set t.replacedBy = null where t.replacedBy is not null and t.expiresAt < :cutoff")
    int detachReplacementsBefore(@Param("cutoff") LocalDateTime cutoff);

    // §16: 만료 + 7일이 지난 행(폐기된 토큰 포함)을 물리 삭제한다
    @Modifying(flushAutomatically = true)
    @Query("delete from RefreshToken t where t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") LocalDateTime cutoff);

    @Modifying(flushAutomatically = true)
    @Query("update RefreshToken t set t.replacedBy = :next where t.id = :id")
    int linkReplacement(@Param("id") long id, @Param("next") RefreshToken next);
}
