package com.crane.bandwebbackend.domain.club.repository;

import com.crane.bandwebbackend.domain.club.entity.Club;
import com.crane.bandwebbackend.domain.club.entity.ClubStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClubRepository extends JpaRepository<Club, Long> {

    // 공개(isSearchable)이고 ACTIVE인 동아리의 이름 부분 일치 검색. like 와일드카드는 호출 쪽에서 이스케이프한다(\)
    @Query("select c from Club c where c.isSearchable = true and c.status = :status"
            + " and c.name like concat('%', :q, '%') escape '\\'")
    Page<Club> searchByName(@Param("q") String q, @Param("status") ClubStatus status, Pageable pageable);

    // 가입 코드는 서버가 발급한 대문자 코드라 완전 일치로 찾는다(C-08)
    Optional<Club> findByJoinCodeAndStatus(String joinCode, ClubStatus status);

    // C-15: nameKey는 DB 생성 컬럼(LOWER(공백 제거한 name)). 비활성 동아리까지 포함해 비교한다
    boolean existsByNameKey(String nameKey);

    boolean existsByNameKeyAndIdNot(String nameKey, Long id);
}
