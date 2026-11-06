package com.crane.bandwebbackend.domain.schedule.service;

import com.crane.bandwebbackend.domain.schedule.entity.PracticeSchedule;
import com.crane.bandwebbackend.domain.schedule.repository.PracticeScheduleRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 다른 도메인에서 필요한 일정 조회를 일정 도메인 안에 둔다. */
@Service
@RequiredArgsConstructor
public class ScheduleLookup {
	private final PracticeScheduleRepository repository;

	@Transactional(readOnly = true)
	public List<Long> attendedScheduleIds(Long performanceId, Long memberId) {
		return repository.findAttendedScheduleIds(performanceId, memberId);
	}

	@Transactional(readOnly = true)
	public List<PracticeSchedule> schedulesOfPerformance(Long clubId, Long performanceId) {
		return repository.findByPerformance_IdAndPerformance_Club_Id(performanceId, clubId);
	}
}
