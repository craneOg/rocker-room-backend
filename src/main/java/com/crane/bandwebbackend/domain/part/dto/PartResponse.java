package com.crane.bandwebbackend.domain.part.dto;

import com.crane.bandwebbackend.domain.part.entity.Part;
import com.crane.bandwebbackend.global.response.RevisionTag;
import com.fasterxml.jackson.annotation.JsonInclude;
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

    // API-033 목록 응답에만 담는다(API-034·035 응답에는 없음)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final Long memberCount;

    public static PartResponse from(Part part) {
        return builderOf(part).build();
    }

    public static PartResponse from(Part part, long memberCount) {
        return builderOf(part).memberCount(memberCount).build();
    }

    private static PartResponseBuilder builderOf(Part part) {
        return PartResponse.builder()
                .id(part.getId().toString())
                .clubId(part.getClub().getId().toString())
                .name(part.getName())
                .displayOrder(part.getDisplayOrder())
                .active(part.isActive())
                .revision(RevisionTag.from("part", part.getId(), part.getUpdatedAt()));
    }

    @JsonProperty("isActive")
    public boolean isActive() {
        return active;
    }
}
