package com.crane.bandwebbackend.global.response;

import java.util.List;
import lombok.Getter;

// offset 페이지. page는 0부터
@Getter
public class PageResponse<T> {

    private final List<T> content;
    private final int page;
    private final int size;
    private final long totalElements;
    private final int totalPages;

    public PageResponse(List<T> all, int page, int size) {
        int from = Math.min(page * size, all.size());
        this.content = all.subList(from, Math.min(from + size, all.size()));
        this.page = page;
        this.size = size;
        this.totalElements = all.size();
        this.totalPages = (all.size() + size - 1) / size;
    }
}
