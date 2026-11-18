package com.crane.bandwebbackend.domain.membership.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crane.bandwebbackend.TestcontainersConfiguration;
import com.crane.bandwebbackend.global.security.AuthPrincipal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** API-014 가입 신청, API-028 가입 신청 취소. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Sql({"/performance-flow-cleanup.sql", "/performance-flow-seed.sql"})
@Sql(scripts = "/performance-flow-cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class JoinRequestApiTest {
    private static final String CODE = "ABCDEFGH23";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PlatformTransactionManager transactionManager;

    private void setUp(boolean withCode, boolean searchable) {
        jdbc.update("INSERT INTO users (id, name, handle) VALUES (5, '신청자', 'applicant_5')");
        jdbc.update(
                "UPDATE club SET join_code = ?, is_searchable = ? WHERE id = 1", withCode ? CODE : null, searchable);
    }

    @Test
    @DisplayName("API-014: JOIN_CODE가 맞으면 201 PENDING 소속을 만든다")
    void joinCodeSuccess() throws Exception {
        setUp(true, false);
        apply(5, "{\"source\":\"JOIN_CODE\",\"joinCode\":\"" + CODE + "\",\"displayName\":\"새내기\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.joinSource").value("JOIN_CODE"))
                .andExpect(jsonPath("$.data.memberType").value("REGULAR"))
                .andExpect(jsonPath("$.data.role").value("MEMBER"))
                .andExpect(jsonPath("$.data.displayName").value("새내기"))
                .andExpect(jsonPath("$.data.approvedAt").isEmpty());
        assertThat(jdbc.queryForObject("SELECT status FROM membership WHERE club_id = 1 AND user_id = 5", String.class))
                .isEqualTo("PENDING");
    }

    @Test
    @DisplayName("API-014: 공개 동아리는 SEARCH로 신청할 수 있다")
    void searchSuccess() throws Exception {
        setUp(false, true);
        apply(5, "{\"source\":\"SEARCH\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.joinSource").value("SEARCH"))
                .andExpect(jsonPath("$.data.displayName").value("신청자"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ZZZZZZZZ22", "abc", "ABCDEFGH23X"})
    @DisplayName("C-08: 코드가 다르거나 형식이 틀려도 404")
    void wrongOrMalformedCode(String code) throws Exception {
        setUp(true, false);
        apply(5, "{\"source\":\"JOIN_CODE\",\"joinCode\":\"" + code + "\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("C-08: 동아리 코드가 없으면 404, 비공개 SEARCH도 404")
    void missingCodeAndPrivateSearch() throws Exception {
        setUp(true, false);
        apply(5, "{\"source\":\"SEARCH\"}").andExpect(status().isNotFound());
        jdbc.update("UPDATE club SET join_code = NULL WHERE id = 1");
        apply(5, "{\"source\":\"JOIN_CODE\",\"joinCode\":\"" + CODE + "\"}").andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("API-014: 틀린 코드는 동아리 잠금을 기다리지 않고 바로 404")
    void wrongCodeDoesNotWaitForClubLock() throws Exception {
        setUp(true, false);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        // 다른 트랜잭션(임원 쓰기 등)이 동아리 행을 잠그고 있는 상황
        Thread holder = new Thread(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbc.queryForObject("SELECT id FROM club WHERE id = 1 FOR UPDATE", Long.class);
            locked.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }));
        holder.start();
        try {
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
            long start = System.nanoTime();
            apply(5, "{\"source\":\"JOIN_CODE\",\"joinCode\":\"WRONGCODE2\"}").andExpect(status().isNotFound());
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(3));
        } finally {
            release.countDown();
            holder.join();
        }
    }

    @Test
    @DisplayName("API-014: 비활성 동아리는 404")
    void inactiveClub() throws Exception {
        setUp(true, true);
        jdbc.update("UPDATE club SET status = 'INACTIVE' WHERE id = 1");
        apply(5, "{\"source\":\"SEARCH\"}").andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("API-014: 이미 PENDING이거나 ACTIVE인 소속이 있으면 409 DUPLICATE_MEMBERSHIP")
    void duplicate() throws Exception {
        setUp(false, true);
        apply(2, "{\"source\":\"SEARCH\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE"))
                .andExpect(jsonPath("$.error.details[0].reason").value("DUPLICATE_MEMBERSHIP"));
        apply(5, "{\"source\":\"SEARCH\"}").andExpect(status().isCreated());
        apply(5, "{\"source\":\"SEARCH\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.details[0].reason").value("DUPLICATE_MEMBERSHIP"));
    }

    @Test
    @DisplayName("API-014: INVITED 소속이 있으면 409 DUPLICATE_MEMBERSHIP")
    void invitedDuplicate() throws Exception {
        setUp(false, true);
        jdbc.update("UPDATE membership SET status = 'INVITED', invited_at = NOW(6) WHERE id = 3");
        apply(3, "{\"source\":\"SEARCH\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.details[0].reason").value("DUPLICATE_MEMBERSHIP"));
    }

    @Test
    @DisplayName("API-014: source가 JOIN_CODE인데 joinCode가 없으면 400")
    void missingJoinCode() throws Exception {
        setUp(true, true);
        apply(5, "{\"source\":\"JOIN_CODE\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"REJECTED", "DECLINED", "WITHDRAWN"})
    @DisplayName("C-06: 거절·탈퇴 소속은 같은 행을 PENDING으로 되살리고 joinSource를 갱신한다")
    void reapply(String oldStatus) throws Exception {
        setUp(false, true);
        jdbc.update(
                "UPDATE membership SET status = ?, role = 'MEMBER', approved_at = NOW(6), left_at = NOW(6) WHERE id = 4",
                oldStatus);
        jdbc.update("INSERT INTO membership_part (membership_id, part_id) VALUES (4, 1)");
        apply(4, "{\"source\":\"SEARCH\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value("4"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.joinSource").value("SEARCH"))
                .andExpect(jsonPath("$.data.approvedAt").isEmpty())
                .andExpect(jsonPath("$.data.leftAt").isEmpty())
                .andExpect(jsonPath("$.data.parts.length()").value(1));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM membership WHERE club_id = 1", Integer.class))
                .isEqualTo(4);
    }

    @Test
    @DisplayName("API-014: displayName이 공백뿐이거나 source가 없으면 400")
    void validation() throws Exception {
        setUp(false, true);
        apply(5, "{\"source\":\"SEARCH\",\"displayName\":\"   \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        apply(5, "{}").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("API-028: 본인의 PENDING 신청을 취소하면 WITHDRAWN이 되고 감사 기록을 남긴다")
    void cancelOwn() throws Exception {
        setUp(false, true);
        jdbc.update("UPDATE membership SET status = 'PENDING' WHERE id = 3");
        cancel(3, 3)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WITHDRAWN"))
                .andExpect(jsonPath("$.data.approvedAt").isEmpty())
                .andExpect(jsonPath("$.data.leftAt").isEmpty());
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM audit_log WHERE action = 'JOIN_REQUEST_CANCELED'"
                                + " AND target_type = 'MEMBERSHIP' AND target_id = 3 AND actor_user_id = 3",
                        Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT left_at FROM membership WHERE id = 3", Object.class))
                .isNull();
    }

    @Test
    @DisplayName("API-028: 남의 신청은 404, PENDING이 아니면 409 INVALID_STATE")
    void cancelRejected() throws Exception {
        setUp(false, true);
        jdbc.update("UPDATE membership SET status = 'PENDING' WHERE id = 3");
        cancel(2, 3).andExpect(status().isNotFound());
        cancel(2, 2)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("STATE_CONFLICT"))
                .andExpect(jsonPath("$.error.details[0].reason").value("INVALID_STATE"));
    }

    @Test
    @DisplayName("API-028: 비활성 동아리에서는 409 CLUB_INACTIVE")
    void cancelInactiveClub() throws Exception {
        setUp(false, true);
        jdbc.update("UPDATE membership SET status = 'PENDING' WHERE id = 3");
        jdbc.update("UPDATE club SET status = 'INACTIVE' WHERE id = 1");
        cancel(3, 3)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.details[0].reason").value("CLUB_INACTIVE"));
    }

    private ResultActions apply(long userId, String body) throws Exception {
        return mvc.perform(post("/api/v1/clubs/1/join-requests")
                .with(authentication(actor(userId)))
                .header("X-Requested-With", "XMLHttpRequest")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions cancel(long userId, long requestId) throws Exception {
        return mvc.perform(post("/api/v1/clubs/1/join-requests/" + requestId + "/cancel")
                .with(authentication(actor(userId)))
                .header("X-Requested-With", "XMLHttpRequest")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"));
    }

    private static UsernamePasswordAuthenticationToken actor(long userId) {
        return new UsernamePasswordAuthenticationToken(new AuthPrincipal(userId, "sid", "jti", false), null, List.of());
    }
}
