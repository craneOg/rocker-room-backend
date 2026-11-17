package com.crane.bandwebbackend.domain.home.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HomeBadgeApiTest extends HomeApiTestBase {

    @Test
    @DisplayName("API 139: 임원은 처리 대기 합계, 일반 부원은 0")
    void badge() throws Exception {
        extraMember("MEMBER", "PENDING", "REGULAR");
        long applicant = extraMember("MEMBER", "ACTIVE", "REGULAR");
        long performance = performance("ACTIVE");
        participation(performance, applicant, "APPLIED");
        team(performance, "SUBMITTED");

        mockMvc.perform(get(path("/home/badge")).cookie(access(adminUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(3));
        mockMvc.perform(get(path("/home/badge")).cookie(access(memberUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(0));
    }

    @Test
    @DisplayName("API 139: 부원은 할 일 수, 임원은 할 일 수 + 처리 대기 합계")
    void badgeCountsTodos() throws Exception {
        long applicant = extraMember("MEMBER", "ACTIVE", "REGULAR");
        long performance = performance("ACTIVE");
        policy(performance, "application_state = 'OPEN'");
        participation(performance, applicant, "APPLIED");
        team(performance, "SUBMITTED");

        // 부원: 참여 신청 1건
        mockMvc.perform(get(path("/home/badge")).cookie(access(memberUser)))
                .andExpect(jsonPath("$.data.count").value(1));
        // 임원: 참여 신청 1건 + 처리 대기(참여 요청 1 + 팀 검토 1)
        mockMvc.perform(get(path("/home/badge")).cookie(access(adminUser)))
                .andExpect(jsonPath("$.data.count").value(3));
    }

    @Test
    @DisplayName("API 139: 소속이 없으면 404")
    void notMember() throws Exception {
        mockMvc.perform(get(path("/home/badge")).cookie(access(user()))).andExpect(status().isNotFound());
    }
}
