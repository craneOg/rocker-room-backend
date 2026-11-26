package com.crane.bandwebbackend.domain.song.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crane.bandwebbackend.domain.song.SongApiTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 곡 API 명세 정합성: #114 #116 #117 #118 #119 #120 #129 #138
class SongSpecApiTest extends SongApiTestSupport {

    // ---------- #114 API-086 ----------

    @Test
    @DisplayName("API-086 곡 version이 옛 값이면 409 VERSION_CONFLICT, 없으면 400, 맞으면 201")
    void createTeamFromSongChecksVersion() throws Exception {
        song(100, "곡A", null, "CONFIRMED");
        session(100, 2, 1);
        long version = version("song", 100);

        send(post(BASE + "/teams"), ADMIN, "{\"sourceSongId\":\"100\",\"name\":\"곡A팀\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0].field").value("version"));
        send(
                        post(BASE + "/teams"),
                        ADMIN,
                        "{\"sourceSongId\":\"100\",\"version\":\"" + (version + 5) + "\",\"name\":\"곡A팀\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("VERSION_CONFLICT"));
        send(
                        post(BASE + "/teams"),
                        ADMIN,
                        "{\"sourceSongId\":\"100\",\"version\":\"" + version + "\",\"name\":\"곡A팀\"}")
                .andExpect(status().isCreated());
    }

    // ---------- #116 song-07·08 ----------

