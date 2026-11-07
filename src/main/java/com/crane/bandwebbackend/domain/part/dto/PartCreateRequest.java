package com.crane.bandwebbackend.domain.part.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

/** PART-02. displayOrder 생략 시 DB와 같은 기본값 0을 사용한다. */
@Getter
public class PartCreateRequest {
    @NotBlank
    private final String name;
    @Min(0)
    private final Integer displayOrder;

    @JsonCreator
    public PartCreateRequest(@JsonProperty("name") String name,
                             @JsonProperty("displayOrder") Integer displayOrder) {
        this.name = name;
        this.displayOrder = displayOrder;
    }
}
