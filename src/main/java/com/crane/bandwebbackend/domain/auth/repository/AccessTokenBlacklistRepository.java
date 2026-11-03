package com.crane.bandwebbackend.domain.auth.repository;

import com.crane.bandwebbackend.domain.auth.entity.AccessTokenBlacklist;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccessTokenBlacklistRepository extends JpaRepository<AccessTokenBlacklist, String> {

    // 만료된 행 정리용(배치에서 트랜잭션 안에서 호출)
    @Modifying
    @Query("delete from AccessTokenBlacklist b where b.expiresAt < :time")
    int deleteByExpiresAtBefore(@Param("time") LocalDateTime time);
}
