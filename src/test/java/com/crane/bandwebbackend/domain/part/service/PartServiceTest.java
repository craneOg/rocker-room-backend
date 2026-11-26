package com.crane.bandwebbackend.domain.part.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crane.bandwebbackend.domain.club.entity.Club;
import com.crane.bandwebbackend.domain.membership.entity.MemberType;
import com.crane.bandwebbackend.domain.membership.entity.Membership;
import com.crane.bandwebbackend.domain.membership.entity.MembershipStatus;
import com.crane.bandwebbackend.domain.membership.service.MembershipService;
import com.crane.bandwebbackend.domain.part.entity.Part;
import com.crane.bandwebbackend.domain.part.repository.MembershipPartRepository;
import com.crane.bandwebbackend.domain.part.repository.PartRepository;
import com.crane.bandwebbackend.domain.participant.service.ParticipationLookup;
import com.crane.bandwebbackend.domain.user.entity.User;
import com.crane.bandwebbackend.global.error.BusinessException;
import com.crane.bandwebbackend.global.error.ErrorCode;
import com.crane.bandwebbackend.global.response.RevisionTag;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class PartServiceTest {
    private final PartRepository repository = mock(PartRepository.class);
    private final MembershipService membershipService = mock(MembershipService.class);
    private final ParticipationLookup participationLookup = mock(ParticipationLookup.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final MembershipPartRepository membershipPartRepository = mock(MembershipPartRepository.class);
    private final com.crane.bandwebbackend.domain.membership.service.CoreEntityLookupService entityLookup =
            mock(com.crane.bandwebbackend.domain.membership.service.CoreEntityLookupService.class);
    private final PartService service = new PartService(
            repository,
            membershipService,
            participationLookup,
            entityManager,
            membershipPartRepository,
            entityLookup,
            mock(com.crane.bandwebbackend.global.audit.AuditRecorder.class));

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
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VERSION_CONFLICT));
    }

    @Test
    void part02RefreshesCreatedPartAfterSaving() {
        Membership actor = mock(Membership.class);
        Club club = mock(Club.class);
        when(membershipService.requireAdmin(1L, 7L)).thenReturn(actor);
        when(entityLookup.lockActiveClub(1L)).thenReturn(club);
        when(repository.saveAndFlush(any(Part.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(1L, 7L, "베이스", null);

        var order = inOrder(repository, entityManager);
        order.verify(repository).saveAndFlush(any(Part.class));
        order.verify(entityManager).refresh(any(Part.class));
    }

    @Test
    void part03RefreshesUpdatedPartAfterFlushing() {
        Membership actor = mock(Membership.class);
        Club club = mock(Club.class);
        Part part = mock(Part.class);
        LocalDateTime updatedAt = LocalDateTime.of(2026, 11, 1, 0, 0);
        when(membershipService.requireAdmin(1L, 7L)).thenReturn(actor);
        when(actor.getClub()).thenReturn(club);
        when(repository.findWithLockByIdAndClub_Id(2L, 1L)).thenReturn(Optional.of(part));
        when(part.getId()).thenReturn(2L);
        when(part.getUpdatedAt()).thenReturn(updatedAt);
        when(part.getName()).thenReturn("기타");

        service.update(1L, 2L, 7L, RevisionTag.from("part", 2L, updatedAt), "베이스", null);

        var order = inOrder(repository, entityManager);
        order.verify(repository).flush();
        order.verify(entityManager).refresh(part);
    }

    @Test
    void part04RefreshesChangedStatusAfterFlushing() {
        Membership actor = mock(Membership.class);
        Club club = mock(Club.class);
        Part part = mock(Part.class);
        LocalDateTime updatedAt = LocalDateTime.of(2026, 11, 1, 0, 0);
        when(membershipService.requireAdmin(1L, 7L)).thenReturn(actor);
        when(actor.getClub()).thenReturn(club);
        when(repository.findWithLockByIdAndClub_Id(2L, 1L)).thenReturn(Optional.of(part));
        when(part.getId()).thenReturn(2L);
        when(part.getUpdatedAt()).thenReturn(updatedAt);

        service.setActive(1L, 2L, 7L, RevisionTag.from("part", 2L, updatedAt), true);

        var order = inOrder(repository, entityManager);
        order.verify(repository).flush();
        order.verify(entityManager).refresh(part);
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
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
        verify(repository, never()).findHistoricallyReferencedPartIds(3L);
    }

    @Test
    void api032ConcurrentInsertBecomesVersionConflict() {
        Membership target = mock(Membership.class);
        User user = mock(User.class);
        Part part = mock(Part.class);
        when(membershipService.require(1L, 2L)).thenReturn(target);
        when(target.getUser()).thenReturn(user);
        when(user.getId()).thenReturn(5L);
        when(target.getStatus()).thenReturn(MembershipStatus.ACTIVE);
        when(repository.findByIdAndClub_Id(3L, 1L)).thenReturn(Optional.of(part));
        when(part.isActive()).thenReturn(true);
        when(membershipPartRepository.findByMembership_Id(2L)).thenReturn(List.of());
        // 삭제 뒤 flush는 통과하고, 저장 flush에서 API-142가 먼저 넣은 행과 키가 충돌한다
        doNothing()
                .doThrow(new DataIntegrityViolationException("Duplicate entry"))
                .when(membershipPartRepository)
                .flush();

        assertThatThrownBy(() -> service.replaceMemberParts(1L, 2L, 5L, List.of("3")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VERSION_CONFLICT));
    }
}
