package com.crane.bandwebbackend.domain.vote.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crane.bandwebbackend.domain.song.SongApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// #137 performance-05 투표 방식 변경 경고 type
class VoteMethodWarningApiTest extends SongApiTestSupport {

    @Test
    @DisplayName("API-043 투표가 있으면 경고 type은 VOTES_DELETED")
    void warningTypeIsVotesDeleted() throws Exception {
        song(100, "곡A", null, "PROPOSED");
        jdbc.update(
                "INSERT INTO song_vote (song_id, performance_id, performance_member_id, score, submitted_by_user_id)"
                        + " VALUES (100, 1, 2, 1, 2)");

        send(
                        post(BASE + "/policy/vote-method/validate"),
                        ADMIN,
                        "{\"version\":\"" + version("performance", 1) + "\",\"voteMethod\":\"SCORE\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.warnings[0].type").value("VOTES_DELETED"))
                .andExpect(jsonPath("$.data.warnings[0].affectedCount").value(1));
    }
}
