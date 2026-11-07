package com.crane.bandwebbackend.domain.performance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crane.bandwebbackend.domain.club.entity.Club;
import com.crane.bandwebbackend.domain.club.entity.ClubStatus;
import com.crane.bandwebbackend.domain.membership.service.MembershipService;
import com.crane.bandwebbackend.domain.performance.entity.Performance;
import com.crane.bandwebbackend.domain.performance.entity.PerformanceStatus;
import com.crane.bandwebbackend.domain.performance.repository.PerformanceRepository;
import com.crane.bandwebbackend.domain.performance.service.LockChecklistEvaluator.ParticipantRow;
import com.crane.bandwebbackend.domain.performance.service.LockChecklistEvaluator.PlayerRow;
import com.crane.bandwebbackend.domain.performance.service.LockChecklistEvaluator.Snapshot;
import com.crane.bandwebbackend.domain.performance.service.LockChecklistEvaluator.SongRow;
import com.crane.bandwebbackend.global.audit.AuditLog;
import com.crane.bandwebbackend.global.error.ErrorCode;
import com.crane.bandwebbackend.global.error.BusinessException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PerformanceLockServiceTest {
    private final MembershipService memberships = mock(MembershipService.class);
    private final PerformanceRepository performances = mock(PerformanceRepository.class);
    private final LockReadinessReader readiness = mock(LockReadinessReader.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final PerformanceLockService service =
            new PerformanceLockService(memberships, performances, readiness, entityManager,
                    mock(com.crane.bandwebbackend.global.audit.AuditRecorder.class));

    @Test
    void confirmationWarningDoesNotChangePerformanceOrSong() {
        Performance performance = activePerformance();
        when(performances.findWithLockByIdAndClub_Id(3L, 1L)).thenReturn(Optional.of(performance));
        Query query = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter("performanceId", 3L)).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());
        when(readiness.read(3L)).thenReturn(new Snapshot(
                List.of(new SongRow(8, 1, 2L, "APPROVED")),
                List.of(new PlayerRow(8, 21, 4, "APPROVED")), List.of(),
                List.of(new ParticipantRow(21, "APPROVED"), new ParticipantRow(22, "APPROVED")),
                List.of(), List.of(), null));

        assertThatThrownBy(() -> service.lock(1L, 3L, 5L, "8", false))
                .isInstanceOfSatisfying(BusinessException.class, error ->
                        assertThat(error.getWarnings()).hasSize(1));
        verify(performance, never()).transitionTo(PerformanceStatus.LOCKED);
        verify(entityManager, never()).persist(org.mockito.ArgumentMatchers.any(AuditLog.class));
    }

    @Test
    void blockingRequirementWinsOverConfirmedFlag() {
        Performance performance = activePerformance();
        when(performances.findWithLockByIdAndClub_Id(3L, 1L)).thenReturn(Optional.of(performance));
        Query query = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter("performanceId", 3L)).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());
        when(readiness.read(3L)).thenReturn(new Snapshot(
                List.of(new SongRow(8, null, null, null)), List.of(), List.of(), List.of(),
                List.of(), List.of(), null));

        assertThatThrownBy(() -> service.lock(1L, 3L, 5L, "8", true))
                .isInstanceOfSatisfying(BusinessException.class, error -> {
                    assertThat(error.getCode()).isEqualTo(ErrorCode.RULE_VIOLATION);
                    assertThat(error.getReason()).isEqualTo("LOCK_REQUIREMENTS_FAILED");
                });
        verify(performance, never()).transitionTo(PerformanceStatus.LOCKED);
    }

    private Performance activePerformance() {
        Performance performance = mock(Performance.class);
        Club club = mock(Club.class);
        when(performance.getClub()).thenReturn(club);
        when(club.getStatus()).thenReturn(ClubStatus.ACTIVE);
        when(performance.getStatus()).thenReturn(PerformanceStatus.ACTIVE);
        when(performance.getVersion()).thenReturn(8L);
        return performance;
    }
}
