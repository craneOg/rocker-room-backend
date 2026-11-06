package com.crane.bandwebbackend.domain.membership.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.crane.bandwebbackend.domain.club.entity.Club;
import com.crane.bandwebbackend.domain.club.entity.ClubStatus;
import com.crane.bandwebbackend.domain.membership.entity.Membership;
import com.crane.bandwebbackend.domain.membership.entity.MembershipRole;
import com.crane.bandwebbackend.domain.membership.entity.MembershipStatus;
import com.crane.bandwebbackend.domain.membership.repository.MembershipRepository;
import com.crane.bandwebbackend.domain.participant.service.ParticipationLookup;
import com.crane.bandwebbackend.global.error.ApiErrorCode;
import com.crane.bandwebbackend.global.error.ApiException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.junit.jupiter.api.Test;

class MembershipStatusServiceTest {
    private final MembershipService members = mock(MembershipService.class);
    private final MembershipRepository repository = mock(MembershipRepository.class);
    private final ParticipationLookup participants = mock(ParticipationLookup.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final MembershipStatusService service =
            new MembershipStatusService(members, repository, participants, entityManager,
                    mock(com.crane.bandwebbackend.global.audit.AuditRecorder.class));

    @Test
    void api161BlocksGraduationOfLastAdminAfterLockingClub() {
        Club club = mock(Club.class);
        Membership member = mock(Membership.class);
        when(entityManager.find(Club.class, 1L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(club);
        when(club.getStatus()).thenReturn(ClubStatus.ACTIVE);
        when(members.require(1L, 2L)).thenReturn(member);
        when(member.getStatus()).thenReturn(MembershipStatus.ACTIVE);
        when(member.getRole()).thenReturn(MembershipRole.CLUB_ADMIN);
        when(member.getId()).thenReturn(2L);
        when(repository.findByClub_Id(1L)).thenReturn(List.of(member));

        assertThatThrownBy(() -> service.transition(1L, 2L, 7L,
                MembershipStatus.GRADUATED, "졸업"))
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.getCode()).isEqualTo(ApiErrorCode.RULE_VIOLATION);
                    assertThat(error.getReason()).isEqualTo("LAST_ADMIN");
                });

        var order = inOrder(entityManager, members);
        order.verify(entityManager).find(Club.class, 1L, LockModeType.PESSIMISTIC_WRITE);
        order.verify(members).requireAdmin(1L, 7L);
        order.verify(members).require(1L, 2L);
    }

    @Test
    void changeTypeLocksClubBeforeCheckingAdminAndTarget() {
        Club club = mock(Club.class);
        Membership member = mock(Membership.class);
        when(entityManager.find(Club.class, 1L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(club);
        when(club.getStatus()).thenReturn(ClubStatus.ACTIVE);
        when(members.require(1L, 2L)).thenReturn(member);

        assertThatThrownBy(() -> service.changeType(1L, 2L, 7L, null, null))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.getCode()).isEqualTo(ApiErrorCode.VALIDATION_ERROR));

        var order = inOrder(entityManager, members);
        order.verify(entityManager).find(Club.class, 1L, LockModeType.PESSIMISTIC_WRITE);
        order.verify(members).requireAdmin(1L, 7L);
        order.verify(members).require(1L, 2L);
    }
}
