package com.crane.bandwebbackend.domain.user.repository;

import com.crane.bandwebbackend.domain.user.entity.UserWithdrawalArchive;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserWithdrawalArchiveRepository extends JpaRepository<UserWithdrawalArchive, Long> {

    // U-11: 탈퇴 시각이 기준 이전인(보관 90일이 지난) 사용자 ID
    @Query("select a.userId from UserWithdrawalArchive a where a.withdrawnAt <= :cutoff")
    List<Long> findUserIdsWithdrawnAtOrBefore(@Param("cutoff") LocalDateTime cutoff);
}
