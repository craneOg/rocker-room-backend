package com.crane.bandwebbackend.domain.auth.repository;

import com.crane.bandwebbackend.domain.auth.entity.AccessTokenBlacklist;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccessTokenBlacklistRepository extends JpaRepository<AccessTokenBlacklist, String> {

    // 만료된 행 정리용(배치에서 트랜잭션 안에서 호출)
    long deleteByExpiresAtBefore(LocalDateTime time);
}
