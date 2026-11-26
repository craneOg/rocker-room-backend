package com.crane.bandwebbackend.domain.team.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crane.bandwebbackend.domain.song.SongApiTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 팀 API 명세 정합성: #126 #128 #129 #138
class TeamSpecApiTest extends SongApiTestSupport {

    private String v(String table, long id) {
        return "\"version\":\"" + version(table, id) + "\"";
    }

    // 승인 팀 100(팀장 참여자 2, 팀원 참여자 100) + 선정곡 100. LOCKED 공연
    private void lockedTeam() {
        participant(100, 3, "APPROVED", "APPLICATION");
        team(100, "1팀", "APPROVED", 2, 100);
        confirmedSong(100, "곡A", 1, 100, 2);
        session(100, 100, 2);
        lockPerformance();
    }

    // ---------- #126 API-068 ----------

    @Test
    @DisplayName("T-06 팀장은 DRAFT 팀만 팀장을 바꿀 수 있고 SUBMITTED·APPROVED는 409 INVALID_STATE")
    void leaderCanChangeLeaderOnlyInDraft() throws Exception {
        participant(100, 3, "APPROVED", "APPLICATION");
        team(100, "1팀", "SUBMITTED", 100, 2);
        String body = "{" + v("team", 100) + ",\"leaderMembershipId\":\"2\"}";

        send(put(BASE + "/teams/100/leader"), MEMBER_B, body)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("STATE_CONFLICT"))
                .andExpect(jsonPath("$.error.details[0].reason").value("INVALID_STATE"));
        jdbc.update("UPDATE team SET status = 'APPROVED' WHERE id = 100");
        send(put(BASE + "/teams/100/leader"), MEMBER_B, "{" + v("team", 100) + ",\"leaderMembershipId\":\"2\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.details[0].reason").value("INVALID_STATE"));
        // 임원은 그대로 가능
        send(put(BASE + "/teams/100/leader"), ADMIN, "{" + v("team", 100) + ",\"leaderMembershipId\":\"2\"}")
                .andExpect(status().isOk());
        // DRAFT 팀은 팀장도 가능(팀장은 이제 참여자 2)
        jdbc.update("UPDATE team SET status = 'DRAFT' WHERE id = 100");
        send(put(BASE + "/teams/100/leader"), MEMBER, "{" + v("team", 100) + ",\"leaderMembershipId\":\"3\"}")
                .andExpect(status().isOk());
    }

    // ---------- #128 API-072 ----------

    @Test
    @DisplayName("P-07 팀 거절로 REJECTED가 된 참여자는 팀원·팀장에서 빠지고 재개→제출→승인 뒤 팀원은 모두 APPROVED")
    void rejectedApplicantIsRemovedFromTeam() throws Exception {
        // 부원B(미참여)가 승인 참여자 2와 팀 신청 → 작성자는 TEAM_SUBMISSION APPLIED 참여자이자 팀장
        String created = send(
                        post(BASE + "/teams"), MEMBER_B, "{\"name\":\"신청팀\",\"members\":[{\"membershipId\":\"2\"}]}")
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String teamId = JsonPath.read(created, "$.data.resource.id");
        send(post(BASE + "/teams/" + teamId + "/submit"), MEMBER_B, "{" + v("team", Long.parseLong(teamId)) + "}")
                .andExpect(status().isOk());

        String rejected = send(
                        post(BASE + "/teams/" + teamId + "/reject"),
                        ADMIN,
                        "{" + v("team", Long.parseLong(teamId)) + ",\"reason\":\"보류\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.effects.rejectedPerformanceMemberIds.length()")
                        .value(1))
                .andReturn()
                .getResponse()
                .getContentAsString();
        // 거절 대상은 팀원·팀장에서 빠지고 팀장은 남은 팀원이 승계, 응답 version은 DB와 같다
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM team_member tm JOIN performance_member pm ON pm.id = tm.performance_member_id"
                                + " WHERE pm.status = 'REJECTED'",
                        Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT leader_member_id FROM team WHERE id = ?", Long.class, teamId))
                .isEqualTo(2L);
        assertThat((String) JsonPath.read(rejected, "$.data.team.version"))
                .isEqualTo(String.valueOf(version("team", Long.parseLong(teamId))));
        send(get(BASE + "/teams/" + teamId), ADMIN, null)
                .andExpect(jsonPath("$.data.team.leaderMembershipId").value("2"))
                .andExpect(jsonPath("$.data.members.length()").value(1));

        // 재개 → 제출 → 승인
        long id = Long.parseLong(teamId);
        send(post(BASE + "/teams/" + teamId + "/reopen"), MEMBER, "{" + v("team", id) + "}")
                .andExpect(status().isOk());
        send(post(BASE + "/teams/" + teamId + "/submit"), MEMBER, "{" + v("team", id) + "}")
                .andExpect(status().isOk());
        send(post(BASE + "/teams/" + teamId + "/approve"), ADMIN, "{" + v("team", id) + "}")
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM team_member tm JOIN performance_member pm ON pm.id = tm.performance_member_id"
                                + " WHERE tm.team_id = ? AND pm.status <> 'APPROVED'",
                        Integer.class,
                        id))
                .isZero();
    }

    // ---------- #129 감사 detail ----------

    @Test
    @DisplayName("X-07 LOCKED 팀명 변경 감사에 이전·새 이름")
    void lockedRenameAudit() throws Exception {
        lockedTeam();
        send(patch(BASE + "/teams/100"), ADMIN, "{" + v("team", 100) + ",\"name\":\"새팀\",\"reason\":\"개명\"}")
                .andExpect(status().isOk());

        String detail = lockedAuditDetail("TEAM", 100);
        assertThat((String) JsonPath.read(detail, "$.teamName.from")).isEqualTo("1팀");
        assertThat((String) JsonPath.read(detail, "$.teamName.to")).isEqualTo("새팀");
    }

    @Test
    @DisplayName("X-07 LOCKED 팀장 교체 감사에 이전·새 팀장")
    void lockedLeaderAudit() throws Exception {
        lockedTeam();
        send(
                        put(BASE + "/teams/100/leader"),
                        ADMIN,
                        "{" + v("team", 100) + ",\"leaderMembershipId\":\"3\",\"reason\":\"교체\"}")
                .andExpect(status().isOk());

        String detail = lockedAuditDetail("TEAM", 100);
        assertThat((Integer) JsonPath.read(detail, "$.leaderMembershipId.from")).isEqualTo(2);
        assertThat((Integer) JsonPath.read(detail, "$.leaderMembershipId.to")).isEqualTo(3);
    }

    @Test
    @DisplayName("X-07 LOCKED 대표 파트 변경(API-066)도 감사 기록이 남고 partId 전·후 값이 있다")
    void lockedPartChangeAudit() throws Exception {
        lockedTeam();
        send(
                        patch(BASE + "/teams/100/members/1100"),
                        ADMIN,
                        "{" + v("team", 100) + ",\"partId\":\"2\",\"reason\":\"파트 변경\"}")
                .andExpect(status().isOk());

        String detail = lockedAuditDetail("TEAM", 100);
        assertThat((Object) JsonPath.read(detail, "$.partId.from")).isNull();
        assertThat((Integer) JsonPath.read(detail, "$.partId.to")).isEqualTo(2);
    }

    @Test
    @DisplayName("X-07 LOCKED 팀원 제외(removeSessions) 감사에 함께 지운 곡 ID")
    void lockedRemoveMemberAudit() throws Exception {
        lockedTeam();
        send(
                        delete(BASE + "/teams/100/members/1100"),
                        ADMIN,
                        "{" + v("team", 100) + ",\"removeSessions\":true,\"reason\":\"제외\"}")
                .andExpect(status().isOk());

        String detail = lockedAuditDetail("TEAM", 100);
        assertThat((Integer) JsonPath.read(detail, "$.removedPerformanceMemberId"))
                .isEqualTo(100);
        assertThat((Integer) JsonPath.read(detail, "$.removedSessionSongIds[0]"))
                .isEqualTo(100);
    }

    // ---------- #138 team-06·07·08·12 ----------

    @Test
    @DisplayName("API-065 팀원 추가는 201, PART_NOT_REGISTERED 경고에는 partName이 없다")
    void addMemberIs201WithoutPartName() throws Exception {
        team(100, "1팀", "APPROVED", 2);
        send(
                        post(BASE + "/teams/100/members"),
                        ADMIN,
                        "{" + v("team", 100) + ",\"membershipId\":\"3\",\"partId\":\"2\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.warnings[0].type").value("PART_NOT_REGISTERED"))
                .andExpect(jsonPath("$.data.warnings[0].partId").value("2"))
                .andExpect(jsonPath("$.data.warnings[0].partName").doesNotExist());
    }

    @Test
    @DisplayName("API-072 팀 범위 선정곡이 있으면 409 CONFIRMATION_REQUIRED, details[0].reason 포함")
    void rejectWithConfirmedSongsHasReason() throws Exception {
        team(100, "1팀", "SUBMITTED", 2);
        song(100, "곡A", null, "CONFIRMED");
        jdbc.update("UPDATE song SET candidate_team_id = 100 WHERE id = 100");

        send(post(BASE + "/teams/100/reject"), ADMIN, "{" + v("team", 100) + ",\"reason\":\"보류\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFIRMATION_REQUIRED"))
                .andExpect(jsonPath("$.error.details[0].reason").value("CONFIRMED_SONGS_IN_TEAM_SCOPE"))
                .andExpect(jsonPath("$.error.warnings[0].type").value("CONFIRMED_SONGS_IN_TEAM_SCOPE"));
    }

    @Test
    @DisplayName("API-064 빈 팀명은 400 reason NAME_REQUIRED")
    void blankNameIsNameRequired() throws Exception {
        team(100, "1팀", "APPROVED", 2);
        send(patch(BASE + "/teams/100"), ADMIN, "{" + v("team", 100) + ",\"name\":\"\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0].reason").value("NAME_REQUIRED"));
    }

    @Test
    @DisplayName("API-155 다른 공연의 팀은 404가 아니라 409 TEAM_NOT_APPROVED")
    void defaultTeamOfOtherPerformanceIs409() throws Exception {
        jdbc.update("INSERT INTO performance (id, club_id, title, preset, status, created_by_user_id)"
                + " VALUES (100, 1, '다른공연', 'CUSTOM', 'ACTIVE', 1)");
        jdbc.update("INSERT INTO team (id, performance_id, name, status, source, created_by_user_id)"
                + " VALUES (200, 100, '타공연팀', 'APPROVED', 'ADMIN', 1)");

        send(put(BASE + "/teams/default"), ADMIN, "{\"teamId\":\"200\",\"version\":\"0\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("STATE_CONFLICT"))
                .andExpect(jsonPath("$.error.details[0].reason").value("TEAM_NOT_APPROVED"));
    }
}
