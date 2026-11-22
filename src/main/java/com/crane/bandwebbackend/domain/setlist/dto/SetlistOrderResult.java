package com.crane.bandwebbackend.domain.setlist.dto;

import com.crane.bandwebbackend.domain.song.dto.SongResponse;
import com.crane.bandwebbackend.global.serialization.IdString;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

// API-088 응답
@Getter
@Builder
public class SetlistOrderResult {

    @IdString
    private final Long performanceVersion;

    private final List<SongResponse> items;
}