    @Test
    @DisplayName("S-08 API-076 직접 확정에 sessions 빈 배열은 연주자 없이 확정")
    void directConfirmWithEmptySessions() throws Exception {
        send(post(BASE + "/songs"), ADMIN, "{\"title\":\"A\",\"status\":\"CONFIRMED\",\"sessions\":[]}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.resource.status").value("CONFIRMED"));
    }

    @Test
    @DisplayName("S-08 API-080 확정에 sessions 빈 배열은 연주자 없이 확정")
    void confirmWithEmptySessions() throws Exception {
        song(100, "곡A", null, "PROPOSED");
        send(post(BASE + "/songs/100/confirm"), ADMIN, "{\"version\":\"" + version("song", 100) + "\",\"sessions\":[]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resource.status").value("CONFIRMED"));
    }

    @Test
    @DisplayName("API-076 등록과 함께 보낸 requirements가 저장되고, 중복 파트는 400")
    void createStoresRequirements() throws Exception {
        String created = send(
                        post(BASE + "/songs"),
                        ADMIN,
                        "{\"title\":\"B\",\"requirements\":[{\"partId\":\"2\",\"requiredCount\":1}]}")
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String id = JsonPath.read(created, "$.data.resource.id");
        send(get(BASE + "/songs/" + id), ADMIN, null)
                .andExpect(jsonPath("$.data.requirements.length()").value(1))
                .andExpect(jsonPath("$.data.requirements[0].partId").value("2"))
                .andExpect(jsonPath("$.data.requirements[0].requiredCount").value(1));

        send(
                        post(BASE + "/songs"),
                        ADMIN,
                        "{\"title\":\"C\",\"requirements\":[{\"partId\":\"2\",\"requiredCount\":1},{\"partId\":\"2\",\"requiredCount\":2}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].reason").value("DUPLICATE_PART"));
    }

    // ---------- #117 song-04 ----------

    @Test
    @DisplayName("API-077 schedules.eventType, auditions.type·applicantName·judgedAt")
    void detailUsesSpecFieldNames() throws Exception {
        song(100, "곡A", null, "PROPOSED");
        jdbc.update("INSERT INTO practice_schedule (id, performance_id, schedule_type, status, title, start_at, end_at,"
                + " created_by_user_id) VALUES (100, 1, 'PRACTICE', 'CONFIRMED', '합주', '2026-12-01 10:00:00',"
                + " '2026-12-01 12:00:00', 1)");
        jdbc.update("INSERT INTO practice_schedule_song (practice_schedule_id, song_id, performance_id)"
                + " VALUES (100, 100, 1)");
        jdbc.update(
                "INSERT INTO audition_entry (performance_id, song_id, performance_member_id, part_id, result,"
                        + " reviewed_by_user_id, reviewed_at, created_by_user_id) VALUES (1, 100, 2, 1, 'PASSED', 1, '2026-12-02 10:00:00', 1)");
        // 곡 심사(지원자 없음). 심사 대기라 result·reviewed 모두 NULL
        jdbc.update("INSERT INTO audition_entry (performance_id, song_id, created_by_user_id) VALUES (1, 100, 1)");

        send(get(BASE + "/songs/100"), ADMIN, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.schedules[0].eventType").value("PRACTICE"))
                .andExpect(jsonPath("$.data.schedules[0].scheduleType").doesNotExist())
                .andExpect(jsonPath("$.data.auditions[0].type").value("SESSION"))
                .andExpect(jsonPath("$.data.auditions[0].applicantPerformanceMemberId")
                        .value("2"))
                .andExpect(jsonPath("$.data.auditions[0].applicantName").value("부원A"))
                .andExpect(jsonPath("$.data.auditions[0].judgedAt").value("2026-12-02T19:00:00+09:00"))
                .andExpect(jsonPath("$.data.auditions[0].reviewedAt").doesNotExist())
                .andExpect(jsonPath("$.data.auditions[1].type").value("SONG"))
                .andExpect(jsonPath("$.data.auditions[1].applicantName").doesNotExist());
    }

    // ---------- #118 M-02 ----------

    @Test
    @DisplayName("M-02 ACTIVE 공연에서 참여 희망(APPLIED) 참여자를 연주자로 저장하면 422")
    void appliedParticipantCannotBeSession() throws Exception {
        participant(100, 3, "APPLIED", "APPLICATION");
        song(100, "곡A", null, "PROPOSED");

        send(
                        put(BASE + "/songs/100/sessions"),
                        ADMIN,
                        "{\"version\":\"" + version("song", 100)
                                + "\",\"items\":[{\"performanceMemberId\":\"100\",\"partId\":\"1\"}]}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("RULE_VIOLATION"))
                .andExpect(jsonPath("$.error.details[0].reason").value("SESSION_MEMBER_INVALID"));
        // APPROVED 참여자는 성공
        send(
                        put(BASE + "/songs/100/sessions"),
                        ADMIN,
                        "{\"version\":\"" + version("song", 100)
                                + "\",\"items\":[{\"performanceMemberId\":\"2\",\"partId\":\"1\"}]}")
                .andExpect(status().isOk());
    }

    // ---------- #119 API-079 ----------

    @Test
    @DisplayName("X-02·X-07 LOCKED 필요 편성 저장은 사유 필수, 사유가 있으면 전·후 값을 감사 기록")
    void requirementsInLockedNeedReasonAndAudit() throws Exception {
        song(100, "곡A", null, "PROPOSED");
        lockPerformance();
        String items = "\"items\":[{\"partId\":\"2\",\"requiredCount\":2}]";

        send(
                        put(BASE + "/songs/100/requirements"),
                        ADMIN,
                        "{\"version\":\"" + version("song", 100) + "\"," + items + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].reason").value("REASON_REQUIRED"));
        send(
                        put(BASE + "/songs/100/requirements"),
                        ADMIN,
                        "{\"version\":\"" + version("song", 100) + "\"," + items + ",\"reason\":\"편성 조정\"}")
                .andExpect(status().isOk());

        String detail = lockedAuditDetail("SONG", 100);
        assertThat((Object) JsonPath.read(detail, "$.requirements.from"))
                .asList()
                .isEmpty();
        assertThat((Integer) JsonPath.read(detail, "$.requirements.to[0].partId"))
                .isEqualTo(2);
        assertThat((Integer) JsonPath.read(detail, "$.requirements.to[0].requiredCount"))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("S-09 철회된 곡의 필요 편성 저장은 409 STATE_CONFLICT")
    void requirementsOfWithdrawnSongIs409() throws Exception {
        jdbc.update("INSERT INTO song (id, performance_id, title, status, withdrawal_type, proposed_by_membership_id)"
                + " VALUES (100, 1, '곡A', 'WITHDRAWN', 'ADMIN', 1)");

        send(
                        put(BASE + "/songs/100/requirements"),
                        ADMIN,
                        "{\"version\":\"" + version("song", 100) + "\",\"items\":[]}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("STATE_CONFLICT"))
                .andExpect(jsonPath("$.error.details[0].reason").value("INVALID_STATE"));
    }

    // ---------- #120 API-075 ----------

    @Test
    @DisplayName("API-075 q는 제목·아티스트, assignedTeamId는 담당 팀으로 거른다")
    void listFiltersByQueryAndAssignedTeam() throws Exception {
        team(100, "1팀", "APPROVED", 2);
        confirmedSong(100, "Alpha", 1, 100, 2);
        jdbc.update("UPDATE song SET artist = 'Beatles' WHERE id = 100");
        song(101, "Beta", "Queen", "PROPOSED");

        send(get(BASE + "/songs?q=alp"), ADMIN, null)
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value("100"));
        send(get(BASE + "/songs").param("q", " QUEEN "), ADMIN, null)
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value("101"));
        send(get(BASE + "/songs?q=없는곡"), ADMIN, null)
                .andExpect(jsonPath("$.data.totalElements").value(0));
        send(get(BASE + "/songs").param("q", " "), ADMIN, null)
                .andExpect(jsonPath("$.data.totalElements").value(2));
        send(get(BASE + "/songs?assignedTeamId=100"), ADMIN, null)
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value("100"));
    }

    // ---------- #129 song-14 ----------

    @Test
    @DisplayName("X-07 LOCKED 연주자 저장 감사에 연주자 전·후 값이 담긴다")
    void lockedSessionsAuditHasFromTo() throws Exception {
        team(100, "1팀", "APPROVED", 2);
        confirmedSong(100, "곡A", 1, 100, 2);
        lockPerformance();

        send(
                        put(BASE + "/songs/100/sessions"),
                        ADMIN,
                        "{\"version\":\"" + version("song", 100)
                                + "\",\"items\":[{\"performanceMemberId\":\"2\",\"partId\":\"2\"}],\"reason\":\"파트 교체\"}")
                .andExpect(status().isOk());

        String detail = lockedAuditDetail("SONG", 100);
        assertThat((Integer) JsonPath.read(detail, "$.sessions.from[0].partId")).isEqualTo(1);
        assertThat((Integer) JsonPath.read(detail, "$.sessions.to[0].partId")).isEqualTo(2);
        assertThat((Integer) JsonPath.read(detail, "$.sessions.to[0].performanceMemberId"))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("X-07 LOCKED 직접 확정 감사에 담당 팀·연주자 값이 담긴다")
    void lockedDirectConfirmAuditHasTeamAndSessions() throws Exception {
        team(100, "1팀", "APPROVED", 2);
        confirmedSong(100, "곡A", 1, 100, 2);
        lockPerformance();

        String created = send(
                        post(BASE + "/songs"),
                        ADMIN,
                        "{\"title\":\"곡B\",\"status\":\"CONFIRMED\",\"setlistOrder\":2,\"assignedTeamId\":\"100\","
                                + "\"sessions\":[{\"performanceMemberId\":\"2\",\"partId\":\"1\"}],\"reason\":\"추가\"}")
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long id = Long.parseLong(JsonPath.read(created, "$.data.resource.id"));

        String detail = lockedAuditDetail("SONG", id);
        assertThat(((Number) JsonPath.read(detail, "$.assignedTeamId.to")).longValue())
                .isEqualTo(100L);
        assertThat((Integer) JsonPath.read(detail, "$.sessions.to[0].performanceMemberId"))
                .isEqualTo(2);
    }

    // ---------- #138 song-09·11·12 ----------

    @Test
    @DisplayName("S-02 팀별 후보 범위에서 팀을 안 정하면 임원도 422, 팀원이 아니면 422")
    void teamScopeCandidateRules() throws Exception {
        jdbc.update("UPDATE performance_policy SET candidate_scope = 'TEAM_MEMBERS' WHERE performance_id = 1");
        team(100, "1팀", "APPROVED", 2);

        send(post(BASE + "/songs"), ADMIN, "{\"title\":\"A\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("RULE_VIOLATION"))
                .andExpect(jsonPath("$.error.details[0].reason").value("TEAM_REQUIRED"));
        // 부원B(참여자 100)는 팀 100의 팀원이 아님
        participant(100, 3, "APPROVED", "APPLICATION");
        send(post(BASE + "/songs"), MEMBER_B, "{\"title\":\"A\",\"candidateTeamId\":\"100\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("RULE_VIOLATION"));
        send(post(BASE + "/songs"), MEMBER, "{\"title\":\"A\",\"candidateTeamId\":\"100\"}")
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("API-082 선정곡 제외 확인 요청은 details[0].reason CONFIRMED_SONG_REJECTION")
    void rejectConfirmedSongHasReason() throws Exception {
        team(100, "1팀", "APPROVED", 2);
        confirmedSong(100, "곡A", 1, 100, 2);

        send(post(BASE + "/songs/100/reject"), ADMIN, "{\"version\":\"" + version("song", 100) + "\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFIRMATION_REQUIRED"))
                .andExpect(jsonPath("$.error.details[0].reason").value("CONFIRMED_SONG_REJECTION"))
                .andExpect(jsonPath("$.error.warnings[0].type").value("ASSIGNMENT_RELEASED"));
    }

    @Test
    @DisplayName("API-156·157 상태 오류 reason은 NOT_REJECTED·NOT_PROPOSED")
    void stateReasonsOfReconsiderAndRemove() throws Exception {
        song(100, "곡A", null, "PROPOSED");
        song(101, "곡B", null, "REJECTED");

        send(post(BASE + "/songs/100/reconsider"), ADMIN, "{\"version\":\"" + version("song", 100) + "\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("STATE_CONFLICT"))
                .andExpect(jsonPath("$.error.details[0].reason").value("NOT_REJECTED"));
        send(post(BASE + "/songs/101/remove"), ADMIN, "{\"version\":\"" + version("song", 101) + "\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("STATE_CONFLICT"))
                .andExpect(jsonPath("$.error.details[0].reason").value("NOT_PROPOSED"));
    }
}
