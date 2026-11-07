package com.crane.bandwebbackend.domain.participant.repository;

import com.crane.bandwebbackend.domain.participant.entity.PerformanceMember;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMemberStatus;
import java.util.List;
import java.util.Optional;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

public interface PerformanceMemberRepository extends JpaRepository<PerformanceMember, Long> {
	@EntityGraph(attributePaths = {"performance", "membership", "membership.user"})
	Optional<PerformanceMember> findByIdAndPerformance_IdAndClubId(Long id, Long performanceId, Long clubId);

	@EntityGraph(attributePaths = {"performance", "membership", "membership.user"})
	Optional<PerformanceMember> findByPerformance_IdAndMembership_Id(Long performanceId, Long membershipId);

	@EntityGraph(attributePaths = {"performance", "membership", "membership.user"})
	List<PerformanceMember> findByPerformance_IdAndClubId(Long performanceId, Long clubId);

	@EntityGraph(attributePaths = {"performance", "membership", "membership.user"})
	List<PerformanceMember> findByMembership_IdAndStatusIn(Long membershipId,
															Collection<PerformanceMemberStatus> statuses);

	@EntityGraph(attributePaths = {"performance", "membership", "membership.user"})
	List<PerformanceMember> findByMembership_Id(Long membershipId);
}
