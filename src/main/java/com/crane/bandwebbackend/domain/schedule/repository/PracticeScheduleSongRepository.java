package com.crane.bandwebbackend.domain.schedule.repository;

import com.crane.bandwebbackend.domain.schedule.entity.PracticeScheduleSong;
import com.crane.bandwebbackend.domain.schedule.entity.PracticeScheduleSongId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PracticeScheduleSongRepository extends JpaRepository<PracticeScheduleSong, PracticeScheduleSongId> {
	List<PracticeScheduleSong> findByPracticeSchedule_IdIn(List<Long> scheduleIds);

	List<PracticeScheduleSong> findBySong_Id(Long songId);

	List<PracticeScheduleSong> findByPracticeSchedule_Id(Long scheduleId);
}
