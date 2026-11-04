package com.crane.bandwebbackend.domain.audition.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.crane.bandwebbackend.domain.audition.dto.AuditionReviewRequest;
import com.crane.bandwebbackend.domain.audition.entity.AuditionEntry;
import com.crane.bandwebbackend.domain.audition.repository.AuditionEntryRepository;
import com.crane.bandwebbackend.domain.membership.service.MembershipService;
import com.crane.bandwebbackend.domain.participant.repository.PerformanceMemberRepository;
import com.crane.bandwebbackend.domain.performance.entity.Performance;
import com.crane.bandwebbackend.domain.performance.entity.PerformanceStatus;
import com.crane.bandwebbackend.domain.performance.service.PerformanceService;
import com.crane.bandwebbackend.global.error.ApiErrorCode;
import com.crane.bandwebbackend.global.error.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AuditionReviewServiceTest {
    private final AuditionEntryRepository repository = mock(AuditionEntryRepository.class);
    private final PerformanceService performances = mock(PerformanceService.class);
    private final AuditionService service = new AuditionService(repository,
            mock(PerformanceMemberRepository.class), mock(MembershipService.class),
            performances, mock(EntityManager.class));

    @Test
    void api149RejectsStaleAuditionVersion() throws Exception {
        Performance performance = mock(Performance.class);
        AuditionEntry entry = mock(AuditionEntry.class);
        when(performances.require(1L, 2L)).thenReturn(performance);
        when(performance.getStatus()).thenReturn(PerformanceStatus.ACTIVE);
        when(repository.findByIdAndPerformanceId(3L, 2L)).thenReturn(Optional.of(entry));
        when(entry.getVersion()).thenReturn(4L);
        AuditionReviewRequest body = new ObjectMapper().readValue(
                "{\"result\":\"PASSED\",\"version\":\"3\"}", AuditionReviewRequest.class);

        assertThatThrownBy(() -> service.review(1L, 2L, 3L, 7L, body))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.getCode()).isEqualTo(ApiErrorCode.VERSION_CONFLICT));
    }
}
