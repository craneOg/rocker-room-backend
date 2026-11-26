package com.crane.bandwebbackend.domain.evaluation.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crane.bandwebbackend.domain.song.SongApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// #100 개별 가불·투표 {items}, #117 가불 집계 키, #138 song-17 후보 범위 밖 422
class EvaluationSpecApiTest extends SongApiTestSupport {

    @Test
    @DisplayName("API-090·095 개별 가불·투표 조회는 {items: [...]} 객체")
    void listsAreWrappedInItems() throws Exception {
        song(100, "곡A", null, "PROPOSED");
        send(put(BASE + "/songs/100/playability/me/1"), MEMBER, "{\"answer\":\"POSSIBLE\"}")
                .andExpect(status().isOk());
        send(put(BASE + "/songs/100/votes/me"), MEMBER, "{\"score\":1}").andExpect(status().isOk());

        send(get(BASE + "/songs/100/playability"), ADMIN, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].answer").value("POSSIBLE"));
        send(get(BASE + "/songs/100/votes"), ADMIN, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(1));
    }

    @Test
    @DisplayName("API-089 playabilityByPart 키는 명세의 POSSIBLE·IMPOSSIBLE·UNDECIDED")
    void playabilityByPartUsesSpecKeys() throws Exception {
        song(100, "곡A", null, "PROPOSED");
        send(put(BASE + "/songs/100/playability/me/1"), MEMBER, "{\"answer\":\"POSSIBLE\"}")
                .andExpect(status().isOk());

        send(get(BASE + "/songs/100/evaluations"), ADMIN, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.playabilityByPart[0].POSSIBLE").value(1))
                .andExpect(jsonPath("$.data.playabilityByPart[0].IMPOSSIBLE").value(0))
                .andExpect(jsonPath("$.data.playabilityByPart[0].UNDECIDED").value(0))
                .andExpect(jsonPath("$.data.playabilityByPart[0].possible").doesNotExist());
    }

    @Test
    @DisplayName("V-02 후보 범위 팀 밖 응답은 403이 아니라 422 RULE_VIOLATION")
    void outOfCandidateScopeIs422() throws Exception {
        team(100, "다른팀", "APPROVED", 2);
        participant(100, 3, "APPROVED", "APPLICATION");
        song(100, "팀곡", null, "PROPOSED");
        jdbc.update("UPDATE song SET candidate_team_id = 100 WHERE id = 100");

        // 부원B(참여자 100)는 팀 100의 팀원이 아님
        send(put(BASE + "/songs/100/playability/me/1"), MEMBER_B, "{\"answer\":\"POSSIBLE\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("RULE_VIOLATION"))
                .andExpect(jsonPath("$.error.details[0].reason").value("OUT_OF_CANDIDATE_SCOPE"));
        send(put(BASE + "/songs/100/votes/me"), MEMBER_B, "{\"score\":1}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("RULE_VIOLATION"))
                .andExpect(jsonPath("$.error.details[0].reason").value("OUT_OF_CANDIDATE_SCOPE"));
    }
}
