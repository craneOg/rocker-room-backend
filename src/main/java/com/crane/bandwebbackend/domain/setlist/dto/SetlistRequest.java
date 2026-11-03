package com.crane.bandwebbackend.domain.setlist.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// API-088: 현재 CONFIRMED 전곡을 정확히 한 번씩 {songId, order|null, version}. order NULL = 미배정
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SetlistRequest {

    // 공연 version
    @NotNull
    private Long version;

    @NotNull
    @Valid
    private List<Item> items;

    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class Item {

        @NotNull
        private Long songId;

        @Positive
        private Integer order;

        @NotNull
        private Long version;
    }
}
