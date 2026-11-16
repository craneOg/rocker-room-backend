package com.crane.bandwebbackend.domain.part.dto;

import com.crane.bandwebbackend.domain.part.entity.Part;
import com.crane.bandwebbackend.global.response.RevisionTag;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PartResponse {
    private final String id;
    private final String clubId;
    private final String name;
    private final int displayOrder;
    private final boolean active;
    private final String revision;

    public static PartResponse from(Part part) {
        return PartResponse.builder()
                .id(part.getId().toString())
                .clubId(part.getClub().getId().toString())
                .name(part.getName())
                .displayOrder(part.getDisplayOrder())
                .active(part.isActive())
                .revision(RevisionTag.from("part", part.getId(), part.getUpdatedAt()))
                .build();
    }

    @JsonProperty("isActive")
    public boolean isActive() {
        return active;
    }
}
