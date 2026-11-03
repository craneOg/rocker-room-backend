package com.crane.bandwebbackend.domain.user.repository;

import com.crane.bandwebbackend.domain.user.entity.User;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    // G-11: 하루 1회만 갱신. dayStart는 오늘 0시(KST)를 UTC로 바꾼 값
    @Modifying
    @Query("update User u set u.lastActiveAt = :now"
            + " where u.id = :id and (u.lastActiveAt is null or u.lastActiveAt < :dayStart)")
    int touchLastActive(
            @Param("id") long id, @Param("now") LocalDateTime now, @Param("dayStart") LocalDateTime dayStart);
}
