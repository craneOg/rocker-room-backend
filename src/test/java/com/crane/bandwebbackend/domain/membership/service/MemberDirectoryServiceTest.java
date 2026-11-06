package com.crane.bandwebbackend.domain.membership.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.crane.bandwebbackend.domain.membership.entity.MemberType;
import com.crane.bandwebbackend.domain.membership.entity.Membership;
import com.crane.bandwebbackend.domain.membership.entity.MembershipRole;
import com.crane.bandwebbackend.domain.membership.repository.MembershipRepository;
import com.crane.bandwebbackend.domain.part.service.MembershipPartReader;
import com.crane.bandwebbackend.global.error.ErrorCode;
import com.crane.bandwebbackend.global.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

class MemberDirectoryServiceTest {

	@Test
	void hidesDirectoryFromGuestMembers() {
		MembershipService memberships = mock(MembershipService.class);
		MembershipRepository repository = mock(MembershipRepository.class);
		MembershipPartReader partReader = mock(MembershipPartReader.class);
		CoreEntityLookupService entityLookup = mock(CoreEntityLookupService.class);
		Membership actor = mock(Membership.class);
		when(memberships.requireMember(1L, 7L)).thenReturn(actor);
		when(actor.getRole()).thenReturn(MembershipRole.MEMBER);
		when(actor.getMemberType()).thenReturn(MemberType.GUEST);
		MemberDirectoryService service = new MemberDirectoryService(
				memberships, repository, partReader, entityLookup);

		assertThatThrownBy(() -> service.list(1L, 7L, null, null, null, null, null, PageRequest.of(0, 20)))
				.isInstanceOfSatisfying(BusinessException.class,
						exception -> assertThat(exception.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
		verifyNoInteractions(repository, partReader);
	}
}
