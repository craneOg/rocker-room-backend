package com.crane.bandwebbackend.domain.team.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import com.crane.bandwebbackend.TestcontainersConfiguration;
import com.crane.bandwebbackend.global.security.AuthPrincipal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

// #80: 같은 팀에 같은 version으로 동시에 팀원을 추가하면 교착 없이 한 건만 성공한다
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Sql({"/performance-flow-cleanup.sql", "/performance-flow-seed.sql"})
@Sql(scripts = "/performance-flow-cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class TeamAddMemberConcurrencyApiTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("같은 version으로 동시에 팀원을 추가하면 201 한 건, 나머지는 409 VERSION_CONFLICT")
    void concurrentAddMember() throws Exception {
        jdbc.update("INSERT INTO team (id, performance_id, name, is_default, status, source, created_by_user_id)"
                + " VALUES (200, 1, '동시성팀', FALSE, 'APPROVED', 'ADMIN', 1)");
        long version = jdbc.queryForObject("SELECT version FROM team WHERE id = 200", Long.class);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();
        for (long membershipId : new long[] {2, 3}) {
            results.add(pool.submit(() -> {
                start.await();
                var response = mvc.perform(MockMvcRequestBuilders.post(
                                        "/api/v1/clubs/1/performances/1/teams/200/members")
                                .with(authentication(new UsernamePasswordAuthenticationToken(
                                        new AuthPrincipal(1, "sid", "jti", false), null, List.of())))
                                .header("X-Requested-With", "XMLHttpRequest")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"version\":\"" + version + "\",\"membershipId\":\"" + membershipId + "\"}"))
                        .andReturn()
                        .getResponse();
                return response.getStatus() + (response.getStatus() == 201 ? "" : response.getContentAsString());
            }));
        }
        start.countDown();
        List<String> bodies = new ArrayList<>();
        for (Future<String> result : results) {
            bodies.add(result.get());
        }
        pool.shutdown();

        assertThat(bodies).filteredOn(b -> b.equals("201")).hasSize(1);
        assertThat(bodies)
                .filteredOn(b -> b.startsWith("409") && b.contains("VERSION_CONFLICT"))
                .hasSize(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM team_member WHERE team_id = 200", Integer.class))
                .isEqualTo(1);
    }
}
