package com.crane.bandwebbackend.domain.membership.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import com.crane.bandwebbackend.domain.participant.service.ParticipationLookup;
import com.crane.bandwebbackend.domain.performance.entity.Performance;
import com.crane.bandwebbackend.domain.performance.entity.PerformanceStatus;
import com.crane.bandwebbackend.domain.performance.service.PerformanceService;
import com.crane.bandwebbackend.global.error.ErrorCode;
import com.crane.bandwebbackend.global.error.BusinessException;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class MembershipDecisionServiceTest {
	@Test
	void rejectsDecisionForInactiveClub() {
		MembershipService members = mock(MembershipService.class);
		Membership member = mock(Membership.class);
		Club club = mock(Club.class);
		when(members.require(10L, 20L)).thenReturn(member);
		when(member.getStatus()).thenReturn(MembershipStatus.PENDING);
		when(member.getClub()).thenReturn(club);
		when(club.getStatus()).thenReturn(ClubStatus.INACTIVE);
		MembershipDecisionService service = new MembershipDecisionService(members,
				mock(PerformanceService.class), mock(ParticipationLookup.class),
				mock(EntityManager.class), mock(com.crane.bandwebbackend.global.audit.AuditRecorder.class));

		assertThatThrownBy(() -> service.decide(10L, 20L, 30L, MembershipStatus.PENDING,
				MembershipStatus.ACTIVE, null))
				.isInstanceOfSatisfying(BusinessException.class,
						exception -> org.assertj.core.api.Assertions.assertThat(exception.getCode())
								.isEqualTo(ErrorCode.STATE_CONFLICT));
		verify(member, never()).transitionTo(any(), any());
	}

	@Test
	void reactivatesEndedParticipationAndLeavesActiveParticipationUnchanged() {
		MembershipService members = mock(MembershipService.class);
		PerformanceService performances = mock(PerformanceService.class);
		ParticipationLookup participants = mock(ParticipationLookup.class);
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
		when(performances.list(10L, 30L))
				.thenReturn(List.of(absent, rejectedPerformance, withdrawnPerformance, active));
		MembershipDecisionService service = new MembershipDecisionService(
				members, performances, participants, entityManager,
				mock(com.crane.bandwebbackend.global.audit.AuditRecorder.class));

		service.decide(10L, 20L, 30L, MembershipStatus.PENDING, MembershipStatus.ACTIVE, null);

		for (Performance performance : List.of(absent, rejectedPerformance, withdrawnPerformance, active)) {
			verify(participants).ensureAdminParticipation(eq(performance), eq(member), any(LocalDateTime.class));
		}
	}

	private static Performance performance(Long id, Club club) {
		Performance performance = mock(Performance.class);
		when(performance.getId()).thenReturn(id);
		when(performance.getClub()).thenReturn(club);
		when(performance.getStatus()).thenReturn(PerformanceStatus.ACTIVE);
		return performance;
	}
}
