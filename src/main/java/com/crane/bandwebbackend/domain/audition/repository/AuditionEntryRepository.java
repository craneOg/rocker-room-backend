package com.crane.bandwebbackend.domain.audition.repository;

import com.crane.bandwebbackend.domain.audition.entity.AuditionEntry;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

public interface AuditionEntryRepository extends JpaRepository<AuditionEntry, Long> {
	@EntityGraph(attributePaths = {"song", "song.candidateTeam", "performanceMember",
			"performanceMember.membership", "performanceMember.membership.user",
			"part", "practiceSchedule", "reviewedBy"})
	Optional<AuditionEntry> findByIdAndPerformanceId(Long id, Long performanceId);

	@EntityGraph(attributePaths = {"song", "song.candidateTeam", "performanceMember",
			"performanceMember.membership", "performanceMember.membership.user",
			"part", "practiceSchedule", "reviewedBy"})
	List<AuditionEntry> findByPerformanceId(Long performanceId);

	@EntityGraph(attributePaths = {"song", "song.candidateTeam", "performanceMember",
			"performanceMember.membership", "performanceMember.membership.user",
			"part", "practiceSchedule", "reviewedBy"})
	List<AuditionEntry> findByPerformanceMember_Membership_User_Id(Long userId);

	@EntityGraph(attributePaths = {"song", "song.candidateTeam", "performanceMember",
			"performanceMember.membership", "performanceMember.membership.user",
			"part", "practiceSchedule", "reviewedBy"})
	List<AuditionEntry> findBySong_ProposedBy_User_IdAndPerformanceMemberIsNull(Long userId);
}
