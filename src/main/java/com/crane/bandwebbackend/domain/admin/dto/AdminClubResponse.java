package com.crane.bandwebbackend.domain.admin.dto;

import com.crane.bandwebbackend.global.serialization.IdString;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.Getter;

// API 118 항목. 가입 코드는 내려주지 않는다
@Getter
@Builder
public class AdminClubResponse {

    @IdString
    private final Long id;

    private final String name;
    private final String description;
    private final String clubType;
    private final String status;

    @Getter(onMethod_ = @JsonProperty("isSearchable"))
    private final boolean searchable;

    @IdString
    private final Long createdByUserId;

    private final long activeMemberCount;
    private final long adminCount;
    private final OffsetDateTime lastActiveAt;
    private final long ongoingPerformanceCount;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;
}
