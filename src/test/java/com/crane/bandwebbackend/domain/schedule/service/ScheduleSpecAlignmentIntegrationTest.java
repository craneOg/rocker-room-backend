package com.crane.bandwebbackend.domain.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.crane.bandwebbackend.TestcontainersConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Sql({"/performance-flow-cleanup.sql", "/performance-flow-seed.sql"})
@Sql(scripts = "/performance-flow-cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class ScheduleSpecAlignmentIntegrationTest {
    @Autowired
    private PracticeScheduleService schedules;

    @Autowired
    private CalendarReader calendars;

    @Autowired
    private ScheduleCreateService creates;

    @Autowired
    private ScheduleEditService edits;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("schedule-04/API-105 93일을 넘는 395일 조회 구간도 허용한다")
    void longRangeIsAllowed() {
        var from = LocalDateTime.of(2026, 1, 1, 0, 0);
        var page = schedules.page(1L, 1L, 1L, from, from.plusDays(395), null, null, null, null, PageRequest.of(0, 20));
        assertThat(page.getContent()).isEmpty();
    }

    @Test
    @DisplayName("schedule-05/API-114 DRAFT 공연의 일정과 공연일은 개인 캘린더에서 제외한다")
    void personalCalendarSkipsDraft() {
        jdbc.update(
                "INSERT INTO practice_schedule (id,performance_id,title,status,start_at,end_at,for_all_participants,created_by_user_id)"
                        + " VALUES (41,1,'전체','CONFIRMED','2099-01-02 00:00:00','2099-01-02 01:00:00',TRUE,1)");
        var from = LocalDateTime.of(2099, 1, 1, 0, 0);
        var to = from.plusDays(3);
        assertThat(calendars.personal(2L, from, to, null, false, false)).hasSize(1);
        jdbc.update("UPDATE performance SET status='DRAFT' WHERE id=1");
        assertThat(calendars.personal(2L, from, to, null, false, false)).isEmpty();
    }

    @Test
    @DisplayName("schedule-09/API-110 일정 수정 감사에 바뀐 항목의 전·후 값이 남는다")
    void editAuditHasFromTo() throws Exception {
        jdbc.update(
                "INSERT INTO practice_schedule (id,performance_id,title,status,start_at,end_at,for_all_participants,created_by_user_id)"
                        + " VALUES (41,1,'전체','CONFIRMED','2099-01-02 00:00:00','2099-01-02 01:00:00',TRUE,1)");
        String version =
                jdbc.queryForObject("SELECT CAST(version AS CHAR) FROM practice_schedule WHERE id=41", String.class);
        edits.edit(1L, 1L, 41L, 1L, mapper.readTree("{\"version\":\"" + version + "\",\"location\":\"합주실\"}"));
        var json = mapper.readTree(jdbc.queryForObject(
                "SELECT CAST(detail AS CHAR) FROM audit_log WHERE action='SCHEDULE_UPDATED'", String.class));
        assertThat(json.at("/from/location").asText()).isEqualTo("null");
        assertThat(json.at("/to/location").asText()).isEqualTo("합주실");
        assertThat(json.at("/to").size()).isEqualTo(1);
    }
}
