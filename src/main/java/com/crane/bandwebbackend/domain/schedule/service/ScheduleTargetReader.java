package com.crane.bandwebbackend.domain.schedule.service;

import com.crane.bandwebbackend.domain.schedule.repository.PracticeScheduleSongRepository;
import com.crane.bandwebbackend.domain.schedule.repository.PracticeScheduleTeamRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 일정 페이지의 팀·곡 ID를 각각 한 번의 조회로 읽어 N+1을 피한다. */
@Service
@Transactional
@RequiredArgsConstructor
public class ScheduleTargetReader {
	private final PracticeScheduleTeamRepository teamRepository;
	private final PracticeScheduleSongRepository songRepository;

	/** 일정 연결된 팀 ID를 조회한다 */
	@Transactional(readOnly = true)
	public Map<Long, List<String>> teamIds(List<Long> scheduleIds) {
		// 빈 페이지에서는 불필요한 IN 쿼리를 실행하지 않는다.
		if (scheduleIds.isEmpty()) {
			return Map.of();
		}
		// 한 번에 읽은 연결 행을 일정 ID별 팀 ID 목록으로 묶는다.
		return teamRepository.findByPracticeSchedule_IdIn(scheduleIds).stream().collect(Collectors.groupingBy(
				row -> row.getPracticeSchedule().getId(),
				Collectors.mapping(row -> row.getTeam().getId().toString(), Collectors.toList())));
	}

	/** 일정 연결된 곡 ID를 조회한다 */
	@Transactional(readOnly = true)
	public Map<Long, List<String>> songIds(List<Long> scheduleIds) {
		// 빈 페이지에서는 불필요한 IN 쿼리를 실행하지 않는다.
		if (scheduleIds.isEmpty()) {
			return Map.of();
		}
		// 한 번에 읽은 연결 행을 일정 ID별 곡 ID 목록으로 묶는다.
		return songRepository.findByPracticeSchedule_IdIn(scheduleIds).stream().collect(Collectors.groupingBy(
				row -> row.getPracticeSchedule().getId(),
				Collectors.mapping(row -> row.getSong().getId().toString(), Collectors.toList())));
	}
}
