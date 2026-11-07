package com.crane.bandwebbackend.domain.part.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;

/** PART-04. null은 상태 변경이 아니므로 허용하지 않는다. */
@Getter
public class PartStatusRequest {
    @NotBlank
    @Size(max = 128)
    private final String expectedRevision;
    @NotNull
    private final Boolean isActive;

    @JsonCreator
    public PartStatusRequest(@JsonProperty("expectedRevision") String expectedRevision,
                             @JsonProperty("isActive") Boolean isActive) {
        this.expectedRevision = expectedRevision;
        this.isActive = isActive;
    }
}
