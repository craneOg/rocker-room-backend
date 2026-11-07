package com.crane.bandwebbackend.domain.schedule.repository;

import com.crane.bandwebbackend.domain.schedule.entity.PracticeScheduleTeam;
import com.crane.bandwebbackend.domain.schedule.entity.PracticeScheduleTeamId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PracticeScheduleTeamRepository extends JpaRepository<PracticeScheduleTeam, PracticeScheduleTeamId> {
	List<PracticeScheduleTeam> findByPracticeSchedule_IdIn(List<Long> scheduleIds);

	List<PracticeScheduleTeam> findByTeam_Id(Long teamId);

	List<PracticeScheduleTeam> findByPracticeSchedule_Id(Long scheduleId);
}
