package com.crane.bandwebbackend.domain.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crane.bandwebbackend.domain.club.entity.Club;
import com.crane.bandwebbackend.domain.club.entity.ClubStatus;
import com.crane.bandwebbackend.domain.membership.service.MembershipService;
import com.crane.bandwebbackend.domain.performance.entity.Performance;
import com.crane.bandwebbackend.domain.performance.entity.PerformanceStatus;
import com.crane.bandwebbackend.domain.schedule.entity.PracticeSchedule;
import com.crane.bandwebbackend.domain.schedule.entity.ScheduleStatus;
import com.crane.bandwebbackend.domain.schedule.repository.PracticeScheduleRepository;
import com.crane.bandwebbackend.global.error.ConfirmationRequiredException;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ScheduleTransitionServiceTest {
    private final MembershipService memberships = mock(MembershipService.class);
    private final PracticeScheduleRepository schedules = mock(PracticeScheduleRepository.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final ScheduleEditService edits = mock(ScheduleEditService.class);
    private final ScheduleTransitionService service = new ScheduleTransitionService(
            memberships, schedules, entityManager, edits);

    @Test
    void confirmRequiresExplicitRetryWhenScheduleHasWarnings() {
        PracticeSchedule schedule = mock(PracticeSchedule.class);
        Performance performance = mock(Performance.class);
        Club club = mock(Club.class);
        when(schedules.findByIdAndPerformance_IdAndPerformance_Club_Id(3L, 2L, 1L))
                .thenReturn(Optional.of(schedule));
        when(schedule.getPerformance()).thenReturn(performance);
        when(performance.getClub()).thenReturn(club);
        when(club.getStatus()).thenReturn(ClubStatus.ACTIVE);
        when(performance.getStatus()).thenReturn(PerformanceStatus.ACTIVE);
        when(schedule.getVersion()).thenReturn(4L);
        when(schedule.getStatus()).thenReturn(ScheduleStatus.TENTATIVE);
        when(edits.hasAttendees(3L)).thenReturn(true);
        when(edits.warningsFor(1L, 2L, 3L, 7L)).thenReturn(List.of(Map.of("type", "OUT_OF_SEASON")));

        assertThatThrownBy(() -> service.confirm(1L, 2L, 3L, 7L, "4", false))
                .isInstanceOfSatisfying(ConfirmationRequiredException.class,
                        error -> assertThat(error.getWarnings()).hasSize(1));
        verify(schedule, never()).transitionTo(ScheduleStatus.CONFIRMED);
    }
}
