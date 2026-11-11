package com.crane.bandwebbackend.domain.performance.dto;

import com.crane.bandwebbackend.domain.performance.entity.PerformancePreset;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record PerformanceCreateRequest(
        @NotBlank(message = "공연 제목은 필수입니다.") @Size(max = 200, message = "공연 제목은 200자 이하여야 합니다.")
        String title,

        LocalDate performDate,
        @Size(max = 200, message = "공연 장소는 200자 이하여야 합니다.") String location,
        PerformancePreset preset,
        JsonNode policy) {}
