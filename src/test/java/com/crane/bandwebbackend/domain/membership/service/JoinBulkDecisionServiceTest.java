package com.crane.bandwebbackend.domain.membership.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crane.bandwebbackend.domain.membership.dto.JoinBulkDecisionRequest;
import com.crane.bandwebbackend.domain.membership.entity.Membership;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

class JoinBulkDecisionServiceTest {

	@Test
	void recordsRuntimeFailuresAndContinuesWithRemainingItems() {
		MembershipService memberships = mock(MembershipService.class);
		MembershipDecisionService decisions = mock(MembershipDecisionService.class);
		PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
		when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
		when(decisions.decide(anyLong(), anyLong(), anyLong(), any(), any(), nullable(String.class)))
				.thenAnswer(invocation -> {
					long membershipId = invocation.getArgument(1);
					if (membershipId == 1L) {
						throw new DataIntegrityViolationException("constraint");
					}
					if (membershipId == 2L) {
						throw new OptimisticLockingFailureException("version");
					}
					if (membershipId == 3L) {
						throw new IllegalStateException("unexpected");
					}
					return mock(Membership.class);
				});
		JoinBulkDecisionService service = new JoinBulkDecisionService(memberships, decisions, transactionManager);

		var result = service.decide(10L, 20L, new JoinBulkDecisionRequest(
				java.util.List.of("1", "2", "3", "4"), JoinBulkDecisionRequest.Decision.APPROVE, null));

		assertThat(result.succeeded()).extracting(item -> item.membershipId()).containsExactly("4");
		assertThat(result.failed())
				.extracting(item -> item.membershipId() + ":" + item.reason())
				.containsExactly("1:CONFLICT", "2:CONFLICT", "3:PROCESSING_FAILED");
		assertThat(result.summary().requested()).isEqualTo(4);
		assertThat(result.summary().succeeded()).isEqualTo(1);
		assertThat(result.summary().failed()).isEqualTo(3);
		verify(decisions, times(4)).decide(anyLong(), anyLong(), anyLong(), any(), any(), nullable(String.class));
	}
}
