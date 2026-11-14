package com.crane.bandwebbackend.domain.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.crane.bandwebbackend.TestcontainersConfiguration;
import com.crane.bandwebbackend.domain.schedule.repository.PracticeScheduleRepository;
import com.crane.bandwebbackend.global.error.BusinessException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Sql({"/performance-flow-cleanup.sql", "/performance-flow-seed.sql"})
@Sql(scripts = "/performance-flow-cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class CalendarReaderIntegrationTest {
    @Autowired
    private CalendarReader calendars;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoSpyBean
    private PracticeScheduleRepository schedules;

    private final LocalDateTime from = LocalDateTime.of(2099, 1, 2, 0, 0);
    private final LocalDateTime to = from.plusDays(1);

    @BeforeEach
    void guest() {
        jdbc.update("INSERT INTO users (id,name,handle) VALUES (4,'객원','guest4')");
        jdbc.update(
                "INSERT INTO membership (id,club_id,user_id,member_type,role,status,join_source) VALUES (4,1,4,'GUEST','MEMBER','ACTIVE','ADMIN_ADDED')");
        jdbc.update(
                "INSERT INTO performance_member (id,performance_id,club_id,membership_id,status,source) VALUES (4,1,1,4,'APPROVED','ADMIN')");
    }

    @Test
    @DisplayName("API-114·115 기간의 일부만 겹치는 일정을 포함하고 객원 공개 범위·상태를 DB에서 제한한다")
    void overlapAndGuestVisibility() {
        schedule(1, from, from.plusHours(1), "CONFIRMED", true);
        schedule(2, from.minusMinutes(30), from.plusMinutes(30), "CONFIRMED", true);
        schedule(3, from.minusHours(1), from, "CONFIRMED", true);
        schedule(4, to, to.plusHours(1), "CONFIRMED", true);
        schedule(5, from, from.plusHours(1), "TENTATIVE", true);
        schedule(6, from, from.plusHours(1), "CANCELED", true);
        schedule(7, from, from.plusHours(1), "CONFIRMED", false);
        assertThat(calendars.personal(4L, from, to, null, false, false))
                .extracting(event -> event.getId())
                .containsExactly("2", "1");
        verify(schedules, times(1)).findAttendedScheduleIds(1L, 4L);
        assertThat(club(false)).extracting(event -> event.getId()).containsExactly("2", "1");
        verify(schedules, times(2)).findAttendedScheduleIds(1L, 4L);
        assertThat(club(true)).extracting(event -> event.getId()).containsExactly("2", "1", "6");
        assertThat(calendars.club(1L, 2L, from, to, null, null, null, null, false, false, false))
                .extracting(event -> event.getId())
                .containsExactly("2", "1", "7");
    }

    @Test
    @DisplayName("API-114·115 기간 상한 없이 공개 결과 2000건은 허용하고 2001건은 RANGE_TOO_LARGE다")
    void boundsVisibleResultsBeforeConversion() {
        List<Object[]> batch = new ArrayList<>();
        for (int i = 0; i < 4001; i++) {
            batch.add(new Object[] {Timestamp.valueOf(from), Timestamp.valueOf(from.plusHours(1)), i < 2000});
        }
        jdbc.batchUpdate(
                "INSERT INTO practice_schedule (performance_id,title,status,start_at,end_at,for_all_participants,created_by_user_id) VALUES (1,'일정','CONFIRMED',?,?,?,1)",
                batch);
        // 기간 밖의 공개 일정과 기간 안의 비공개 일정은 결과 한도에 포함하지 않는다.
        schedule(10000, LocalDateTime.of(1999, 1, 1, 0, 0), LocalDateTime.of(1999, 1, 1, 1, 0), "CONFIRMED", true);
        LocalDateTime wideFrom = LocalDateTime.of(2000, 1, 1, 0, 0);
        LocalDateTime wideTo = LocalDateTime.of(2100, 1, 1, 0, 0);
        assertThat(calendars.personal(4L, wideFrom, wideTo, null, false, false)).hasSize(2000);
        assertThat(club(false)).hasSize(2000);
        schedule(10001, from, from.plusHours(1), "CONFIRMED", true);
        assertThatThrownBy(() -> calendars.personal(4L, wideFrom, wideTo, null, false, false))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getDetails().get(0).getReason()).isEqualTo("RANGE_TOO_LARGE"));
        assertThatThrownBy(() -> club(false))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getDetails().get(0).getReason()).isEqualTo("RANGE_TOO_LARGE"));
    }

    @Test
    @DisplayName("API-114·115 공연일 id와 performanceId는 공연 ID이며 일정 ID는 기존 형식을 유지한다")
    void performanceDateIdentifier() {
        jdbc.update("UPDATE performance SET perform_date='2099-01-02' WHERE id=1");
        schedule(1, from, from.plusHours(1), "CONFIRMED", true);
        var personal = calendars.personal(4L, from, to, null, false, true);
        var club = calendars.club(1L, 4L, from, to, null, null, null, null, false, false, true);
        for (var events : List.of(personal, club)) {
            assertThat(events).hasSize(2);
            var performance = events.stream()
                    .filter(event -> event.getEventType().equals("PERFORMANCE"))
                    .findFirst()
                    .orElseThrow();
            assertThat(performance.getId()).isEqualTo("1");
            assertThat(performance.getPerformanceId()).isEqualTo("1");
            assertThat(performance.isAllDay()).isTrue();
            assertThat(performance.getStatus()).isNull();
            assertThat(events.stream()
                            .filter(event -> !event.isAllDay())
                            .findFirst()
                            .orElseThrow()
                            .getId())
                    .isEqualTo("1");
        }
    }

    @Test
    @DisplayName("API-115 공연일 이벤트도 전체 2000건 한도에 포함한다")
    void performanceDateCountsTowardLimit() {
        List<Object[]> batch = new ArrayList<>();
        for (int i = 0; i < 2000; i++)
            batch.add(new Object[] {Timestamp.valueOf(from), Timestamp.valueOf(from.plusHours(1))});
        jdbc.batchUpdate(
                "INSERT INTO practice_schedule (performance_id,title,status,start_at,end_at,for_all_participants,created_by_user_id) VALUES (1,'일정','CONFIRMED',?,?,true,1)",
                batch);
        jdbc.update("UPDATE performance SET perform_date='2099-01-02' WHERE id=1");
        assertThatThrownBy(() -> calendars.club(1L, 4L, from, to, null, null, null, null, false, false, true))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getDetails().get(0).getReason()).isEqualTo("RANGE_TOO_LARGE"));
    }

    private List<com.crane.bandwebbackend.domain.schedule.dto.CalendarEvent> club(boolean canceled) {
        return calendars.club(1L, 4L, from, to, null, null, null, null, false, canceled, false);
    }

    private void schedule(long id, LocalDateTime start, LocalDateTime end, String status, boolean all) {
        jdbc.update(
                "INSERT INTO practice_schedule (id,performance_id,title,status,start_at,end_at,for_all_participants,created_by_user_id) VALUES (?,1,'일정',?,?,?,?,1)",
                id,
                status,
                Timestamp.valueOf(start),
                Timestamp.valueOf(end),
                all);
    }
}
