package com.crane.bandwebbackend.domain.user.dto;

import com.crane.bandwebbackend.global.serialization.IdString;
import com.crane.bandwebbackend.global.time.KstTime;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.Getter;

// API 135·136 응답의 SuccessionNotice. ADMIN 안내는 team·performance 필드가 null, confirmedAt은 확인한 뒤에만 있다
@Getter
@Builder
public class SuccessionNoticeResponse {

    @IdString
    private final Long id;

    private final String type;

    @IdString
    private final Long clubId;

    private final String clubName;

    @IdString
    private final Long teamId;

    private final String teamName;

    @IdString
    private final Long performanceId;

    private final String performanceName;
    private final OffsetDateTime createdAt;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final OffsetDateTime confirmedAt;

    public static SuccessionNoticeResponse of(Row r) {
        return SuccessionNoticeResponse.builder()
                .id(r.getId())
                .type(r.getType())
                .clubId(r.getClubId())
                .clubName(r.getClubName())
                .teamId(r.getTeamId())
                .teamName(r.getTeamName())
                .performanceId(r.getPerformanceId())
                .performanceName(r.getPerformanceName())
                .createdAt(KstTime.toKst(r.getCreatedAt()))
                .confirmedAt(KstTime.toKst(r.getConfirmedAt()))
                .build();
    }

    // 저장소가 읽어 온 행(시각은 UTC 그대로)
    @Getter
    @Builder
    public static class Row {
        private final Long id;
        private final String type;
        private final Long clubId;
        private final String clubName;
        private final Long teamId;
        private final String teamName;
        private final Long performanceId;
        private final String performanceName;
        private final LocalDateTime createdAt;
        private final LocalDateTime confirmedAt;
    }
}
