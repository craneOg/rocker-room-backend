package com.crane.bandwebbackend.domain.schedule.repository;

import com.crane.bandwebbackend.domain.schedule.entity.PracticeSchedule;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PracticeScheduleRepository
        extends JpaRepository<PracticeSchedule, Long>, JpaSpecificationExecutor<PracticeSchedule> {
    @EntityGraph(attributePaths = "performance")
    Optional<PracticeSchedule> findByIdAndPerformance_IdAndPerformance_Club_Id(
            Long id, Long performanceId, Long clubId);

    @EntityGraph(attributePaths = "performance")
    List<PracticeSchedule> findByPerformance_IdAndPerformance_Club_Id(Long performanceId, Long clubId);

    @EntityGraph(attributePaths = "performance")
    Page<PracticeSchedule> findAll(Specification<PracticeSchedule> spec, Pageable pageable);

    @EntityGraph(attributePaths = "performance")
    List<PracticeSchedule> findByPerformance_Club_IdAndStartAtLessThanAndEndAtGreaterThan(
            Long clubId, LocalDateTime endAt, LocalDateTime startAt);

    @Query(value = """
			SELECT ps.id FROM practice_schedule ps
			WHERE ps.performance_id = :performanceId
			  AND (ps.for_all_participants = TRUE
				OR EXISTS (SELECT 1 FROM practice_schedule_team pst
						   JOIN team_member tm ON tm.team_id = pst.team_id
						   WHERE pst.practice_schedule_id = ps.id AND tm.performance_member_id = :memberId)
				OR EXISTS (SELECT 1 FROM practice_schedule_song pss
						   JOIN song_session_member ssm ON ssm.song_id = pss.song_id
						   WHERE pss.practice_schedule_id = ps.id AND ssm.performance_member_id = :memberId))
			""", nativeQuery = true)
    List<Long> findAttendedScheduleIds(@Param("performanceId") Long performanceId, @Param("memberId") Long memberId);
}
