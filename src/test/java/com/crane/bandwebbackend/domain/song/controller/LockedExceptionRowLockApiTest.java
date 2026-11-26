package com.crane.bandwebbackend.domain.song.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.crane.bandwebbackend.domain.song.SongApiTestSupport;
import java.sql.Connection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * #133 X-03: LOCKED 예외 수정 API는 공연 행을 SELECT … FOR UPDATE로 잠근다.
 * 다른 트랜잭션이 공연 행을 잠가 두면 요청이 끝나지 않고 기다렸다가, 잠금이 풀리면 처리된다.
 */
class LockedExceptionRowLockApiTest extends SongApiTestSupport {

    @Autowired
    private DataSource dataSource;

    static Stream<Arguments> calls() {
        return Stream.of(
                Arguments.of(
                        "API-065 팀원 추가",
                        "POST",
                        "/teams/100/members",
                        "team",
                        100L,
                        ",\"membershipId\":\"3\",\"reason\":\"r\""),
                Arguments.of(
                        "API-066 대표 파트 변경",
                        "PATCH",
                        "/teams/100/members/1100",
                        "team",
                        100L,
                        ",\"partId\":\"2\",\"reason\":\"r\""),
                Arguments.of("API-067 팀원 제외", "DELETE", "/teams/100/members/1100", "team", 100L, ",\"reason\":\"r\""),
                Arguments.of(
                        "API-068 팀장 교체",
                        "PUT",
                        "/teams/100/leader",
                        "team",
                        100L,
                        ",\"leaderMembershipId\":\"2\",\"reason\":\"r\""),
                Arguments.of(
                        "API-079 필요 편성 저장",
                        "PUT",
                        "/songs/100/requirements",
                        "song",
                        100L,
                        ",\"items\":[],\"reason\":\"r\""),
                Arguments.of(
                        "API-084 담당 팀 배정",
                        "PUT",
                        "/songs/100/assigned-team",
                        "song",
                        100L,
                        ",\"assignedTeamId\":\"100\",\"reason\":\"r\""),
                Arguments.of(
                        "API-085 연주자 저장",
                        "PUT",
                        "/songs/100/sessions",
                        "song",
                        100L,
                        ",\"items\":[{\"performanceMemberId\":\"2\",\"partId\":\"1\"}],\"reason\":\"r\""));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("calls")
    @DisplayName("X-03 LOCKED 예외 수정은 공연 행 잠금을 기다린다")
    void waitsForPerformanceRowLock(String name, String method, String path, String table, long id, String bodyTail)
            throws Exception {
        participant(100, 3, "APPROVED", "APPLICATION");
        team(100, "1팀", "APPROVED", 2, 100);
        confirmedSong(100, "곡A", 1, 100, 2);
        lockPerformance();
        String body = "{" + v(table, id) + bodyTail + "}";
        MockHttpServletRequestBuilder req = switch (method) {
            case "POST" -> post(BASE + path);
            case "PATCH" -> patch(BASE + path);
            case "DELETE" -> delete(BASE + path);
            default -> put(BASE + path);
        };

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try (Connection holder = dataSource.getConnection()) {
            holder.setAutoCommit(false);
            holder.createStatement().executeQuery("SELECT id FROM performance WHERE id = 1 FOR UPDATE");
            Future<Integer> response = pool.submit(
                    () -> send(req, ADMIN, body).andReturn().getResponse().getStatus());
            Thread.sleep(1500);
            assertThat(response.isDone()).as("공연 행이 잠긴 동안 %s은 대기해야 한다", name).isFalse();
            holder.commit();
            assertThat(response.get(20, TimeUnit.SECONDS)).isLessThan(500);
        } finally {
            pool.shutdownNow();
        }
    }

    private String v(String table, long id) {
        return "\"version\":\"" + version(table, id) + "\"";
    }
}
