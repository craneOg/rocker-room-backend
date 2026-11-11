package com.crane.bandwebbackend.domain.performance.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record LockValidationResponse(
        List<LockChecklistItem> hardErrors,
        List<LockChecklistItem> warnings,
        List<NormalizedSong> normalizedSetlist,
        boolean confirmed,
        OffsetDateTime expiresAt,
        Map<String, String> versions) {
    public record NormalizedSong(String songId, int order) {}
}
