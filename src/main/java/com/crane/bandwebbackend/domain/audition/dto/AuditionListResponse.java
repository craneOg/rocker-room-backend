package com.crane.bandwebbackend.domain.audition.dto;

import java.util.List;
import lombok.Builder;

@Builder
public record AuditionListResponse(
        List<AuditionItemResponse> items, int page, int size, long totalElements, int totalPages, long pendingCount) {}
