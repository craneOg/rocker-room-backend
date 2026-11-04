package com.crane.bandwebbackend.domain.part.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

import com.crane.bandwebbackend.domain.club.entity.Club;
import com.crane.bandwebbackend.domain.membership.entity.Membership;
import com.crane.bandwebbackend.domain.membership.entity.MemberType;
import com.crane.bandwebbackend.domain.membership.service.MembershipService;
import com.crane.bandwebbackend.domain.part.entity.Part;
import com.crane.bandwebbackend.domain.part.repository.PartRepository;
import com.crane.bandwebbackend.domain.participant.service.ParticipationLookup;
import com.crane.bandwebbackend.global.error.ApiErrorCode;
import com.crane.bandwebbackend.global.error.ApiException;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;
import org.junit.jupiter.api.Test;

class PartServiceTest {
    private final PartRepository repository = mock(PartRepository.class);
    private final MembershipService membershipService = mock(MembershipService.class);
    private final ParticipationLookup participationLookup = mock(ParticipationLookup.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final PartService service = new PartService(repository, membershipService, participationLookup, entityManager);

    @Test
    void part03RejectsStaleRevisionAfterLock() {
        Membership actor = mock(Membership.class);
        Club club = mock(Club.class);
        Part part = mock(Part.class);
        when(membershipService.requireAdmin(1L, 7L)).thenReturn(actor);
        when(actor.getClub()).thenReturn(club);
        when(repository.findWithLockByIdAndClub_Id(2L, 1L)).thenReturn(Optional.of(part));
        when(part.getId()).thenReturn(2L);
        when(part.getUpdatedAt()).thenReturn(LocalDateTime.of(2026, 11, 1, 0, 0));

        assertThatThrownBy(() -> service.update(1L, 2L, 7L, "old-revision", "베이스", null))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(ApiErrorCode.VERSION_CONFLICT));
    }

    @Test
    void part01GuestCannotSeeInactivePartWhenFilterIsOff() {
        Membership guest = mock(Membership.class);
        Part active = mock(Part.class);
        Part inactive = mock(Part.class);
        when(membershipService.requireMember(1L, 7L)).thenReturn(guest);
        when(guest.getMemberType()).thenReturn(MemberType.GUEST);
        when(guest.getId()).thenReturn(3L);
        when(participationLookup.visiblePerformanceIds(3L)).thenReturn(List.of(9L));
        when(repository.findByClub_IdOrderByDisplayOrderAscIdAsc(1L)).thenReturn(List.of(active, inactive));
        when(active.isActive()).thenReturn(true);

        assertThat(service.list(1L, 7L, false)).containsExactly(active);
    }

    @Test
    void part01GuestWithoutParticipationCannotEnumerateClubParts() {
        Membership guest = mock(Membership.class);
        when(membershipService.requireMember(1L, 7L)).thenReturn(guest);
        when(guest.getMemberType()).thenReturn(MemberType.GUEST);
        when(guest.getId()).thenReturn(3L);

        assertThatThrownBy(() -> service.list(1L, 7L, true))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(ApiErrorCode.NOT_FOUND));
        verify(repository, never()).findHistoricallyReferencedPartIds(3L);
    }
}
