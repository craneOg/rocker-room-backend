package com.crane.bandwebbackend.domain.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crane.bandwebbackend.TestcontainersConfiguration;
import com.crane.bandwebbackend.global.security.AuthPrincipal;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 팀 편성 → 후보곡 → 가불·투표 → 확정 → 세트리스트 → 가능 시간까지 한 흐름(성공 + 규칙 위반).
 * 인증 필터가 아직 없어 principal(users.id)을 직접 넣음
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Sql({"/performance-flow-cleanup.sql", "/performance-flow-seed.sql"})
@Sql(scripts = "/performance-flow-cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class PerformanceFlowTest {

    private static final String BASE = "/api/v1/clubs/1/performances/1";
    private static final long ADMIN = 1L;
    private static final long MEMBER = 2L;

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("공연 준비 한 흐름: 공통 응답 {data, meta}, ID 문자열, 확인 필요·한도·범위 규칙")
    void flow() throws Exception {
        // API-062 임원 팀 생성 → APPROVED, 미참여자(소속 3)는 APPROVED 참여자로 함께 생성
        String team = send(post(BASE + "/teams"), ADMIN, """
                        {"name":"1팀","members":[{"membershipId":"2","partId":"1"},{"membershipId":"3"}],
                         "leaderMembershipId":"2"}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.meta.requestId").isNotEmpty())
                .andExpect(jsonPath("$.data.team.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.team.isDefault").value(false))
                .andExpect(jsonPath("$.data.team.leaderMembershipId").value("2"))
                .andExpect(jsonPath("$.data.team.memberCount").value(2))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String teamId = JsonPath.read(team, "$.data.team.id");

        // API-154 T-07
        send(get(BASE + "/teams/default-name"), MEMBER, null)
                .andExpect(jsonPath("$.data.name").value("2팀"));
        // API-062 팀원 자격 없음(탈퇴) → 409 INELIGIBLE_TEAM_MEMBER(#30)
        send(post(BASE + "/teams"), ADMIN, """
                        {"name":"탈퇴팀","members":[{"membershipId":"4"}]}""")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("STATE_CONFLICT"))
                .andExpect(jsonPath("$.error.details[0].reason").value("INELIGIBLE_TEAM_MEMBER"));
        // API-061·063
        send(get(BASE + "/teams"), ADMIN, null)
                .andExpect(jsonPath("$.data.statusCounts.APPROVED").value(1))
                .andExpect(jsonPath("$.data.content[0].performanceId").value("1"));
        send(get(BASE + "/teams/" + teamId), MEMBER, null)
                .andExpect(jsonPath("$.data.team.performanceId").value("1"))
                .andExpect(jsonPath("$.data.members[0].teamId").value(teamId))
                .andExpect(jsonPath("$.data.members.length()").value(2))
                .andExpect(jsonPath("$.data.members[0].isRegisteredPart").value(true));

        // API-076 부원 팀 범위 후보곡
        String song = send(post(BASE + "/songs"), MEMBER, """
                        {"title":"곡A","artist":"X","referenceUrl":"https://example.com/a","candidateTeamId":"%s"}""".formatted(teamId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.song.status").value("PROPOSED"))
                .andExpect(jsonPath("$.data.song.candidateTeamName").value("1팀"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String songId = JsonPath.read(song, "$.data.song.id");
        // 규칙 0.5 https만
        send(post(BASE + "/songs"), MEMBER, """
                        {"title":"곡X","referenceUrl":"http://example.com"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].reason").value("INVALID_URL"));

        // API-091·096 가불·찬성
        send(put(BASE + "/songs/" + songId + "/playability/me/1"), MEMBER, """
                        {"answer":"POSSIBLE"}""")
                .andExpect(jsonPath("$.data.answer").value("POSSIBLE"))
                .andExpect(jsonPath("$.data.enteredByAdmin").value(false));
        send(put(BASE + "/songs/" + songId + "/votes/me"), MEMBER, """
                        {"score":1}""").andExpect(status().isOk());

        // V-05 찬성 최대 1곡 → 두 번째 곡 찬성은 422
        String other = send(post(BASE + "/songs"), ADMIN, """
                        {"title":"곡B"}""")
                .andReturn()
                .getResponse()
                .getContentAsString();
        String otherId = JsonPath.read(other, "$.data.song.id");
        send(put(BASE + "/songs/" + otherId + "/votes/me"), MEMBER, """
                        {"score":1}""")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("RULE_VIOLATION"))
                .andExpect(jsonPath("$.error.details[0].reason").value("MAX_APPROVALS_EXCEEDED"));

        // API-158·089
        send(get(BASE + "/evaluations"), ADMIN, null)
                .andExpect(jsonPath("$.data.responseRate.total").value(2))
                .andExpect(jsonPath("$.data.responseRate.answered").value(1))
                // 부원A는 곡B(공연 범위)에 미응답, 부원B는 둘 다 미응답
                .andExpect(jsonPath("$.data.nonResponders.length()").value(2));
        send(get(BASE + "/songs/" + songId + "/evaluations"), MEMBER, null)
                .andExpect(jsonPath("$.data.voteCount").value(1))
                .andExpect(jsonPath("$.data.playability.POSSIBLE").value(1))
                .andExpect(jsonPath("$.data.namesVisible.playability").value(true))
                .andExpect(jsonPath("$.data.playabilityResponses[0].reason").doesNotExist());

        // API-080 확정: 담당 팀 기본값 = APPROVED 후보 팀, 연주자 함께
        Object version = JsonPath.read(song, "$.data.song.version");
        String confirmed = send(post(BASE + "/songs/" + songId + "/confirm"), ADMIN, """
                        {"version":"%s","setlistOrder":1,"sessions":[{"performanceMemberId":"2","partId":"1"}]}""".formatted(version))
                .andExpect(jsonPath("$.data.song.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.song.assignedTeamId").value(teamId))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // API-085·079 하위 행 교체 응답의 version으로 바로 다음 수정이 가능해야 함(새로고침 없이 연속 저장)
        String sessionsBody = """
                {"version":"%s","sessions":[{"performanceMemberId":"2","partId":"1"}]}""";
        Object v1 = JsonPath.read(confirmed, "$.data.song.version");
        String saved = send(put(BASE + "/songs/" + songId + "/sessions"), ADMIN, sessionsBody.formatted(v1))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Object v2 = JsonPath.read(saved, "$.data.song.version");
        confirmed = send(put(BASE + "/songs/" + songId + "/requirements"), ADMIN, """
                        {"version":"%s","requirements":[{"partId":"1","requiredCount":1}]}""".formatted(v2))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(Long.parseLong(v2.toString())).isGreaterThan(Long.parseLong(v1.toString()));

        // API-075·077
        send(get(BASE + "/songs?sort=setlistOrder,asc&status=CONFIRMED,PROPOSED"), MEMBER, null)
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.items[0].sessionCount").value(1))
                .andExpect(jsonPath("$.data.myProposalCount").value(1));
        send(get(BASE + "/songs?sort=id,asc"), MEMBER, null).andExpect(status().isBadRequest());
        send(get(BASE + "/songs/" + songId), MEMBER, null)
                .andExpect(jsonPath("$.data.sessions[0].isSupport").value(false))
                .andExpect(jsonPath("$.data.schedules.length()").value(0));

        // API-063 담당곡 항목 필드(#30)
        send(get(BASE + "/teams/" + teamId), MEMBER, null)
                .andExpect(jsonPath("$.data.songs[0].performanceId").value("1"))
                .andExpect(jsonPath("$.data.songs[0].status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.songs[0].assignedTeamId").value(teamId))
                .andExpect(jsonPath("$.data.songs[0].candidateTeamId").value(teamId))
                .andExpect(jsonPath("$.data.songs[0].setlistOrder").value(1));

        // API-087·088
        Object songVersion = JsonPath.read(confirmed, "$.data.song.version");
        // 선정곡이 빠진 목록 → 409 STATE_CONFLICT SETLIST_CHANGED(#30)
        send(put(BASE + "/setlist/order"), ADMIN, """
                        {"version":"0","items":[]}""")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("STATE_CONFLICT"))
                .andExpect(jsonPath("$.error.details[0].reason").value("SETLIST_CHANGED"));
        send(put(BASE + "/setlist/order"), ADMIN, """
                        {"version":"0","items":[{"songId":"%s","order":null,"version":"%s"}]}""".formatted(songId, songVersion))
                .andExpect(jsonPath("$.data[0].song.setlistOrder").doesNotExist());

        // API-082 선정곡 제외는 확인 필요(0.1)
        send(post(BASE + "/songs/" + songId + "/reject"), ADMIN, """
                        {"version":"%s"}""".formatted(
                                Integer.parseInt(songVersion.toString()) + 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFIRMATION_REQUIRED"))
                .andExpect(jsonPath("$.error.warnings[0].type").value("ASSIGNMENT_RELEASED"));

        // API-101·103 가능 시간. null 구간은 500이 아니라 400
        send(put(BASE + "/availability/me"), MEMBER, """
                        {"from":"2026-12-01T00:00:00+09:00","to":"2026-12-02T00:00:00+09:00","slots":[null]}""").andExpect(status().isBadRequest());
        send(put(BASE + "/availability/me"), MEMBER, """
                        {"from":"2026-12-01T00:00:00+09:00","to":"2026-12-02T00:00:00+09:00",
                         "slots":[{"startAt":"2026-12-01T19:00:00+09:00","endAt":"2026-12-01T20:00:00+09:00"}]}""")
                .andExpect(jsonPath("$.data.slots.length()").value(1));
        send(common().param("forAllParticipants", "true"), ADMIN, null)
                .andExpect(jsonPath("$.data.participantCount").value(2))
                .andExpect(jsonPath("$.data.respondedCount").value(1))
                .andExpect(jsonPath("$.data.slots.length()").value(2))
                .andExpect(
                        jsonPath("$.data.missingAvailabilityMemberIds.length()").value(1));
        send(common().param("forAllParticipants", "true"), MEMBER, null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.details[0].reason").value("TARGET_NOT_ALLOWED"));
        send(common().param("teamIds", teamId), MEMBER, null)
                .andExpect(jsonPath("$.data.participants").doesNotExist());

        // G-04 다른 동아리 경로는 404
        send(get("/api/v1/clubs/999/performances/1/songs"), ADMIN, null).andExpect(status().isNotFound());
    }

    private static MockHttpServletRequestBuilder common() {
        return get(BASE + "/availability/common")
                .param("from", "2026-12-01T00:00:00+09:00")
                .param("to", "2026-12-02T00:00:00+09:00");
    }

    private ResultActions send(MockHttpServletRequestBuilder req, long userId, String body) throws Exception {
        req.with(authentication(new UsernamePasswordAuthenticationToken(
                        new AuthPrincipal(userId, "sid", "jti", false), null, List.of())))
                .with(csrf())
                .header("X-Requested-With", "XMLHttpRequest");
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mvc.perform(req);
    }
}
