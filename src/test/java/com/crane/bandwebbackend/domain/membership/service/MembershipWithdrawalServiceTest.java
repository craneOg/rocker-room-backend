package com.crane.bandwebbackend.domain.membership.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.crane.bandwebbackend.domain.club.entity.Club;
import com.crane.bandwebbackend.domain.club.entity.ClubStatus;
import com.crane.bandwebbackend.domain.membership.entity.Membership;
import com.crane.bandwebbackend.domain.membership.entity.MembershipStatus;
import com.crane.bandwebbackend.domain.membership.repository.MembershipRepository;
import com.crane.bandwebbackend.domain.part.service.MembershipPartReader;
import com.crane.bandwebbackend.domain.participant.service.ParticipationLookup;
import com.crane.bandwebbackend.domain.schedule.service.ScheduleLookup;
import com.crane.bandwebbackend.domain.user.entity.User;
import com.crane.bandwebbackend.global.error.ApiErrorCode;
import com.crane.bandwebbackend.global.error.ApiException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;

class MembershipWithdrawalServiceTest {

	@Test
	void locksClubBeforeCheckingAdminAndTarget() {
		MembershipService memberships = mock(MembershipService.class);
		EntityManager entityManager = mock(EntityManager.class);
		Club club = mock(Club.class);
		Membership target = mock(Membership.class);
		User targetUser = mock(User.class);
		when(entityManager.find(Club.class, 1L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(club);
		when(club.getStatus()).thenReturn(ClubStatus.ACTIVE);
		when(memberships.require(1L, 2L)).thenReturn(target);
		when(target.getUser()).thenReturn(targetUser);
		when(targetUser.getId()).thenReturn(3L);
		when(target.getStatus()).thenReturn(MembershipStatus.PENDING);
		MembershipWithdrawalService service = new MembershipWithdrawalService(
				memberships,
				mock(MembershipRepository.class),
				mock(ParticipationLookup.class),
				mock(ScheduleLookup.class),
				mock(MembershipPartReader.class),
				entityManager, mock(com.crane.bandwebbackend.global.audit.AuditRecorder.class));

		assertThatThrownBy(() -> service.withdraw(1L, 2L, 7L, null))
				.isInstanceOfSatisfying(ApiException.class,
						error -> assertThat(error.getCode()).isEqualTo(ApiErrorCode.STATE_CONFLICT));

		var order = inOrder(entityManager, memberships);
		order.verify(entityManager).find(Club.class, 1L, LockModeType.PESSIMISTIC_WRITE);
		order.verify(memberships).require(1L, 2L);
		order.verify(memberships).requireAdmin(1L, 7L);
		order.verify(entityManager).refresh(target, LockModeType.PESSIMISTIC_WRITE);
	}
}
