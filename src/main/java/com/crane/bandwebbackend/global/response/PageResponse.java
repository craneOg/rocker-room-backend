package com.crane.bandwebbackend.global.response;

import java.util.List;
import lombok.Getter;
import org.springframework.data.domain.Page;

// offset 페이지 응답. page는 Spring Page의 번호를 그대로 쓴다(0부터)
@Getter
public class PageResponse<T> {

    private final List<T> items;
    private final int page;
    private final int size;
    private final long totalElements;
    private final int totalPages;

    private PageResponse(List<T> items, int page, int size, long totalElements, int totalPages) {
        this.items = items;
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
    }

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
