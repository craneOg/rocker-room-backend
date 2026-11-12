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
import com.crane.bandwebbackend.global.error.BusinessException;
import com.crane.bandwebbackend.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

class JoinBulkDecisionServiceTest {

    @Test
    @DisplayName("API-143 INVALID_STATE만 NOT_PENDING으로 바꾸고 CLUB_INACTIVE 등 원래 사유를 보존한다")
    void preservesSpecificConflictReasons() {
        MembershipService memberships = mock(MembershipService.class);
        MembershipDecisionService decisions = mock(MembershipDecisionService.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        when(decisions.decide(anyLong(), anyLong(), anyLong(), any(), any(), nullable(String.class)))
                .thenAnswer(invocation -> {
                    long id = invocation.getArgument(1);
                    if (id == 1L) throw new BusinessException(ErrorCode.STATE_CONFLICT, "INVALID_STATE", "상태");
                    if (id == 2L) throw new BusinessException(ErrorCode.STATE_CONFLICT, "CLUB_INACTIVE", "비활성");
                    if (id == 3L) throw new BusinessException(ErrorCode.STATE_CONFLICT, "상태");
                    if (id == 4L) throw new BusinessException(ErrorCode.RULE_VIOLATION, "OTHER_REASON", "규칙");
                    throw new BusinessException(ErrorCode.NOT_FOUND, "대상 없음");
                });
        var result = new JoinBulkDecisionService(memberships, decisions, transactionManager)
                .decide(
                        1L,
                        1L,
                        new JoinBulkDecisionRequest(
                                java.util.List.of("1", "2", "3", "4", "5"),
                                JoinBulkDecisionRequest.Decision.APPROVE,
                                null));
        assertThat(result.failed())
                .extracting(item -> item.reason())
                .containsExactly("NOT_PENDING", "CLUB_INACTIVE", "STATE_CONFLICT", "OTHER_REASON", "NOT_FOUND");
    }

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

        var result = service.decide(
                10L,
                20L,
                new JoinBulkDecisionRequest(
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
