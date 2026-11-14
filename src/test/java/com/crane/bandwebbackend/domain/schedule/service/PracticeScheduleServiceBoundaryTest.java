package com.crane.bandwebbackend.domain.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crane.bandwebbackend.domain.membership.entity.MemberType;
import com.crane.bandwebbackend.domain.membership.entity.Membership;
import com.crane.bandwebbackend.domain.membership.service.MembershipService;
import com.crane.bandwebbackend.domain.participant.service.ParticipationLookup;
import com.crane.bandwebbackend.domain.performance.service.PerformanceService;
import com.crane.bandwebbackend.domain.schedule.repository.PracticeScheduleRepository;
import com.crane.bandwebbackend.domain.schedule.repository.PracticeScheduleSongRepository;
import com.crane.bandwebbackend.domain.schedule.repository.PracticeScheduleTeamRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

class PracticeScheduleServiceBoundaryTest {
    private final PracticeScheduleRepository repository = mock(PracticeScheduleRepository.class);
    private final MembershipService membershipService = mock(MembershipService.class);
    private final PerformanceService performanceService = mock(PerformanceService.class);
    private final ParticipationLookup participationLookup = mock(ParticipationLookup.class);
    private final PracticeScheduleTeamRepository teamRepository = mock(PracticeScheduleTeamRepository.class);
    private final PracticeScheduleSongRepository songRepository = mock(PracticeScheduleSongRepository.class);
    private final PracticeScheduleService service = new PracticeScheduleService(
            repository, membershipService, performanceService, participationLookup, teamRepository, songRepository);

    @Test
    void sch01GuestWithoutApprovedParticipationGetsEmptyPageWithoutAudienceQuery() {
        Membership guest = mock(Membership.class);
        when(membershipService.requireMember(1L, 7L)).thenReturn(guest);
        when(guest.getMemberType()).thenReturn(MemberType.GUEST);
        when(guest.getId()).thenReturn(3L);
        when(participationLookup.approvedMemberId(2L, 3L)).thenReturn(Optional.empty());
        var pageable = PageRequest.of(0, 20);

        var result = service.page(
                1L,
                2L,
                7L,
                LocalDateTime.of(2026, 11, 1, 0, 0),
                LocalDateTime.of(2026, 11, 2, 0, 0),
                null,
                null,
                null,
                null,
                pageable);

        assertThat(result.getTotalElements()).isZero();
        verify(performanceService).detail(1L, 2L, 7L);
        verify(repository, never()).findAttendedScheduleIds(any(), any());
        verify(repository, never()).findAll(any(Specification.class), any(PageRequest.class));
    }
}
