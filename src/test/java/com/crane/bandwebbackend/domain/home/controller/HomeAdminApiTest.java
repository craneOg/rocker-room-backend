package com.crane.bandwebbackend.domain.home.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HomeAdminApiTest extends HomeApiTestBase {

    // 가입 대기 2, 유효 초대 1(+만료 초대 1), 활동 2(+휴학 1, 졸업 1, 객원 활동 1),
    // 진행 중 공연(ACTIVE)에 SUBMITTED 팀 1·APPLIED 1·심사 대기 1, FINISHED 공연에는 같은 것을 두고 센다
    private void seed() {
        extraMember("MEMBER", "PENDING", "REGULAR");
        extraMember("MEMBER", "PENDING", "REGULAR");
        member(user(), "MEMBER", "INVITED", "REGULAR", "ADMIN_ADDED", utcAgo(1));
        member(user(), "MEMBER", "INVITED", "REGULAR", "ADMIN_ADDED", utcAgo(60)); // 만료
        extraMember("MEMBER", "ON_LEAVE", "REGULAR");
        extraMember("MEMBER", "GRADUATED", "REGULAR");
        long guest = extraMember("MEMBER", "ACTIVE", "GUEST");
        extraMember("CLUB_ADMIN", "ON_LEAVE", "REGULAR");

        long active = performance("ACTIVE");
        long finished = performance("FINISHED");
        performance("DRAFT");
        team(active, "SUBMITTED");
        team(active, "APPROVED");
        team(finished, "SUBMITTED");
        participation(active, guest, "APPLIED");
        participation(finished, guest, "APPLIED");
        audition(active, guest, null);
        audition(active, guest, "PASSED");
        audition(finished, guest, null);
    }

    @Test
    @DisplayName("API 117: 만료 초대·종료 공연 제외, 활동 인원은 ACTIVE만, 공연 상태별 수는 0 제외, 경고 수는 미충족 점검 항목 수")
    void dashboard() throws Exception {
        seed();
        mockMvc.perform(get(path("/dashboard")).cookie(access(adminUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pendingMemberships").value(2))
                .andExpect(jsonPath("$.data.invitedMemberships").value(1))
                .andExpect(jsonPath("$.data.activeMembers").value(3)) // 임원·부원·객원(휴학 제외)
                .andExpect(jsonPath("$.data.performancesByStatus.ACTIVE").value(1))
                .andExpect(jsonPath("$.data.performancesByStatus.DRAFT").value(1))
                .andExpect(jsonPath("$.data.performancesByStatus.FINISHED").value(1))
                .andExpect(jsonPath("$.data.performancesByStatus.LOCKED").doesNotExist())
                .andExpect(jsonPath("$.data.pendingTeams").value(1))
                .andExpect(jsonPath("$.data.pendingPerformanceMembers").value(1))
                .andExpect(jsonPath("$.data.warningsCount").value(3)); // ACTIVE 공연의 L-04·L-05·L-06만
    }

    @Test
    @DisplayName("API 138: pending·clubStats 집계(ACTIVE+ON_LEAVE, 객원은 부분집합), 점검은 ACTIVE 공연만")
    void homeAdmin() throws Exception {
        seed();
        mockMvc.perform(get(path("/home/admin")).cookie(access(adminUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pending.joinRequests").value(2))
                .andExpect(jsonPath("$.data.pending.invitesWaiting").value(1))
                .andExpect(jsonPath("$.data.pending.participationRequests").value(1))
                .andExpect(jsonPath("$.data.pending.teamReviews").value(1))
                .andExpect(jsonPath("$.data.pending.auditionReviews").value(1))
                .andExpect(jsonPath("$.data.performanceChecks", hasSize(1)))
                .andExpect(jsonPath("$.data.performanceChecks[0].satisfied").value(8))
                .andExpect(jsonPath("$.data.performanceChecks[0].total").value(11))
                .andExpect(jsonPath("$.data.clubStats.activeMembers").value(5))
                .andExpect(jsonPath("$.data.clubStats.admins").value(2))
                .andExpect(jsonPath("$.data.clubStats.graduated").value(1))
                .andExpect(jsonPath("$.data.clubStats.guests").value(1))
                .andExpect(jsonPath("$.data.clubStats.performances.draft").value(1))
                .andExpect(jsonPath("$.data.clubStats.performances.active").value(1))
                .andExpect(jsonPath("$.data.clubStats.performances.locked").value(0))
                .andExpect(jsonPath("$.data.clubStats.performances.finished").value(1));
    }

    @Test
    @DisplayName("API 117·138: 임원이 아니면 403, 소속이 없으면 404")
    void nonAdmin() throws Exception {
        for (String p : new String[] {"/dashboard", "/home/admin"}) {
            mockMvc.perform(get(path(p)).cookie(access(memberUser))).andExpect(status().isForbidden());
            mockMvc.perform(get(path(p)).cookie(access(user()))).andExpect(status().isNotFound());
        }
    }

    @Test
    @DisplayName("API 138: 점검 위반 줄을 checkId로 묶어 count·satisfied·kind로 변환, 열린 기능만 응답률")
    void performanceChecksAndRates() throws Exception {
        long m1 = extraMember("MEMBER", "ACTIVE", "REGULAR");
        long m2 = extraMember("MEMBER", "ACTIVE", "REGULAR");
        long m3 = extraMember("MEMBER", "ACTIVE", "REGULAR");
        long m4 = extraMember("MEMBER", "ACTIVE", "REGULAR");
        long part = part();
        long first = performance("ACTIVE");
        long second = performance("ACTIVE");
        // 첫 공연: 가불만 열림, 투표는 마감 지남, 가능 시간은 닫힘
        policy(
                first,
                "playability_state = 'OPEN', vote_state = 'OPEN', vote_deadline = '" + utcAgo(1) + "',"
                        + " availability_state = 'CLOSED'");
        // 둘째 공연: 투표·가능 시간만 열림
        policy(second, "vote_state = 'OPEN', availability_state = 'OPEN'");
        long a1 = participation(first, m1, "APPROVED");
        participation(first, m2, "APPROVED");
        long a3 = participation(first, m3, "APPLIED");
        long a4 = participation(first, m4, "REJECTED");
        long song = song(first, "PROPOSED", adminMid, null);
        playability(song, first, a1, part);
        playability(song, first, a3, part);
        playability(song, first, a4, part); // 거절된 참여자의 응답은 세지 않는다
        long b1 = participation(second, m1, "APPROVED");
        long b2 = participation(second, m2, "APPROVED");
        long b4 = participation(second, m4, "REJECTED");
        long song2 = song(second, "PROPOSED", adminMid, null);
        vote(song2, second, b1);
        vote(song2, second, b4);
        slot(b2);

        String base = "$.data.performanceChecks[0]";
        mockMvc.perform(get(path("/home/admin")).cookie(access(adminUser)))
                .andExpect(jsonPath("$.data.performanceChecks", hasSize(2)))
                .andExpect(jsonPath(base + ".performanceId").value(String.valueOf(first)))
                .andExpect(jsonPath(base + ".total").value(11))
                .andExpect(jsonPath(base + ".satisfied").value(9)) // L-05(승인 대기)·L-08(참여곡 없음)만 위반
                .andExpect(jsonPath(base + ".items[?(@.checkId=='L-05')].count").value(contains(1)))
                .andExpect(jsonPath(base + ".items[?(@.checkId=='L-05')].kind").value(contains("BLOCKER")))
                .andExpect(jsonPath(base + ".items[?(@.checkId=='L-05')].satisfied")
                        .value(contains(false)))
                .andExpect(jsonPath(base + ".items[?(@.checkId=='L-08')].count").value(contains(2)))
                .andExpect(jsonPath(base + ".items[?(@.checkId=='L-08')].kind").value(contains("WARNING")))
                .andExpect(jsonPath(base + ".items[?(@.checkId=='L-01')].satisfied")
                        .value(contains(true)))
                .andExpect(jsonPath(base + ".items[?(@.checkId=='L-01')].count").value(contains(0)))
                .andExpect(
                        jsonPath(base + ".responseRates.playability.answered").value(2))
                .andExpect(jsonPath(base + ".responseRates.playability.total").value(3))
                .andExpect(jsonPath(base + ".responseRates.vote").doesNotExist())
                .andExpect(jsonPath(base + ".responseRates.availability").doesNotExist())
                .andExpect(jsonPath("$.data.performanceChecks[1].responseRates.playability")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.performanceChecks[1].responseRates.vote.answered")
                        .value(1))
                .andExpect(jsonPath("$.data.performanceChecks[1].responseRates.vote.total")
                        .value(2))
                .andExpect(jsonPath("$.data.performanceChecks[1].responseRates.availability.answered")
                        .value(1))
                .andExpect(jsonPath("$.data.performanceChecks[1].responseRates.availability.total")
                        .value(2));
        // 경고 수 = 두 공연의 미충족 항목(첫 공연 2 + 둘째 공연 L-08 1)
        mockMvc.perform(get(path("/dashboard")).cookie(access(adminUser)))
                .andExpect(jsonPath("$.data.warningsCount").value(3));
    }
}
