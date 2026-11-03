package com.crane.bandwebbackend.domain.availability.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crane.bandwebbackend.global.error.BusinessException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AvailabilitySlotRulesTest {

    private static final OffsetDateTime FROM = OffsetDateTime.parse("2026-10-12T00:00:00+09:00");
    private static final OffsetDateTime TO = OffsetDateTime.parse("2026-10-19T00:00:00+09:00");

    private static OffsetDateTime[] slot(String start, String end) {
        return new OffsetDateTime[] {OffsetDateTime.parse(start), OffsetDateTime.parse(end)};
    }

    @Test
    @DisplayName("구간을 30분 슬롯(UTC)으로 펼치고 중복 제거·정렬")
    void expandsToUtcSlots() {
        List<LocalDateTime> slots = AvailabilityService.toUtcSlots(
                FROM,
                TO,
                List.of(
                        slot("2026-10-12T19:00:00+09:00", "2026-10-12T20:00:00+09:00"),
                        slot("2026-10-12T10:30:00Z", "2026-10-12T11:00:00Z")),
                null,
                null);
        assertThat(slots)
                .containsExactly(LocalDateTime.parse("2026-10-12T10:00"), LocalDateTime.parse("2026-10-12T10:30"));
    }

    @Test
    @DisplayName("이어지는 슬롯은 한 구간으로 합쳐 반환")
    void mergesContiguousSlots() {
        var merged = AvailabilityService.merge(List.of(
                LocalDateTime.parse("2026-10-12T10:00"),
                LocalDateTime.parse("2026-10-12T10:30"),
                LocalDateTime.parse("2026-10-12T12:00")));
        assertThat(merged).hasSize(2);
        assertThat(merged.get(0).getEndAt()).isEqualTo(OffsetDateTime.parse("2026-10-12T20:00:00+09:00"));
    }

    @Test
    @DisplayName("AV-02 30분 단위가 아니면 400")
    void rejectsUnaligned() {
        assertThatThrownBy(() -> AvailabilityService.toUtcSlots(
                        FROM,
                        TO,
                        List.<OffsetDateTime[]>of(slot("2026-10-12T19:15:00+09:00", "2026-10-12T20:00:00+09:00")),
                        null,
                        null))
                .isInstanceOf(BusinessException.class)
                .extracting("reason")
                .isEqualTo("SLOT_NOT_ALIGNED");
    }

    @Test
    @DisplayName("AV-03 요청 기간 밖 슬롯이면 400")
    void rejectsOutsideRange() {
        assertThatThrownBy(() -> AvailabilityService.toUtcSlots(
                        FROM,
                        TO,
                        List.<OffsetDateTime[]>of(slot("2026-10-18T23:30:00+09:00", "2026-10-19T00:30:00+09:00")),
                        null,
                        null))
                .isInstanceOf(BusinessException.class)
                .extracting("reason")
                .isEqualTo("OUTSIDE_RANGE");
    }

    @Test
    @DisplayName("합주 시즌 밖이면 400 OUTSIDE_PRACTICE_SEASON")
    void rejectsOutsideSeason() {
        assertThatThrownBy(() -> AvailabilityService.toUtcSlots(
                        FROM,
                        TO,
                        List.<OffsetDateTime[]>of(slot("2026-10-12T19:00:00+09:00", "2026-10-12T19:30:00+09:00")),
                        LocalDate.of(2026, 10, 13),
                        null))
                .isInstanceOf(BusinessException.class)
                .extracting("reason")
                .isEqualTo("OUTSIDE_PRACTICE_SEASON");
    }
}
