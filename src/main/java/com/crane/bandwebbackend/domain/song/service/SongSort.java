package com.crane.bandwebbackend.domain.song.service;

import com.crane.bandwebbackend.global.error.BusinessException;
import com.crane.bandwebbackend.global.error.ErrorCode;
import java.util.Arrays;

// API-075 정렬 허용 목록. 사용자 입력을 그대로 정렬 키로 쓰지 않음
public enum SongSort {
    CREATED_AT_DESC("createdAt,desc"),
    SETLIST_ORDER_ASC("setlistOrder,asc"),
    VOTE_COUNT_DESC("voteCount,desc"),
    POSSIBLE_COUNT_DESC("possibleCount,desc"),
    TITLE_ASC("title,asc");

    private final String param;

    SongSort(String param) {
        this.param = param;
    }

    public static SongSort parse(String value) {
        if (value == null || value.isBlank()) {
            return CREATED_AT_DESC;
        }
        return Arrays.stream(values())
                .filter(s -> s.param.equals(value.replace(" ", "")))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "INVALID_SORT", "지원하지 않는 정렬입니다."));
    }
}
