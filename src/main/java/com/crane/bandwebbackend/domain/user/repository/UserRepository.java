package com.crane.bandwebbackend.domain.user.repository;

import com.crane.bandwebbackend.domain.user.entity.User;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    // 복구·새로 시작처럼 같은 계정을 동시에 바꾸는 요청을 직렬화한다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") long id);

    // G-11: 하루 1회만 갱신. dayStart는 오늘 0시(KST)를 UTC로 바꾼 값
    @Modifying
    @Query("update User u set u.lastActiveAt = :now"
            + " where u.id = :id and (u.lastActiveAt is null or u.lastActiveAt < :dayStart)")
    int touchLastActive(
            @Param("id") long id, @Param("now") LocalDateTime now, @Param("dayStart") LocalDateTime dayStart);

    // 모든 기기 로그아웃·복구: 모든 요청이 Access Token의 버전과 비교한다(G-02)
    @Modifying(flushAutomatically = true)
    @Query("update User u set u.tokenVersion = u.tokenVersion + 1 where u.id = :id")
    int incrementTokenVersion(@Param("id") long id);
}
