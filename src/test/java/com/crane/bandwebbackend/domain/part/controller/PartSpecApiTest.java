package com.crane.bandwebbackend.domain.part.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crane.bandwebbackend.TestcontainersConfiguration;
import com.crane.bandwebbackend.global.security.AuthPrincipal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** API-033~035 명세 정합성(#109 memberCount, #132 G-08, #137 PART_NAME_TAKEN). 시드: 파트 1·2, 소속 2가 파트 1 보유. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Sql({"/performance-flow-cleanup.sql", "/performance-flow-seed.sql"})
@Sql(scripts = "/performance-flow-cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class PartSpecApiTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("club-03 API-033: 목록에 memberCount(활동·휴학·졸업만, 중지 파트 포함)가 있고 API-034 응답에는 없다")
    void memberCount() throws Exception {
        jdbc.update("INSERT INTO users (id, name, handle) VALUES (5, '휴학', 'leave_5'), (6, '졸업', 'grad_6'),"
                + " (7, '대기', 'pending_7')");
        jdbc.update("INSERT INTO membership (id, club_id, user_id, role, status, join_source) VALUES"
                + " (5, 1, 5, 'MEMBER', 'ON_LEAVE', 'JOIN_CODE'), (6, 1, 6, 'MEMBER', 'GRADUATED', 'JOIN_CODE'),"
                + " (7, 1, 7, 'MEMBER', 'PENDING', 'JOIN_CODE')");
        jdbc.update("INSERT INTO part (id, club_id, name, display_order, is_active) VALUES (3, 1, '퍼커션', 3, FALSE)");
        jdbc.update(
                "INSERT INTO membership_part (membership_id, part_id) VALUES (5, 1), (6, 1), (7, 1), (4, 1), (2, 3)");
        mvc.perform(get("/api/v1/clubs/1/parts").with(authentication(actor(1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.id=='1')].memberCount").value(3)) // 2(활동)·5(휴학)·6(졸업)
                .andExpect(jsonPath("$.data.items[?(@.id=='2')].memberCount").value(0))
                .andExpect(jsonPath("$.data.items[?(@.id=='3')].memberCount").value(1));
        create(1, "신규파트")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.memberCount").doesNotExist());
    }

    @Test
    @DisplayName("club-04 API-034: 같은 이름의 파트는 409 DUPLICATE, reason PART_NAME_TAKEN")
    void duplicateName() throws Exception {
        create(1, "보컬")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE"))
                .andExpect(jsonPath("$.error.details[0].reason").value("PART_NAME_TAKEN"));
    }

    @Test
    @DisplayName("club-05 G-08 API-034: 비활성 동아리의 파트 추가는 409 CLUB_INACTIVE이고 저장되지 않는다")
    void createOnInactiveClub() throws Exception {
        jdbc.update("UPDATE club SET status = 'INACTIVE' WHERE id = 1");
        create(1, "신규파트")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.details[0].reason").value("CLUB_INACTIVE"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM part", Integer.class))
                .isEqualTo(2);
    }

    private ResultActions create(long actorId, String name) throws Exception {
        return mvc.perform(post("/api/v1/clubs/1/parts")
                .with(authentication(actor(actorId)))
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\"}"));
    }

    private static UsernamePasswordAuthenticationToken actor(long userId) {
        return new UsernamePasswordAuthenticationToken(new AuthPrincipal(userId, "sid", "jti", false), null, List.of());
    }
}
