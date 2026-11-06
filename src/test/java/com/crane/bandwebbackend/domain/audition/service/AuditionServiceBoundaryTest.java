package com.crane.bandwebbackend.domain.audition.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.crane.bandwebbackend.domain.audition.entity.AuditionEntry;
import com.crane.bandwebbackend.domain.audition.repository.AuditionEntryRepository;
import com.crane.bandwebbackend.domain.membership.entity.Membership;
import com.crane.bandwebbackend.domain.membership.entity.MembershipRole;
import com.crane.bandwebbackend.domain.membership.service.MembershipService;
import com.crane.bandwebbackend.domain.participant.entity.PerformanceMember;
import com.crane.bandwebbackend.domain.participant.service.ParticipationLookup;
import com.crane.bandwebbackend.domain.performance.service.PerformanceService;
import com.crane.bandwebbackend.global.error.ErrorCode;
import com.crane.bandwebbackend.global.error.BusinessException;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AuditionServiceBoundaryTest {
    private final AuditionEntryRepository repository = mock(AuditionEntryRepository.class);
    private final ParticipationLookup memberRepository = mock(ParticipationLookup.class);
    private final MembershipService membershipService = mock(MembershipService.class);
    private final PerformanceService performanceService = mock(PerformanceService.class);
    private final AuditionService service = new AuditionService(
            repository, memberRepository, membershipService, performanceService, mock(EntityManager.class),
            mock(com.crane.bandwebbackend.global.audit.AuditRecorder.class));

    @Test
    void aud03DoesNotRevealSongReviewOrOtherMembersSessionToOrdinaryMember() {
        Membership actor = mock(Membership.class);
        AuditionEntry songReview = mock(AuditionEntry.class);
        when(membershipService.requireMember(1L, 7L)).thenReturn(actor);
        when(actor.getId()).thenReturn(3L);
        when(repository.findByIdAndPerformanceId(4L, 2L)).thenReturn(Optional.of(songReview));

        assertThatThrownBy(() -> service.detail(1L, 2L, 4L, 7L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(ErrorCode.NOT_FOUND));

        AuditionEntry otherSession = mock(AuditionEntry.class);
        PerformanceMember otherMember = mock(PerformanceMember.class);
        Membership otherMembership = mock(Membership.class);
        when(repository.findByIdAndPerformanceId(5L, 2L)).thenReturn(Optional.of(otherSession));
        when(otherSession.getPerformanceMember()).thenReturn(otherMember);
        when(otherMember.getMembership()).thenReturn(otherMembership);
        when(otherMembership.getId()).thenReturn(99L);

        assertThatThrownBy(() -> service.detail(1L, 2L, 5L, 7L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void aud03AllowsAdminToReadSongReview() {
        Membership admin = mock(Membership.class);
        AuditionEntry songReview = mock(AuditionEntry.class);
        when(membershipService.requireMember(1L, 7L)).thenReturn(admin);
        when(admin.getRole()).thenReturn(MembershipRole.CLUB_ADMIN);
        when(repository.findByIdAndPerformanceId(4L, 2L)).thenReturn(Optional.of(songReview));

        assertThat(service.detail(1L, 2L, 4L, 7L)).isSameAs(songReview);
    }
}
