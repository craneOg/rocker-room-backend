package com.crane.bandwebbackend.domain.setlist.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crane.bandwebbackend.domain.song.SongApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// #124 O-03 빈 번호 허용, 중복만 거부
class SetlistOrderApiTest extends SongApiTestSupport {

    private String body(int orderA, int orderB) {
        return "{\"version\":\"0\",\"items\":[{\"songId\":\"100\",\"order\":%d,\"version\":\"%d\"},{\"songId\":\"101\",\"order\":%d,\"version\":\"%d\"}]}"
                .formatted(orderA, version("song", 100), orderB, version("song", 101));
    }

    @Test
    @DisplayName("O-03 순서 [1, 3]처럼 빈 번호가 있어도 저장된다")
    void gapIsAllowed() throws Exception {
        song(100, "곡A", null, "CONFIRMED");
        song(101, "곡B", null, "CONFIRMED");

        send(put(BASE + "/setlist/order"), ADMIN, body(1, 3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[1].setlistOrder").value(3));
    }

    @Test
    @DisplayName("O-03 순서 [1, 1] 중복은 400 VALIDATION_ERROR")
    void duplicateIsRejected() throws Exception {
        song(100, "곡A", null, "CONFIRMED");
        song(101, "곡B", null, "CONFIRMED");

        send(put(BASE + "/setlist/order"), ADMIN, body(1, 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0].reason").value("INVALID_ORDER"));
    }
}
