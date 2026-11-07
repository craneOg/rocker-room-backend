package com.crane.bandwebbackend.domain.membership.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crane.bandwebbackend.domain.membership.dto.MembershipBulkRequest;
import com.crane.bandwebbackend.domain.membership.entity.Membership;
import com.crane.bandwebbackend.domain.membership.entity.MembershipStatus;
import com.crane.bandwebbackend.domain.part.repository.PartRepository;
import com.crane.bandwebbackend.domain.participant.service.ParticipationLookup;
import com.crane.bandwebbackend.domain.participant.service.PerformanceMemberService;
import com.crane.bandwebbackend.domain.performance.service.PerformanceService;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

class MembershipBulkServiceTest {

	@Test
	void recordsDataIntegrityFailureAndContinuesWithRemainingItems() {
		MembershipService memberships = mock(MembershipService.class);
		MembershipStatusService statusService = mock(MembershipStatusService.class);
		PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
		Membership member = mock(Membership.class);
		when(member.getStatus()).thenReturn(MembershipStatus.ACTIVE);
		when(memberships.require(anyLong(), anyLong())).thenReturn(member);
		when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
		when(statusService.transition(anyLong(), anyLong(), anyLong(), any(), nullable(String.class)))
				.thenAnswer(invocation -> {
					if ((Long) invocation.getArgument(1) == 1L) {
						throw new DataIntegrityViolationException("constraint");
					}
					return member;
				});
		MembershipBulkService service = new MembershipBulkService(
				memberships,
				statusService,
				mock(PartRepository.class),
				mock(PerformanceService.class),
				mock(ParticipationLookup.class),
				mock(PerformanceMemberService.class),
				mock(EntityManager.class),
				mock(com.crane.bandwebbackend.global.audit.AuditRecorder.class),
				transactionManager);
		var params = JsonNodeFactory.instance.objectNode().put("status", "ON_LEAVE");

		var result = service.process(10L, 20L, new MembershipBulkRequest(
				List.of("1", "2"), MembershipBulkRequest.Action.CHANGE_STATUS, params, null));

		assertThat(result.succeeded()).extracting(item -> item.membershipId()).containsExactly("2");
		assertThat(result.failed())
				.extracting(item -> item.membershipId() + ":" + item.reason())
				.containsExactly("1:CONFLICT");
		assertThat(result.summary().requested()).isEqualTo(2);
		assertThat(result.summary().succeeded()).isEqualTo(1);
		assertThat(result.summary().failed()).isEqualTo(1);
		verify(statusService, times(2))
				.transition(anyLong(), anyLong(), anyLong(), any(), nullable(String.class));
	}
}
