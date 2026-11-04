package com.crane.bandwebbackend.domain.participant.service;

import com.crane.bandwebbackend.domain.participant.entity.PerformanceMemberStatus;
import com.crane.bandwebbackend.domain.participant.repository.PerformanceMemberRepository;
import lombok.RequiredArgsConstructor;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class ParticipationLookup {
	private final PerformanceMemberRepository repository;

	/** 공연 참여 객원에게 공개할 수 있는지 확인한다 */
	@Transactional(readOnly = true)
	public boolean isVisibleToGuest(
		Long performanceId,
		Long membershipId
	) {
		// 객원 소속의 공연 참여 행이 현재 유효한 상태인지 확인한다.
		return repository.findByPerformance_IdAndMembership_Id(performanceId, membershipId)
				.map(member -> member.getStatus() == PerformanceMemberStatus.APPLIED
						|| member.getStatus() == PerformanceMemberStatus.APPROVED)
				.orElse(false);
	}

	/** 공연 참여 공개 가능한 공연 ID를 조회한다 */
	@Transactional(readOnly = true)
	public List<Long> visiblePerformanceIds(Long membershipId) {
		// 신청 또는 승인 상태인 참여만 남겨 객원이 접근 가능한 공연 ID로 변환한다.
		return repository.findByMembership_IdAndStatusIn(membershipId,
						List.of(PerformanceMemberStatus.APPLIED, PerformanceMemberStatus.APPROVED))
				.stream().map(member -> member.getPerformance().getId()).toList();
	}

	/** 공연 참여 승인된 참여자 ID를 조회한다 */
	@Transactional(readOnly = true)
	public Optional<Long> approvedMemberId(
		Long performanceId,
		Long membershipId
	) {
		// 승인 상태인 경우에만 이후 팀·곡 조회에 사용할 참여자 ID를 제공한다.
		return repository.findByPerformance_IdAndMembership_Id(performanceId, membershipId)
				.filter(member -> member.getStatus() == PerformanceMemberStatus.APPROVED)
				.map(member -> member.getId());
	}
}
