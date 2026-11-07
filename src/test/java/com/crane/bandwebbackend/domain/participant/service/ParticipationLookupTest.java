package com.crane.bandwebbackend.domain.participant.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crane.bandwebbackend.domain.club.entity.Club;
import com.crane.bandwebbackend.domain.membership.entity.Membership;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMember;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMemberSource;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMemberStatus;
import com.crane.bandwebbackend.domain.participant.repository.PerformanceMemberRepository;
import com.crane.bandwebbackend.domain.performance.entity.Performance;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ParticipationLookupTest {
    @Test
    void adminEnrollmentReusesEndedParticipationAndLeavesApprovedUnchanged() {
        PerformanceMemberRepository repository = mock(PerformanceMemberRepository.class);
        ParticipationLookup lookup = new ParticipationLookup(repository);
        Performance performance = mock(Performance.class);
        Membership membership = mock(Membership.class);
        Club club = mock(Club.class);
        when(performance.getId()).thenReturn(2L);
        when(performance.getClub()).thenReturn(club);
        when(membership.getClub()).thenReturn(club);
        when(club.getId()).thenReturn(1L);
        when(membership.getId()).thenReturn(3L);
        LocalDateTime now = LocalDateTime.of(2026, 11, 6, 0, 0);

        when(repository.findByPerformance_IdAndMembership_Id(2L, 3L)).thenReturn(Optional.empty());
        lookup.ensureAdminParticipation(performance, membership, now);
        verify(repository).save(any(PerformanceMember.class));

        PerformanceMember withdrawn = mock(PerformanceMember.class);
        when(withdrawn.getStatus()).thenReturn(PerformanceMemberStatus.WITHDRAWN);
        when(repository.findByPerformance_IdAndMembership_Id(2L, 3L)).thenReturn(Optional.of(withdrawn));
        lookup.ensureAdminParticipation(performance, membership, now);
        verify(withdrawn).reapply(eq(PerformanceMemberSource.ADMIN), eq(now));

        PerformanceMember approved = mock(PerformanceMember.class);
        when(approved.getStatus()).thenReturn(PerformanceMemberStatus.APPROVED);
        when(repository.findByPerformance_IdAndMembership_Id(2L, 3L)).thenReturn(Optional.of(approved));
        lookup.ensureAdminParticipation(performance, membership, now);
        verify(approved, never()).reapply(any(), any());
    }
}
