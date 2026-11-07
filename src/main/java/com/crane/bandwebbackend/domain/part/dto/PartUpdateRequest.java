package com.crane.bandwebbackend.domain.part.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

/** PART-03. name/displayOrder 중 최소 하나는 서비스가 검증한다. */
@Getter
public class PartUpdateRequest {
    @NotBlank
    @Size(max = 128)
    private final String expectedRevision;
    private final String name;
    @Min(0)
    private final Integer displayOrder;

    @JsonCreator
    public PartUpdateRequest(@JsonProperty("expectedRevision") String expectedRevision,
                             @JsonProperty("name") String name,
                             @JsonProperty("displayOrder") Integer displayOrder) {
        this.expectedRevision = expectedRevision;
        this.name = name;
        this.displayOrder = displayOrder;
    }
}
