package com.crane.bandwebbackend.domain.performance.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode
public class LockValidationResponse {
    @JsonProperty("hardErrors")
    private List<LockChecklistItem> hardErrors;

    @JsonProperty("warnings")
    private List<LockChecklistItem> warnings;

    @JsonProperty("normalizedSetlist")
    private List<NormalizedSong> normalizedSetlist;

    @JsonProperty("confirmed")
    private boolean confirmed;

    @JsonProperty("expiresAt")
    private OffsetDateTime expiresAt;

    @JsonProperty("versions")
    private Map<String, String> versions;

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @EqualsAndHashCode
    public static class NormalizedSong {
        @JsonProperty("songId")
        private String songId;

        @JsonProperty("order")
        private int order;
    }
}
