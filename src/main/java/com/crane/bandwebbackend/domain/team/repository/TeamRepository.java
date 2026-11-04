package com.crane.bandwebbackend.domain.team.repository;

import com.crane.bandwebbackend.domain.team.entity.Team;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TeamRepository extends JpaRepository<Team, Long> {

    Optional<Team> findByIdAndPerformanceId(Long id, Long performanceId);

    @Query("select t from Team t left join fetch t.leader l left join fetch l.membership lm left join fetch lm.user"
            + " where t.performance.id = :performanceId")
    List<Team> findAllWithLeader(Long performanceId);

    // 공연의 모든 상태 팀 이름 기준(T-07). 컬럼 콜레이션이 대소문자 구분 없음
    boolean existsByPerformanceIdAndName(Long performanceId, String name);

    @Query("select t.name from Team t where t.performance.id = :performanceId")
    Set<String> findNamesByPerformanceId(Long performanceId);

    List<Team> findByPerformanceIdAndIsDefaultTrue(Long performanceId);
}
