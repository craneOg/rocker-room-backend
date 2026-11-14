package com.crane.bandwebbackend.domain.performance.dto;

import com.crane.bandwebbackend.domain.performance.entity.PerformancePreset;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode
public class PerformanceCreateRequest {
    @JsonProperty("title")
    @NotBlank(message = "공연 제목은 필수입니다.")
    @Size(max = 200, message = "공연 제목은 200자 이하여야 합니다.")
    private String title;

    @JsonProperty("performDate")
    private LocalDate performDate;

    @JsonProperty("location")
    @Size(max = 200, message = "공연 장소는 200자 이하여야 합니다.")
    private String location;

    @JsonProperty("preset")
    private PerformancePreset preset;

    @JsonProperty("policy")
    private JsonNode policy;
}
