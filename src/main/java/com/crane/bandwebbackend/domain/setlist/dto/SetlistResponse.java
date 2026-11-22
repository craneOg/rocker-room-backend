package com.crane.bandwebbackend.domain.setlist.dto;

import com.crane.bandwebbackend.global.serialization.IdString;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

// API-087 응답
@Getter
@Builder
public class SetlistResponse {

    private final List<SetlistItemResponse> items;

    @IdString
    private final Long performanceVersion;
}
