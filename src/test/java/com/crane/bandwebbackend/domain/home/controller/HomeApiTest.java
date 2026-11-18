package com.crane.bandwebbackend.domain.home.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HomeApiTest extends HomeApiTestBase {

    private static final String OPEN = "'OPEN'";

    private String body(long userId) throws Exception {
        return mockMvc.perform(get(path("/home")).cookie(access(userId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private List<String> types(long userId) throws Exception {
        return JsonPath.read(body(userId), "$.data.todos[*].type");
    }

    private String label(long userId, String type) throws Exception {
        List<String> labels = JsonPath.read(body(userId), "$.data.todos[?(@.type=='" + type + "')].label");
        return labels.isEmpty() ? null : labels.get(0);
    }

    @Test
    @DisplayName("API 137: 배너 3종 - 비활성 동아리·졸업·임원의 공연일 지난 LOCKED 공연")
    void banners() throws Exception {
        long locked = performance("LOCKED");
        jdbc.update(
                "UPDATE performance SET perform_date = ? WHERE id = ?",
                LocalDate.now(java.time.ZoneOffset.ofHours(9)).minusDays(1),
                locked);
        long today = performance("LOCKED");
        jdbc.update(
                "UPDATE performance SET perform_date = ? WHERE id = ?",
                LocalDate.now(java.time.ZoneOffset.ofHours(9)),
                today);
        performance("LOCKED"); // 공연일 없음

        mockMvc.perform(get(path("/home")).cookie(access(adminUser)))
                .andExpect(jsonPath("$.data.banners.length()").value(1))
                .andExpect(jsonPath("$.data.banners[0].type").value("PERFORMANCE_FINISH_DUE"))
                .andExpect(jsonPath("$.data.banners[0].performanceId").value(String.valueOf(locked)))
                .andExpect(jsonPath("$.data.banners[0].performanceDate")
                        .value(LocalDate.now(java.time.ZoneOffset.ofHours(9))
                                .minusDays(1)
                                .toString()));
        // 임원이 아니면 공연 마감 배너 없음
        mockMvc.perform(get(path("/home")).cookie(access(memberUser)))
                .andExpect(jsonPath("$.data.banners.length()").value(0));

        long graduated = user();
        member(graduated, "MEMBER", "GRADUATED", "REGULAR", "JOIN_CODE", null);
        mockMvc.perform(get(path("/home")).cookie(access(graduated)))
                .andExpect(jsonPath("$.data.banners[0].type").value("MEMBER_GRADUATED"));

        jdbc.update("UPDATE club SET status = 'INACTIVE'");
        mockMvc.perform(get(path("/home")).cookie(access(memberUser)))
                .andExpect(jsonPath("$.data.banners[0].type").value("CLUB_INACTIVE"));
    }

    @Test
    @DisplayName("G-08 비활성 동아리는 열린 기능이 있어도 할 일 0개")
    void inactiveClubHasNoTodos() throws Exception {
        long perf = performance("ACTIVE");
        policy(perf, "application_state = " + OPEN);
        assertThat(types(memberUser)).containsExactly("PARTICIPATION_APPLY");
        jdbc.update("UPDATE club SET status = 'INACTIVE'");
        assertThat(types(memberUser)).isEmpty();
    }

    @Test
    @DisplayName("G-10 PARTICIPATION_APPLY: 열림·마감 전·참여 행 없음/WITHDRAWN일 때만, 객원은 제외")
    void applyTodo() throws Exception {
        long perf = performance("ACTIVE");
        policy(perf, "application_state = " + OPEN);
        assertThat(types(memberUser)).containsExactly("PARTICIPATION_APPLY");
        assertThat(types(extraGuestUser())).isEmpty();

        policy(perf, "application_state = 'CLOSED'");
        assertThat(types(memberUser)).isEmpty();
        policy(perf, "application_state = 'OPEN', application_deadline = '" + utcAgo(1) + "'");
        assertThat(types(memberUser)).isEmpty();

        policy(perf, "application_deadline = NULL");
        long pm = participation(perf, memberMid, "APPLIED");
        assertThat(types(memberUser)).isEmpty();
        jdbc.update("UPDATE performance_member SET status = 'REJECTED' WHERE id = ?", pm);
        assertThat(types(memberUser)).isEmpty(); // 결정 e
        jdbc.update("UPDATE performance_member SET status = 'WITHDRAWN' WHERE id = ?", pm);
        assertThat(types(memberUser)).containsExactly("PARTICIPATION_APPLY");
    }

    private long extraGuestUser() {
        long guest = user();
        member(guest, "MEMBER", "ACTIVE", "GUEST", "ADMIN_ADDED", null);
        return guest;
    }

    @Test
    @DisplayName("S-03 SONG_PROPOSE: 한도까지 제안하면 완료, 철회 곡은 제외, 한도 null이면 1곡, 팀 범위는 팀원만, LOCKED 제외")
    void proposeTodo() throws Exception {
        long perf = performance("ACTIVE");
        policy(perf, "candidate_state = 'OPEN', candidate_limit_per_member = 2");
        participation(perf, memberMid, "APPLIED");
        assertThat(label(memberUser, "SONG_PROPOSE")).isEqualTo("후보곡 제안 (0/2곡)");
        song(perf, "PROPOSED", memberMid, null);
        song(perf, "WITHDRAWN", memberMid, null);
        assertThat(label(memberUser, "SONG_PROPOSE")).isEqualTo("후보곡 제안 (1/2곡)");
        song(perf, "REJECTED", memberMid, null);
        assertThat(types(memberUser)).isEmpty();

        // 한도 null: 1곡 제안했으면 완료
        policy(perf, "candidate_limit_per_member = NULL");
        assertThat(types(memberUser)).isEmpty();
        jdbc.update("DELETE FROM song");
        assertThat(label(memberUser, "SONG_PROPOSE")).isEqualTo("후보곡 제안");

        // 팀 범위: 팀원이 아니면 제외
        policy(perf, "candidate_scope = 'TEAM_MEMBERS'");
        assertThat(types(memberUser)).isEmpty();
        long team = team(perf, "DRAFT");
        teamMember(team, perf, jdbc.queryForObject("SELECT id FROM performance_member", Long.class));
        assertThat(types(memberUser)).containsExactly("SONG_PROPOSE");

        jdbc.update("UPDATE performance SET status = 'LOCKED'");
        assertThat(types(memberUser)).isEmpty();
    }

    @Test
    @DisplayName("가불 PLAYABILITY: PROPOSED 곡 중 응답할 수 있는 곡 모두에 응답해야 완료(팀 후보곡은 그 팀원만)")
    void playabilityTodo() throws Exception {
        long perf = performance("ACTIVE");
        policy(perf, "playability_state = " + OPEN);
        long pm = participation(perf, memberMid, "APPROVED");
        long part = part();
        long answered = song(perf, "PROPOSED", adminMid, null);
        long todo = song(perf, "PROPOSED", adminMid, null);
        song(perf, "CONFIRMED", adminMid, null); // PROPOSED가 아니면 제외
        long otherTeam = team(perf, "APPROVED");
        song(perf, "PROPOSED", adminMid, otherTeam); // 내가 팀원이 아니라 응답 대상 아님
        playability(answered, perf, pm, part);
        assertThat(label(memberUser, "PLAYABILITY")).isEqualTo("가불 응답 (1곡 남음)");
        playability(todo, perf, pm, part);
        assertThat(types(memberUser)).isEmpty();

        // 팀원이 되면 팀 후보곡도 응답 대상
        teamMember(otherTeam, perf, pm);
        assertThat(label(memberUser, "PLAYABILITY")).isEqualTo("가불 응답 (1곡 남음)");

        policy(perf, "playability_deadline = '" + utcAgo(1) + "'");
        assertThat(types(memberUser)).isEmpty();
    }

    @Test
    @DisplayName("G-06 객원은 자기가 제안했거나 팀 후보곡 팀원인 곡만 응답 대상")
    void guestPlayabilityScope() throws Exception {
        long guestUser = user();
        long guestMid = member(guestUser, "MEMBER", "ACTIVE", "GUEST", "ADMIN_ADDED", null);
        long perf = performance("ACTIVE");
        policy(perf, "playability_state = " + OPEN);
        participation(perf, guestMid, "APPROVED");
        song(perf, "PROPOSED", adminMid, null);
        song(perf, "PROPOSED", guestMid, null);
        assertThat(label(guestUser, "PLAYABILITY")).isEqualTo("가불 응답 (1곡 남음)");

        // 참여하지 않은 공연은 보이지 않는다
        long hidden = performance("ACTIVE");
        policy(hidden, "application_state = " + OPEN);
        mockMvc.perform(get(path("/home")).cookie(access(guestUser)))
                .andExpect(jsonPath("$.data.myPerformances.length()").value(1))
                .andExpect(jsonPath("$.data.todos.length()").value(1));
    }

    @Test
    @DisplayName("V-05 VOTE: 찬성 방식 한도까지 투표해야 완료, 한도 null·SCORE는 1건이면 완료, PROPOSED 곡만 센다")
    void voteTodo() throws Exception {
        long perf = performance("ACTIVE");
        policy(perf, "vote_state = 'OPEN', vote_max_choices = 2");
        long pm = participation(perf, memberMid, "APPROVED");
        long s1 = song(perf, "PROPOSED", adminMid, null);
        long s2 = song(perf, "PROPOSED", adminMid, null);
        long confirmed = song(perf, "CONFIRMED", adminMid, null);
        assertThat(label(memberUser, "VOTE")).isEqualTo("투표 (선택 0/2곡)");
        vote(s1, perf, pm);
        vote(confirmed, perf, pm); // PROPOSED가 아닌 곡의 투표는 세지 않는다
        assertThat(label(memberUser, "VOTE")).isEqualTo("투표 (선택 1/2곡)");
        vote(s2, perf, pm);
        assertThat(types(memberUser)).isEmpty();

        // 한도 null: 1건이면 완료
        policy(perf, "vote_max_choices = NULL");
        assertThat(types(memberUser)).isEmpty();
        jdbc.update("DELETE FROM song_vote WHERE song_id = ?", s1);
        jdbc.update("DELETE FROM song_vote WHERE song_id = ?", s2);
        assertThat(label(memberUser, "VOTE")).isEqualTo("투표");

        // SCORE 방식은 한도가 있어도 무시
        policy(perf, "vote_method = 'SCORE', vote_max_choices = 3");
        assertThat(label(memberUser, "VOTE")).isEqualTo("투표");
        vote(s1, perf, pm);
        assertThat(types(memberUser)).isEmpty();

        // 희망자(APPLIED)는 투표 대상이 아님
        jdbc.update("DELETE FROM song_vote");
        jdbc.update("UPDATE performance_member SET status = 'APPLIED'");
        assertThat(types(memberUser)).isEmpty();
    }

    @Test
    @DisplayName("V-05 한도가 투표할 수 있는 PROPOSED 곡 수보다 커도 모든 곡에 투표하면 VOTE 할 일과 배지에서 제외")
    void voteTodoCappedByVotableSongs() throws Exception {
        long perf = performance("ACTIVE");
        policy(perf, "vote_state = 'OPEN', vote_max_choices = 3");
        long pm = participation(perf, memberMid, "APPROVED");
        long s1 = song(perf, "PROPOSED", adminMid, null);
        long s2 = song(perf, "PROPOSED", adminMid, null);
        // 문구의 목표도 한도(3)가 아니라 투표할 수 있는 곡 수(2)로 보인다
        assertThat(label(memberUser, "VOTE")).isEqualTo("투표 (선택 0/2곡)");
        vote(s1, perf, pm);
        assertThat(label(memberUser, "VOTE")).isEqualTo("투표 (선택 1/2곡)");
        vote(s2, perf, pm);
        assertThat(types(memberUser)).isEmpty();
        mockMvc.perform(get(path("/home/badge")).cookie(access(memberUser)))
                .andExpect(jsonPath("$.data.count").value(0));
    }

    @Test
    @DisplayName("V-05 투표할 PROPOSED 곡이 없으면 VOTE 할 일과 배지에서 제외")
    void voteTodoNeedsVotableSong() throws Exception {
        long perf = performance("ACTIVE");
        policy(perf, "vote_state = 'OPEN'");
        participation(perf, memberMid, "APPROVED");
        song(perf, "CONFIRMED", adminMid, null);
        assertThat(types(memberUser)).isEmpty();
        mockMvc.perform(get(path("/home/badge")).cookie(access(memberUser)))
                .andExpect(jsonPath("$.data.count").value(0));
        song(perf, "PROPOSED", adminMid, null);
        assertThat(types(memberUser)).containsExactly("VOTE");
    }

    @Test
    @DisplayName("AV-01 AVAILABILITY: ACTIVE·LOCKED, APPROVED, 저장된 슬롯이 없을 때만")
    void availabilityTodo() throws Exception {
        long active = performance("ACTIVE");
        long locked = performance("LOCKED");
        policy(active, "availability_state = " + OPEN);
        policy(locked, "availability_state = " + OPEN);
        long pm1 = participation(active, memberMid, "APPROVED");
        participation(locked, memberMid, "APPROVED");
        assertThat(types(memberUser)).containsExactly("AVAILABILITY", "AVAILABILITY");
        slot(pm1);
        assertThat(types(memberUser)).containsExactly("AVAILABILITY");
        policy(locked, "availability_deadline = '" + utcAgo(1) + "'");
        assertThat(types(memberUser)).isEmpty();
    }

    @Test
    @DisplayName("T-02 TEAM_SUBMIT: 제출이 열린 ACTIVE 공연에서 내가 팀장인 DRAFT 팀마다 하나, teamId 포함")
    void teamSubmitTodo() throws Exception {
        long perf = performance("ACTIVE");
        policy(perf, "team_submission_state = " + OPEN);
        long pm = participation(perf, memberMid, "APPROVED");
        long t1 = team(perf, "DRAFT");
        long t2 = team(perf, "DRAFT");
        long submitted = team(perf, "SUBMITTED");
        for (long t : new long[] {t1, t2, submitted}) {
            teamMember(t, perf, pm);
            leader(t, pm);
        }
        long notLeader = team(perf, "DRAFT");
        teamMember(notLeader, perf, pm);
        mockMvc.perform(get(path("/home")).cookie(access(memberUser)))
                .andExpect(jsonPath("$.data.todos.length()").value(2))
                .andExpect(jsonPath("$.data.todos[0].type").value("TEAM_SUBMIT"))
                .andExpect(jsonPath("$.data.todos[0].teamId").value(String.valueOf(t1)))
                .andExpect(jsonPath("$.data.todos[0].label").value("작성 중인 팀 신청서"))
                .andExpect(jsonPath("$.data.todos[1].teamId").value(String.valueOf(t2)));
        policy(perf, "team_submission_state = 'CLOSED'");
        assertThat(types(memberUser)).isEmpty();
    }

    @Test
    @DisplayName("G-10 정렬: 마감 임박 순, 마감 없는 항목은 뒤")
    void sortByDeadline() throws Exception {
        long later = performance("ACTIVE");
        long none = performance("ACTIVE");
        long sooner = performance("ACTIVE");
        policy(later, "application_state = 'OPEN', application_deadline = '" + utcAfter(2) + "'");
        policy(none, "application_state = 'OPEN'");
        policy(sooner, "application_state = 'OPEN', application_deadline = '" + utcAfter(1) + "'");
        mockMvc.perform(get(path("/home")).cookie(access(memberUser)))
                .andExpect(jsonPath("$.data.todos[0].performanceId").value(String.valueOf(sooner)))
                .andExpect(jsonPath("$.data.todos[1].performanceId").value(String.valueOf(later)))
                .andExpect(jsonPath("$.data.todos[2].performanceId").value(String.valueOf(none)))
                .andExpect(jsonPath("$.data.todos[2].deadline").doesNotExist())
                .andExpect(jsonPath("$.data.todos[0].deadline").value(org.hamcrest.Matchers.endsWith("+09:00")));
    }

    @Test
    @DisplayName("일정: 내가 참석자인 CONFIRMED 일정만 시작 순으로 3개")
    void upcomingSchedules() throws Exception {
        long perf = performance("ACTIVE");
        participation(perf, memberMid, "APPROVED");
        LocalDateTime base =
                LocalDateTime.now(ZoneOffset.UTC).withMinute(0).withSecond(0).withNano(0);
        for (int day : new int[] {4, 2, 1, 3}) {
            schedule(perf, "CONFIRMED", base.plusDays(day), "장소" + day);
        }
        schedule(perf, "TENTATIVE", base.plusHours(5), "임시");
        schedule(perf, "CANCELED", base.plusHours(6), "취소");
        mockMvc.perform(get(path("/home")).cookie(access(memberUser)))
                .andExpect(jsonPath("$.data.upcomingSchedules.length()").value(3))
                .andExpect(jsonPath("$.data.upcomingSchedules[0].location").value("장소1"))
                .andExpect(jsonPath("$.data.upcomingSchedules[1].location").value("장소2"))
                .andExpect(jsonPath("$.data.upcomingSchedules[2].location").value("장소3"))
                .andExpect(jsonPath("$.data.upcomingSchedules[0].performanceId").value(String.valueOf(perf)))
                .andExpect(jsonPath("$.data.upcomingSchedules[0].type").value("PRACTICE"))
                .andExpect(
                        jsonPath("$.data.upcomingSchedules[0].performanceName").isNotEmpty());
    }

    private void schedule(long perf, String status, LocalDateTime start, String location) {
        jdbc.update(
                "INSERT INTO practice_schedule (performance_id, status, title, start_at, end_at, location,"
                        + " for_all_participants, created_by_user_id) VALUES (?, ?, '합주', ?, ?, ?, TRUE, ?)",
                perf,
                status,
                start,
                start.plusHours(2),
                location,
                adminUser);
    }

    @Test
    @DisplayName("내 공연: ACTIVE·LOCKED 중 내 참여가 APPLIED·APPROVED, 팀 이름은 APPROVED 팀만, 곡 수는 확정곡 연주 곡 수")
    void myPerformances() throws Exception {
        long active = performance("ACTIVE");
        long pm = participation(active, memberMid, "APPROVED");
        long approvedTeam = team(active, "APPROVED");
        teamMember(approvedTeam, active, pm);
        teamMember(team(active, "DRAFT"), active, pm);
        long part = part();
        long song = song(active, "CONFIRMED", adminMid, null);
        jdbc.update(
                "INSERT INTO song_session_member (song_id, performance_id, performance_member_id, part_id)"
                        + " VALUES (?, ?, ?, ?)",
                song,
                active,
                pm,
                part);
        long locked = performance("LOCKED");
        participation(locked, memberMid, "APPLIED");
        participation(performance("ACTIVE"), memberMid, "REJECTED");
        participation(performance("FINISHED"), memberMid, "APPROVED");
        performance("ACTIVE"); // 참여 없음

        mockMvc.perform(get(path("/home")).cookie(access(memberUser)))
                .andExpect(jsonPath("$.data.myPerformances.length()").value(2))
                .andExpect(jsonPath("$.data.myPerformances[0].performanceId").value(String.valueOf(active)))
                .andExpect(jsonPath("$.data.myPerformances[0].myStatus").value("APPROVED"))
                .andExpect(
                        jsonPath("$.data.myPerformances[0].teamNames.length()").value(1))
                .andExpect(jsonPath("$.data.myPerformances[0].songCount").value(1))
                .andExpect(jsonPath("$.data.myPerformances[1].performanceId").value(String.valueOf(locked)))
                .andExpect(jsonPath("$.data.myPerformances[1].myStatus").value("APPLIED"))
                .andExpect(jsonPath("$.data.myPerformances[1].songCount").value(0));
    }

    @Test
    @DisplayName("§2.1 일반 부원에게 DRAFT 공연은 할 일과 내 공연에 나오지 않는다")
    void draftHiddenFromMember() throws Exception {
        long draft = performance("DRAFT");
        policy(draft, "candidate_state = 'OPEN'");
        participation(draft, memberMid, "APPROVED");
        assertThat(types(memberUser)).isEmpty();
        mockMvc.perform(get(path("/home")).cookie(access(memberUser)))
                .andExpect(jsonPath("$.data.myPerformances.length()").value(0));
    }

    @Test
    @DisplayName("G-06 객원에게 참여하지 않은 공연은 할 일과 내 공연에 나오지 않는다")
    void nonParticipatingPerformanceHiddenFromGuest() throws Exception {
        long guestUser = user();
        member(guestUser, "MEMBER", "ACTIVE", "GUEST", "ADMIN_ADDED", null);
        long perf = performance("ACTIVE");
        policy(perf, "application_state = " + OPEN);
        assertThat(types(guestUser)).isEmpty();
        mockMvc.perform(get(path("/home")).cookie(access(guestUser)))
                .andExpect(jsonPath("$.data.myPerformances.length()").value(0));
    }

    @Test
    @DisplayName("소속이 없으면 404")
    void notMember() throws Exception {
        mockMvc.perform(get(path("/home")).cookie(access(user()))).andExpect(status().isNotFound());
    }
}
