package com.crane.bandwebbackend.domain.team.repository;

import com.crane.bandwebbackend.domain.team.entity.TeamMember;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {

    boolean existsByTeamIdAndPerformanceMemberId(Long teamId, Long performanceMemberId);

    @Query("select tm from TeamMember tm left join fetch tm.part join fetch tm.performanceMember pm"
            + " join fetch pm.membership m join fetch m.user where tm.team.id = :teamId order by tm.id")
    List<TeamMember> findByTeamId(Long teamId);

    @Query("select tm from TeamMember tm left join fetch tm.part join fetch tm.performanceMember pm"
            + " join fetch pm.membership m join fetch m.user where tm.performanceId = :performanceId order by tm.id")
    List<TeamMember> findByPerformanceId(Long performanceId);
}
