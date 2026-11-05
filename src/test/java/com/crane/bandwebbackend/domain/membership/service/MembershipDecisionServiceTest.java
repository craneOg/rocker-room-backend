package com.crane.bandwebbackend.domain.membership.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crane.bandwebbackend.domain.club.entity.Club;
import com.crane.bandwebbackend.domain.club.entity.ClubStatus;
import com.crane.bandwebbackend.domain.club.entity.ClubType;
import com.crane.bandwebbackend.domain.membership.entity.Membership;
import com.crane.bandwebbackend.domain.membership.entity.MembershipStatus;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMember;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMemberSource;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMemberStatus;
import com.crane.bandwebbackend.domain.participant.repository.PerformanceMemberRepository;
import com.crane.bandwebbackend.domain.performance.entity.Performance;
import com.crane.bandwebbackend.domain.performance.entity.PerformanceStatus;
import com.crane.bandwebbackend.domain.performance.repository.PerformanceRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MembershipDecisionServiceTest {

	@Test
	void reactivatesEndedParticipationAndLeavesActiveParticipationUnchanged() {
		MembershipService members = mock(MembershipService.class);
		PerformanceRepository performances = mock(PerformanceRepository.class);
		PerformanceMemberRepository participants = mock(PerformanceMemberRepository.class);
		EntityManager entityManager = mock(EntityManager.class);
		Membership member = mock(Membership.class);
		Club club = mock(Club.class);
		when(members.require(10L, 20L)).thenReturn(member);
		when(member.getStatus()).thenReturn(MembershipStatus.PENDING);
		when(member.getClub()).thenReturn(club);
		when(club.getId()).thenReturn(10L);
		when(club.getStatus()).thenReturn(ClubStatus.ACTIVE);
		when(club.getClubType()).thenReturn(ClubType.BAND);
		Performance absent = performance(1L, club);
		Performance rejectedPerformance = performance(2L, club);
		Performance withdrawnPerformance = performance(3L, club);
		Performance active = performance(4L, club);
		when(performances.findByClub_Id(10L))
				.thenReturn(List.of(absent, rejectedPerformance, withdrawnPerformance, active));
		PerformanceMember rejected = mock(PerformanceMember.class);
		PerformanceMember withdrawn = mock(PerformanceMember.class);
		PerformanceMember approved = mock(PerformanceMember.class);
		when(rejected.getStatus()).thenReturn(PerformanceMemberStatus.REJECTED);
		when(withdrawn.getStatus()).thenReturn(PerformanceMemberStatus.WITHDRAWN);
		when(approved.getStatus()).thenReturn(PerformanceMemberStatus.APPROVED);
		when(participants.findByPerformance_IdAndMembership_Id(1L, 20L)).thenReturn(Optional.empty());
		when(participants.findByPerformance_IdAndMembership_Id(2L, 20L)).thenReturn(Optional.of(rejected));
		when(participants.findByPerformance_IdAndMembership_Id(3L, 20L)).thenReturn(Optional.of(withdrawn));
		when(participants.findByPerformance_IdAndMembership_Id(4L, 20L)).thenReturn(Optional.of(approved));
		MembershipDecisionService service = new MembershipDecisionService(
				members, performances, participants, entityManager);

		service.decide(10L, 20L, 30L, MembershipStatus.PENDING, MembershipStatus.ACTIVE, null);

		verify(participants).save(any(PerformanceMember.class));
		verify(rejected).reapply(eq(PerformanceMemberSource.ADMIN), any(LocalDateTime.class));
		verify(withdrawn).reapply(eq(PerformanceMemberSource.ADMIN), any(LocalDateTime.class));
		verify(approved, never()).reapply(any(), any());
	}

	private static Performance performance(Long id, Club club) {
		Performance performance = mock(Performance.class);
		when(performance.getId()).thenReturn(id);
		when(performance.getClub()).thenReturn(club);
		when(performance.getStatus()).thenReturn(PerformanceStatus.ACTIVE);
		return performance;
	}
}
