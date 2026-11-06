package com.crane.bandwebbackend.domain.performance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.crane.bandwebbackend.domain.club.entity.Club;
import com.crane.bandwebbackend.domain.club.entity.ClubStatus;
import com.crane.bandwebbackend.domain.membership.service.MembershipService;
import com.crane.bandwebbackend.domain.performance.entity.Performance;
import com.crane.bandwebbackend.domain.performance.entity.PerformanceStatus;
import com.crane.bandwebbackend.domain.performance.repository.PerformanceRepository;
import com.crane.bandwebbackend.global.error.ApiErrorCode;
import com.crane.bandwebbackend.global.error.ApiException;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PerformanceTransitionServiceTest {
    private final MembershipService members = mock(MembershipService.class);
    private final PerformanceRepository repository = mock(PerformanceRepository.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final PerformanceTransitionService service =
            new PerformanceTransitionService(members, repository, entityManager,
                    mock(com.crane.bandwebbackend.global.audit.AuditRecorder.class));

    @Test
    void api047RejectsStaleVersionBeforeChangingStatus() {
        Performance performance = mock(Performance.class);
        Club club = mock(Club.class);
        when(repository.findByIdAndClub_Id(3L, 1L)).thenReturn(Optional.of(performance));
        when(performance.getClub()).thenReturn(club);
        when(club.getStatus()).thenReturn(ClubStatus.ACTIVE);
        when(performance.getVersion()).thenReturn(7L);

        assertThatThrownBy(() -> service.change(1L, 3L, 5L, null,
                PerformanceStatus.FINISHED, "6", null))
                .isInstanceOfSatisfying(ApiException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ApiErrorCode.VERSION_CONFLICT));
    }

    @Test
    void api049OnlyRestoresCanceledPerformance() {
        Performance performance = mock(Performance.class);
        Club club = mock(Club.class);
        when(repository.findByIdAndClub_Id(3L, 1L)).thenReturn(Optional.of(performance));
        when(performance.getClub()).thenReturn(club);
        when(club.getStatus()).thenReturn(ClubStatus.ACTIVE);
        when(performance.getStatus()).thenReturn(PerformanceStatus.ACTIVE);

        assertThatThrownBy(() -> service.change(1L, 3L, 5L, PerformanceStatus.CANCELED,
                PerformanceStatus.DRAFT, "0", "복구"))
                .isInstanceOfSatisfying(ApiException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ApiErrorCode.STATE_CONFLICT));
    }

    @Test
    void api047DoesNotFinishAnActivePerformance() {
        Performance performance = mock(Performance.class);
        Club club = mock(Club.class);
        when(repository.findByIdAndClub_Id(3L, 1L)).thenReturn(Optional.of(performance));
        when(performance.getClub()).thenReturn(club);
        when(club.getStatus()).thenReturn(ClubStatus.ACTIVE);
        when(performance.getStatus()).thenReturn(PerformanceStatus.ACTIVE);

        assertThatThrownBy(() -> service.change(1L, 3L, 5L, PerformanceStatus.LOCKED,
                PerformanceStatus.FINISHED, "0", null))
                .isInstanceOfSatisfying(ApiException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ApiErrorCode.STATE_CONFLICT));
    }
}
