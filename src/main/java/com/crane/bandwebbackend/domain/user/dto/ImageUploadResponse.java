package com.crane.bandwebbackend.domain.user.dto;

import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.Getter;

// API 129 응답. imageKey는 expiresAt 전까지 올린 본인이 같은 용도로만 연결할 수 있는 임시 키다
@Getter
@Builder
public class ImageUploadResponse {

    private final String imageKey;
    private final String previewUrl;
    private final String purpose;
    private final OffsetDateTime expiresAt;
}
