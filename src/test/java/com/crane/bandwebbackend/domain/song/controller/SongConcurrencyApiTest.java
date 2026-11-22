package com.crane.bandwebbackend.domain.song.controller;

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

// #82: 같은 곡에 같은 version으로 동시에 필요 편성·연주자를 저장하면 교착 없이 한 건만 성공한다
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Sql({"/performance-flow-cleanup.sql", "/performance-flow-seed.sql"})
@Sql(scripts = "/performance-flow-cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class SongConcurrencyApiTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("같은 version으로 동시에 필요 편성을 저장하면 200 한 건, 나머지는 409 VERSION_CONFLICT")
    void concurrentReplaceRequirements() throws Exception {
        long version = insertSong();
        List<String> bodies = putConcurrently(
                "requirements",
                version,
                List.of("[{\"partId\":\"1\",\"requiredCount\":1}]", "[{\"partId\":\"2\",\"requiredCount\":2}]"));
        assertOneWinnerOneConflict(bodies);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM song_part_requirement WHERE song_id = 300", Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("같은 version으로 동시에 연주자를 저장하면 200 한 건, 나머지는 409 VERSION_CONFLICT")
    void concurrentReplaceSessions() throws Exception {
        long version = insertSong();
        List<String> bodies = putConcurrently(
                "sessions",
                version,
                List.of(
                        "[{\"performanceMemberId\":\"2\",\"partId\":\"1\"}]",
                        "[{\"performanceMemberId\":\"2\",\"partId\":\"2\"}]"));
        assertOneWinnerOneConflict(bodies);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM song_session_member WHERE song_id = 300", Integer.class))
                .isEqualTo(1);
    }

    private long insertSong() {
        jdbc.update("INSERT INTO song (id, performance_id, title, status, proposed_by_membership_id)"
                + " VALUES (300, 1, '동시성곡', 'PROPOSED', 1)");
        return jdbc.queryForObject("SELECT version FROM song WHERE id = 300", Long.class);
    }

    private List<String> putConcurrently(String path, long version, List<String> arrays) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();
        for (String array : arrays) {
            results.add(pool.submit(() -> {
                start.await();
                var response = mvc.perform(
                                MockMvcRequestBuilders.put("/api/v1/clubs/1/performances/1/songs/300/" + path)
                                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                                new AuthPrincipal(1, "sid", "jti", false), null, List.of())))
                                        .header("X-Requested-With", "XMLHttpRequest")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"version\":\"" + version + "\",\"items\":" + array + "}"))
                        .andReturn()
                        .getResponse();
                return response.getStatus() + (response.getStatus() == 200 ? "" : response.getContentAsString());
            }));
        }
        start.countDown();
        List<String> bodies = new ArrayList<>();
        for (Future<String> result : results) {
            bodies.add(result.get());
        }
        pool.shutdown();
        return bodies;
    }

    private void assertOneWinnerOneConflict(List<String> bodies) {
        assertThat(bodies).filteredOn(b -> b.equals("200")).hasSize(1);
        assertThat(bodies)
                .filteredOn(b -> b.startsWith("409") && b.contains("VERSION_CONFLICT"))
                .hasSize(1);
    }
}
