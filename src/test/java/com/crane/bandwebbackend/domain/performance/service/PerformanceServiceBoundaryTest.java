package com.crane.bandwebbackend.domain.performance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.crane.bandwebbackend.domain.membership.entity.MemberType;
import com.crane.bandwebbackend.domain.membership.entity.Membership;
import com.crane.bandwebbackend.domain.membership.entity.MembershipRole;
import com.crane.bandwebbackend.domain.membership.service.MembershipService;
import com.crane.bandwebbackend.domain.participant.service.ParticipationLookup;
import com.crane.bandwebbackend.domain.performance.entity.Performance;
import com.crane.bandwebbackend.domain.performance.entity.PerformanceStatus;
import com.crane.bandwebbackend.domain.performance.repository.PerformancePolicyRepository;
import com.crane.bandwebbackend.domain.performance.repository.PerformanceRepository;
import com.crane.bandwebbackend.global.error.ErrorCode;
import com.crane.bandwebbackend.global.error.BusinessException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PerformanceServiceBoundaryTest {
    private final PerformanceRepository repository = mock(PerformanceRepository.class);
    private final PerformancePolicyRepository policyRepository = mock(PerformancePolicyRepository.class);
    private final MembershipService membershipService = mock(MembershipService.class);
    private final ParticipationLookup participationLookup = mock(ParticipationLookup.class);
    private final PerformanceService service = new PerformanceService(
            repository, policyRepository, membershipService, participationLookup);

    @Test
    void perf03DoesNotRevealAnotherPerformanceToGuest() {
        Membership guest = mock(Membership.class);
        Performance performance = mock(Performance.class);
        when(membershipService.requireMember(1L, 7L)).thenReturn(guest);
        when(guest.getMemberType()).thenReturn(MemberType.GUEST);
        when(guest.getId()).thenReturn(3L);
        when(performance.getId()).thenReturn(2L);
        when(performance.getStatus()).thenReturn(PerformanceStatus.ACTIVE);
        when(repository.findByIdAndClub_Id(2L, 1L)).thenReturn(Optional.of(performance));

        assertThatThrownBy(() -> service.detail(1L, 2L, 7L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void perf03AllowsAdminToReadDraft() {
        Membership admin = mock(Membership.class);
        Performance draft = mock(Performance.class);
        when(membershipService.requireMember(1L, 7L)).thenReturn(admin);
        when(admin.getRole()).thenReturn(MembershipRole.CLUB_ADMIN);
        when(admin.getMemberType()).thenReturn(MemberType.REGULAR);
        when(draft.getStatus()).thenReturn(PerformanceStatus.DRAFT);
        when(repository.findByIdAndClub_Id(2L, 1L)).thenReturn(Optional.of(draft));

        assertThat(service.detail(1L, 2L, 7L)).isSameAs(draft);
    }
}
