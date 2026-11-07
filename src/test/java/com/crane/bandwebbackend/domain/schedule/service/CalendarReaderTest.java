package com.crane.bandwebbackend.domain.schedule.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.crane.bandwebbackend.domain.membership.entity.Membership;
import com.crane.bandwebbackend.domain.membership.entity.MembershipRole;
import com.crane.bandwebbackend.domain.membership.service.MembershipService;
import com.crane.bandwebbackend.domain.participant.service.ParticipationLookup;
import com.crane.bandwebbackend.domain.performance.service.PerformanceService;
import com.crane.bandwebbackend.domain.schedule.repository.PracticeScheduleRepository;
import com.crane.bandwebbackend.domain.schedule.repository.PracticeScheduleSongRepository;
import com.crane.bandwebbackend.domain.schedule.repository.PracticeScheduleTeamRepository;
import com.crane.bandwebbackend.global.error.ErrorCode;
import com.crane.bandwebbackend.global.error.BusinessException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class CalendarReaderTest {
    private final MembershipService memberships = mock(MembershipService.class);
    private final CalendarReader reader = new CalendarReader(memberships,
            mock(PerformanceService.class), mock(ParticipationLookup.class),
            mock(PracticeScheduleRepository.class), mock(PracticeScheduleTeamRepository.class),
            mock(PracticeScheduleSongRepository.class));

    @Test
    void regularMemberCannotRevealTentativeSchedules() {
        Membership member = mock(Membership.class);
        when(memberships.requireMember(1L, 7L)).thenReturn(member);
        when(member.getRole()).thenReturn(MembershipRole.MEMBER);
        assertThatThrownBy(() -> reader.club(1L, 7L,
                LocalDateTime.of(2026, 11, 1, 0, 0), LocalDateTime.of(2026, 12, 1, 0, 0),
                null, null, null, null, true, false, true))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> org.assertj.core.api.Assertions.assertThat(error.getCode())
                                .isEqualTo(ErrorCode.FORBIDDEN));
    }
}
