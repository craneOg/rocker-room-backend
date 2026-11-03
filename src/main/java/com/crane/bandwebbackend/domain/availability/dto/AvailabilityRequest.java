package com.crane.bandwebbackend.domain.availability.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// API-101: 기간 [from, to)의 본인 슬롯을 slots로 원자적 교체(AV-03). 시작·종료는 30분 단위(AV-02)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AvailabilityRequest {

    @NotNull
    private OffsetDateTime from;

    @NotNull
    private OffsetDateTime to;

    // 빈 배열 = 기간 안의 슬롯 모두 삭제. 구간 수 상한(한 구간은 이어진 시간이라 하루 몇 개면 충분)
    @NotNull
    @Size(max = MAX_SLOTS)
    private List<@NotNull @Valid Slot> slots;

    public static final int MAX_SLOTS = 2000;

    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class Slot {

        @NotNull
        private OffsetDateTime startAt;

        @NotNull
        private OffsetDateTime endAt;
    }
}
