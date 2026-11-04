package com.crane.bandwebbackend.global.response;

import java.util.List;
import lombok.Getter;
import org.springframework.data.domain.Page;

/** API v2 offset 목록의 공통 data 형태. 실제 페이지 필터링은 저장소에서 끝낸다. */
@Getter
public class PageResponse<T> {
    private final List<T> items;
    private final int page;
    private final int size;
    private final long totalElements;
    private final int totalPages;

    private PageResponse(Page<T> source) {
        this.items = source.getContent();
        this.page = source.getNumber();
        this.size = source.getSize();
        this.totalElements = source.getTotalElements();
        this.totalPages = source.getTotalPages();
    }

    public static <T> PageResponse<T> from(Page<T> source) {
        return new PageResponse<>(source);
    }
}
