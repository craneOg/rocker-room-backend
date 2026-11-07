package com.crane.bandwebbackend.domain.part.dto;

import java.util.List;
import lombok.Getter;

@Getter
public class PartListResponse {
    private final List<PartResponse> items;

    public PartListResponse(List<PartResponse> items) {
        this.items = List.copyOf(items);
    }
}
