package com.crane.bandwebbackend.domain.user.repository;

import com.crane.bandwebbackend.domain.user.entity.User;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

// 사진 정리 배치가 "아직 쓰이는 사진인지" 확인하는 조회 전용 저장소.
// 현재 프로필(users)뿐 아니라 탈퇴 보관(user_withdrawal_archive)도 참조로 본다: 90일 안에 복구하면 보관한 사진이 되살아난다(U-10)
public interface ProfileImageReferenceRepository extends Repository<User, Long> {

    @Query(
            value = "SELECT profile_image_url FROM users WHERE profile_image_url IN (:urls)"
                    + " UNION SELECT profile_image_url FROM user_withdrawal_archive WHERE profile_image_url IN (:urls)",
            nativeQuery = true)
    List<String> findReferenced(@Param("urls") Collection<String> urls);
}
