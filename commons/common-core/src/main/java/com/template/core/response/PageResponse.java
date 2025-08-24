package com.template.core.response;

import lombok.Getter;

import java.util.List;

/**
 * Lightweight pagination DTO.
 * Contains enough information for most API pagination use cases.
 */
@Getter
public final class PageResponse<T> {
    private final List<T> content;
    private final int page;
    private final int size;
    private final long totalElements;
    private final int totalPages;
    private final boolean hasNext;

    public PageResponse(List<T> content, int page, int size, long totalElements) {
        this.content = List.copyOf(content);
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = size > 0 ? (int) Math.ceil((double) totalElements / (double) size) : 1;
        this.hasNext = (long) (page + 1) * size < totalElements;
    }

    public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
        return new PageResponse<>(content, page, size, totalElements);
    }
}
