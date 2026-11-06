package com.crane.bandwebbackend.domain.user.repository;

import com.crane.bandwebbackend.domain.user.entity.User;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

// 사진 정리 배치가 "아직 쓰이는 사진인지" 확인하는 조회 전용 저장소.
// 현재 프로필(users)뿐 아니라 탈퇴 보관(user_withdrawal_archive)도 참조로 본다: 90일 안에 복구하면 보관한 사진이 되살아난다(U-10)
public interface ProfileImageReferenceRepository extends Repository<User, Long> {

    // 이 저장소 사진으로 보이는 주소 전체. 공개 주소가 바뀌어도 비교할 수 있게 키로 바꿔 쓰도록 주소 그대로 돌려준다
    // ponytail: 사진을 가진 사용자가 수십만 명을 넘으면 키 접미사 비교를 페이지 단위로 나눈다
    @Query(
            value =
                    "SELECT profile_image_url FROM users WHERE profile_image_url LIKE '%/users/%'"
                            + " UNION SELECT profile_image_url FROM user_withdrawal_archive WHERE profile_image_url LIKE '%/users/%'",
            nativeQuery = true)
    List<String> findStoredPhotoUrls();
}
