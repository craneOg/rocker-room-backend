package com.crane.bandwebbackend.domain.user.dto;

import com.crane.bandwebbackend.global.serialization.IdString;
import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LastViewedResponse {

    @IdString
    private final Long membershipId;

    private final OffsetDateTime lastViewedAt;
}
