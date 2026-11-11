package com.crane.bandwebbackend.domain.participant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crane.bandwebbackend.domain.club.entity.Club;
import com.crane.bandwebbackend.domain.membership.entity.MemberType;
import com.crane.bandwebbackend.domain.membership.entity.Membership;
import com.crane.bandwebbackend.domain.membership.entity.MembershipStatus;
import com.crane.bandwebbackend.domain.membership.service.MembershipService;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMember;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMemberSource;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMemberStatus;
import com.crane.bandwebbackend.domain.participant.repository.PerformanceMemberRepository;
import com.crane.bandwebbackend.domain.performance.entity.FeatureState;
import com.crane.bandwebbackend.domain.performance.entity.Performance;
import com.crane.bandwebbackend.domain.performance.entity.PerformancePolicy;
import com.crane.bandwebbackend.domain.performance.entity.PerformanceStatus;
import com.crane.bandwebbackend.domain.performance.service.PerformanceService;
import com.crane.bandwebbackend.global.error.BusinessException;
import com.crane.bandwebbackend.global.error.ErrorCode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PerformanceMemberServiceTest {
    private final PerformanceMemberRepository repository = mock(PerformanceMemberRepository.class);
    private final MembershipService membershipService = mock(MembershipService.class);
    private final PerformanceService performanceService = mock(PerformanceService.class);
    private final PerformanceMemberService service =
            new PerformanceMemberService(repository, membershipService, performanceService);
    private final Membership actor = mock(Membership.class);
    private final Performance performance = mock(Performance.class);
    private final PerformancePolicy policy = mock(PerformancePolicy.class);

    @BeforeEach
    void prepareApplication() {
        when(membershipService.requireMember(1L, 7L)).thenReturn(actor);
        when(performanceService.require(1L, 2L)).thenReturn(performance);
        when(performance.getStatus()).thenReturn(PerformanceStatus.ACTIVE);
        when(performanceService.policy(1L, 2L, 7L)).thenReturn(policy);
        when(policy.getApplicationState()).thenReturn(FeatureState.OPEN);
        when(actor.getMemberType()).thenReturn(MemberType.REGULAR);
        when(actor.getStatus()).thenReturn(MembershipStatus.ACTIVE);
        when(actor.getId()).thenReturn(3L);
    }

    @Test
    void applicationAfterDeadlineIsRejected() {
        when(policy.getApplicationDeadline())
                .thenReturn(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1));

        assertThatThrownBy(() -> service.register(1L, 2L, 7L, PerformanceMemberSource.APPLICATION, null))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.STATE_CONFLICT));
    }

    @Test
    void guestCannotApplyEvenWhenApplicationIsOpen() {
        when(actor.getMemberType()).thenReturn(MemberType.GUEST);

        assertThatThrownBy(() -> service.register(1L, 2L, 7L, PerformanceMemberSource.APPLICATION, null))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void existingApplicationIsNotDuplicated() {
        PerformanceMember existing = mock(PerformanceMember.class);
        when(repository.findByPerformance_IdAndMembership_Id(2L, 3L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.register(1L, 2L, 7L, PerformanceMemberSource.APPLICATION, null))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE));
        verify(repository).findByPerformance_IdAndMembership_Id(2L, 3L);
    }

    @Test
    void par03ApplicationCreatesAppliedRowForAuthenticatedMember() {
        Club club = mock(Club.class);
        when(club.getId()).thenReturn(1L);
        when(performance.getClub()).thenReturn(club);
        when(actor.getClub()).thenReturn(club);
        when(repository.saveAndFlush(any(PerformanceMember.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PerformanceMember result = service.register(1L, 2L, 7L, PerformanceMemberSource.APPLICATION, null);

        assertThat(result.getMembership()).isSameAs(actor);
        assertThat(result.getStatus()).isEqualTo(PerformanceMemberStatus.APPLIED);
        assertThat(result.getSource()).isEqualTo(PerformanceMemberSource.APPLICATION);
        verify(repository).findByPerformance_IdAndMembership_Id(2L, 3L);
    }

    @Test
    void par03AdminUsesAuthorizedTargetMembershipInSameClub() {
        Club club = mock(Club.class);
        Membership target = mock(Membership.class);
        when(club.getId()).thenReturn(1L);
        when(performance.getClub()).thenReturn(club);
        when(target.getClub()).thenReturn(club);
        when(target.getId()).thenReturn(8L);
        when(target.getStatus()).thenReturn(MembershipStatus.GRADUATED);
        when(performance.getStatus()).thenReturn(PerformanceStatus.DRAFT);
        when(membershipService.require(club.getId(), 8L)).thenReturn(target);
        when(repository.saveAndFlush(any(PerformanceMember.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PerformanceMember result = service.register(1L, 2L, 7L, PerformanceMemberSource.ADMIN, 8L);

        verify(membershipService).requireAdmin(1L, 7L);
        verify(membershipService).require(1L, 8L);
        assertThat(result.getMembership()).isSameAs(target);
        assertThat(result.getStatus()).isEqualTo(PerformanceMemberStatus.APPROVED);
    }
}
