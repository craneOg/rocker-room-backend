package com.crane.bandwebbackend.domain.membership.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crane.bandwebbackend.TestcontainersConfiguration;
import com.crane.bandwebbackend.global.security.AuthPrincipal;
import java.util.List;
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

/** API-032 보유 파트 전체 저장. 파트 순서는 가정하지 않는다. 시드: 파트 1·2 활성, 소속 2가 파트 1 보유. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Sql({"/performance-flow-cleanup.sql", "/performance-flow-seed.sql"})
@Sql(scripts = "/performance-flow-cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class MembershipPartsApiTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("API-032: 본인이 저장하면 전체가 교체된다(이미 가진 파트를 다시 넣어도 성공)")
    void selfReplace() throws Exception {
        save(2, 2, "[\"1\",\"2\"]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parts.length()").value(2));
        assertThat(partIds(2)).containsExactlyInAnyOrder(1L, 2L);
        save(2, 2, "[\"2\"]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parts.length()").value(1));
        assertThat(partIds(2)).containsExactly(2L);
    }

    @Test
    @DisplayName("API-032: 바뀌면 본인·임원 모두 MEMBERSHIP_PARTS_CHANGED를 남기고, 같은 목록이면 남기지 않는다")
    void auditOnlyWhenChanged() throws Exception {
        save(2, 2, "[\"1\"]").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log", Integer.class))
                .isZero();
        save(2, 2, "[\"2\",\"1\"]").andExpect(status().isOk());
        save(1, 2, "[]").andExpect(status().isOk());
        assertThat(jdbc.queryForList(
                        "SELECT actor_user_id FROM audit_log WHERE action = 'MEMBERSHIP_PARTS_CHANGED'"
                                + " AND target_id = 2 ORDER BY id",
                        Long.class))
                .containsExactly(2L, 1L);
        assertThat(jdbc.queryForObject(
                        "SELECT JSON_EXTRACT(detail, '$.to') FROM audit_log ORDER BY id LIMIT 1", String.class))
                .isEqualTo("[\"1\", \"2\"]");
    }

    @Test
    @DisplayName("API-032: 임원은 다른 소속의 파트도 저장하고, 빈 배열이면 모두 비운다")
    void adminAndEmpty() throws Exception {
        save(1, 3, "[\"1\"]").andExpect(status().isOk());
        assertThat(partIds(3)).containsExactly(1L);
        save(1, 3, "[]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parts.length()").value(0));
        assertThat(partIds(3)).isEmpty();
    }

    @Test
    @DisplayName("API-032: 비임원이 남의 소속을 저장하면 403")
    void forbidden() throws Exception {
        save(2, 3, "[\"1\"]").andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("API-032: partIds 누락·형식 오류·중복은 400 field partIds")
    void invalidPartIds() throws Exception {
        for (String body : new String[] {"{}", "{\"partIds\":[\"abc\"]}", "{\"partIds\":[\"1\",\"1\"]}"}) {
            raw(2, 2, body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.error.details[0].field").value("partIds"));
        }
    }

    @Test
    @DisplayName("G-04: 다른 동아리의 파트나 없는 파트는 404")
    void otherClubPart() throws Exception {
        jdbc.update("INSERT INTO club (id, name, created_by) VALUES (2, '다른밴드', 1)");
        jdbc.update("INSERT INTO part (id, club_id, name, display_order) VALUES (9, 2, '타악', 1)");
        save(2, 2, "[\"9\"]").andExpect(status().isNotFound());
        save(2, 2, "[\"999\"]").andExpect(status().isNotFound());
        assertThat(partIds(2)).containsExactly(1L);
    }

    @Test
    @DisplayName("API-032: 새 비활성 파트는 422 PART_INACTIVE, 이미 가진 비활성 파트는 유지된다")
    void inactivePart() throws Exception {
        jdbc.update("INSERT INTO part (id, club_id, name, display_order, is_active) VALUES (3, 1, '퍼커션', 3, FALSE)");
        save(2, 2, "[\"1\",\"3\"]")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.details[0].reason").value("PART_INACTIVE"));
        assertThat(partIds(2)).containsExactly(1L);
        jdbc.update("INSERT INTO membership_part (membership_id, part_id) VALUES (2, 3)");
        save(2, 2, "[\"1\",\"3\"]").andExpect(status().isOk());
        assertThat(partIds(2)).containsExactlyInAnyOrder(1L, 3L);
    }

    @Test
    @DisplayName("API-032: 소속 상태가 활동·휴학·졸업이 아니면 409, 비활성 동아리는 409 CLUB_INACTIVE")
    void stateGuards() throws Exception {
        jdbc.update("UPDATE membership SET status = 'PENDING' WHERE id = 3");
        save(1, 3, "[\"1\"]")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.details[0].reason").value("INVALID_MEMBERSHIP_STATUS"));
        jdbc.update("UPDATE club SET status = 'INACTIVE' WHERE id = 1");
        save(1, 2, "[\"1\"]")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.details[0].reason").value("CLUB_INACTIVE"));
    }

    private List<Long> partIds(long membershipId) {
        return jdbc.queryForList(
                "SELECT part_id FROM membership_part WHERE membership_id = ?", Long.class, membershipId);
    }

    private ResultActions save(long actorId, long membershipId, String partIds) throws Exception {
        return raw(actorId, membershipId, "{\"partIds\":" + partIds + "}");
    }

    private ResultActions raw(long actorId, long membershipId, String body) throws Exception {
        return mvc.perform(put("/api/v1/clubs/1/memberships/" + membershipId + "/parts")
                .with(authentication(actor(actorId)))
                .header("X-Requested-With", "XMLHttpRequest")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static UsernamePasswordAuthenticationToken actor(long userId) {
        return new UsernamePasswordAuthenticationToken(new AuthPrincipal(userId, "sid", "jti", false), null, List.of());
    }
}
