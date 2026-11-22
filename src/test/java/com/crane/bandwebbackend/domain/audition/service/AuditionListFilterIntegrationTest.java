package com.crane.bandwebbackend.domain.audition.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crane.bandwebbackend.TestcontainersConfiguration;
import com.crane.bandwebbackend.global.security.CookieFactory;
import com.crane.bandwebbackend.global.security.JwtProvider;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Sql({"/performance-flow-cleanup.sql", "/performance-flow-seed.sql"})
@Sql(scripts = "/performance-flow-cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class AuditionListFilterIntegrationTest {
    private static final String LIST = "/api/v1/clubs/1/performances/1/auditions";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtProvider jwt;

    @Autowired
    private CookieFactory cookies;

    @Autowired
    private JdbcTemplate jdbc;

    // 1번 대기, 2번 완료는 공연 1 / 3번은 다른 공연의 오디션
    @BeforeEach
    void seed() {
        jdbc.update(
                "INSERT INTO performance (id,club_id,title,preset,status,created_by_user_id) VALUES (2,1,'공연','CUSTOM','ACTIVE',1)");
        for (int i = 1; i <= 3; i++)
            jdbc.update(
                    "INSERT INTO song (id,performance_id,title,status,proposed_by_membership_id) VALUES (?,?,'곡','PROPOSED',1)",
                    i,
                    i == 3 ? 2 : 1);
        for (int i = 1; i <= 3; i++)
            jdbc.update(
                    "INSERT INTO audition_entry (id,performance_id,song_id,created_by_user_id) VALUES (?,?,?,1)",
                    i,
                    i == 3 ? 2 : 1,
                    i);
        jdbc.update(
                "UPDATE audition_entry SET result='PASSED', reviewed_by_user_id=1, reviewed_at=UTC_TIMESTAMP(6) WHERE id=2");
    }

    @Test
    @DisplayName("API-147 tab을 보내지 않으면 PENDING만 돌려주고 pendingCount는 유지한다")
    void tabDefaultsToPending() throws Exception {
        mvc.perform(get(LIST).cookie(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value("1"))
                .andExpect(jsonPath("$.data.pendingCount").value(1));
        // pendingCount는 tab과 무관하다
        mvc.perform(get(LIST).cookie(admin()).param("tab", "DONE"))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value("2"))
                .andExpect(jsonPath("$.data.pendingCount").value(1));
    }

    @Test
    @DisplayName("API-147 auditionId 필터는 다른 필터와 AND이며 다른 공연의 ID는 빈 목록이다")
    void auditionIdFilter() throws Exception {
        mvc.perform(get(LIST).cookie(admin()).param("auditionId", "1"))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value("1"));
        mvc.perform(get(LIST).cookie(admin()).param("auditionId", "1").param("tab", "DONE"))
                .andExpect(jsonPath("$.data.items.length()").value(0))
                .andExpect(jsonPath("$.data.pendingCount").value(1));
        // 다른 공연의 ID는 오류가 아니라 빈 목록
        mvc.perform(get(LIST).cookie(admin()).param("auditionId", "3").param("tab", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0));
        // 완료 건은 tab 기본값(PENDING)에서 빠지고 DONE에서 찾는다
        mvc.perform(get(LIST).cookie(admin()).param("auditionId", "2"))
                .andExpect(jsonPath("$.data.items.length()").value(0))
                .andExpect(jsonPath("$.data.pendingCount").value(0));
        mvc.perform(get(LIST).cookie(admin()).param("auditionId", "2").param("tab", "DONE"))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value("2"));
        // songId와 AND로 적용한다
        mvc.perform(get(LIST).cookie(admin()).param("auditionId", "1").param("songId", "2"))
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    @DisplayName("API-147 auditionId 형식이 잘못되면 400 VALIDATION_ERROR이다")
    void invalidAuditionId() throws Exception {
        for (String value : new String[] {"0", "-1", "abc", "1.5"})
            mvc.perform(get(LIST).cookie(admin()).param("auditionId", value))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("API-110 현재와 다른 status는 409 STATE_CONFLICT와 reason INVALID_STATE이다")
    void scheduleStatusMismatch() throws Exception {
        jdbc.update(
                "INSERT INTO practice_schedule (id,performance_id,title,status,start_at,end_at,for_all_participants,created_by_user_id) VALUES (10,1,'일정','TENTATIVE','2099-01-01 00:00:00','2099-01-01 01:00:00',true,1)");
        mvc.perform(post("/api/v1/clubs/1/performances/1/schedules/10/validate")
                        .cookie(admin())
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"0\",\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("STATE_CONFLICT"))
                .andExpect(jsonPath("$.error.details[0].reason").value("INVALID_STATE"));
    }

    @Test
    @DisplayName("API-110 PATCH도 현재와 다른 status는 409 INVALID_STATE이고, 같은 status는 그대로 수정된다")
    void schedulePatchStatusMismatch() throws Exception {
        jdbc.update(
                "INSERT INTO practice_schedule (id,performance_id,title,status,start_at,end_at,for_all_participants,created_by_user_id) VALUES (10,1,'일정','TENTATIVE','2099-01-01 00:00:00','2099-01-01 01:00:00',true,1)");
        mvc.perform(patch("/api/v1/clubs/1/performances/1/schedules/10")
                        .cookie(admin())
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"0\",\"status\":\"CONFIRMED\",\"title\":\"바뀐 제목\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("STATE_CONFLICT"))
                .andExpect(jsonPath("$.error.details[0].reason").value("INVALID_STATE"));
        assertThat(jdbc.queryForObject("SELECT title FROM practice_schedule WHERE id = 10", String.class))
                .isEqualTo("일정");

        mvc.perform(patch("/api/v1/clubs/1/performances/1/schedules/10")
                        .cookie(admin())
                        .header("X-Requested-With", "XMLHttpRequest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"0\",\"status\":\"TENTATIVE\",\"title\":\"바뀐 제목\"}"))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT title FROM practice_schedule WHERE id = 10", String.class))
                .isEqualTo("바뀐 제목");
    }

    private Cookie admin() {
        return new Cookie(
                cookies.accessName(),
                jwt.createAccessToken(1L, "audition-list-filter-test", 0).getToken());
    }
}
