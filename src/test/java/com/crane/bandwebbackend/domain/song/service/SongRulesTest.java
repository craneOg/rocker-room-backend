package com.crane.bandwebbackend.domain.song.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crane.bandwebbackend.global.error.BusinessException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SongRulesTest {

    @Test
    @DisplayName("X-05 LOCKED 확정은 입력 순서에 끼워 넣고 뒤 곡을 한 칸씩 밀어 1부터 다시 매김")
    void insertAtShiftsFollowing() {
        Map<Long, Integer> orders = SongService.insertAt(List.of(10L, 11L, 12L), 99L, 2);
        assertThat(orders).containsExactly(Map.entry(10L, 1), Map.entry(99L, 2), Map.entry(11L, 3), Map.entry(12L, 4));
    }

    @Test
    @DisplayName("X-05 범위를 넘는 순서는 맨 뒤")
    void insertAtClampsToEnd() {
        assertThat(SongService.insertAt(List.of(10L), 99L, 7)).containsEntry(99L, 2);
    }

    @Test
    @DisplayName("X-06 남은 곡 순서를 1부터 빈틈 없이")
    void renumber() {
        assertThat(SongService.renumber(List.of(5L, 7L))).containsExactly(Map.entry(5L, 1), Map.entry(7L, 2));
    }

    @Test
    @DisplayName("규칙 0.5 참고 링크는 https만, 아니면 400 INVALID_URL")
    void httpsOnly() {
        assertThat(SongService.requireHttps(" https://youtu.be/x ")).isEqualTo("https://youtu.be/x");
        assertThat(SongService.requireHttps("")).isNull();
        assertThatThrownBy(() -> SongService.requireHttps("http://youtu.be/x"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getDetails().get(0).getReason())
                .isEqualTo("INVALID_URL");
    }

    @Test
    @DisplayName("API-075 정렬은 허용 목록 다섯 가지뿐, 그 밖은 400")
    void sortWhitelist() {
        assertThat(SongSort.parse(null)).isEqualTo(SongSort.CREATED_AT_DESC);
        assertThat(SongSort.parse("voteCount,desc")).isEqualTo(SongSort.VOTE_COUNT_DESC);
        assertThatThrownBy(() -> SongSort.parse("id,asc")).isInstanceOf(BusinessException.class);
    }
}
