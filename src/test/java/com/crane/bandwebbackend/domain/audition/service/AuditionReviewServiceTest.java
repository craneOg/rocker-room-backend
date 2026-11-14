package com.crane.bandwebbackend.domain.audition.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.crane.bandwebbackend.domain.audition.dto.AuditionReviewRequest;
import com.crane.bandwebbackend.domain.audition.entity.AuditionEntry;
import com.crane.bandwebbackend.domain.audition.repository.AuditionEntryRepository;
import com.crane.bandwebbackend.domain.membership.service.MembershipService;
import com.crane.bandwebbackend.domain.participant.service.ParticipationLookup;
import com.crane.bandwebbackend.domain.performance.entity.Performance;
import com.crane.bandwebbackend.domain.performance.entity.PerformanceStatus;
import com.crane.bandwebbackend.domain.performance.service.PerformanceService;
import com.crane.bandwebbackend.global.error.BusinessException;
import com.crane.bandwebbackend.global.error.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AuditionReviewServiceTest {
    private final AuditionEntryRepository repository = mock(AuditionEntryRepository.class);
    private final PerformanceService performances = mock(PerformanceService.class);
    private final AuditionService service = new AuditionService(
            repository,
            mock(ParticipationLookup.class),
            mock(MembershipService.class),
            performances,
            mock(EntityManager.class),
            mock(com.crane.bandwebbackend.global.audit.AuditRecorder.class));

    @Test
    void api149RejectsStaleAuditionVersion() throws Exception {
        Performance performance = mock(Performance.class);
        var club = mock(com.crane.bandwebbackend.domain.club.entity.Club.class);
        when(club.getStatus()).thenReturn(com.crane.bandwebbackend.domain.club.entity.ClubStatus.ACTIVE);
        when(performance.getClub()).thenReturn(club);
        AuditionEntry entry = mock(AuditionEntry.class);
        when(performances.requireLocked(1L, 2L)).thenReturn(performance);
        when(performance.getStatus()).thenReturn(PerformanceStatus.ACTIVE);
        when(repository.findWithLockByIdAndPerformanceId(3L, 2L)).thenReturn(Optional.of(entry));
        when(entry.getId()).thenReturn(3L);
        when(entry.getUpdatedAt()).thenReturn(LocalDateTime.of(2026, 11, 5, 0, 0));
        AuditionReviewRequest body = new ObjectMapper()
                .readValue("{\"result\":\"PASSED\",\"version\":\"stale\"}", AuditionReviewRequest.class);

        assertThatThrownBy(() -> service.review(1L, 2L, 3L, 7L, body))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.VERSION_CONFLICT));
    }
}
