package org.example.lifecomposer.dto;

import java.util.List;

/**
 * Milestone 7 admin console envelope. Every list endpoint answers with the same
 * shape so the shared frontend table/pager can render any dataset.
 */
public record PageResponse<T>(
        List<T> items,
        int page,
        int pageSize,
        long total,
        int totalPages
) {

    public static <T> PageResponse<T> of(List<T> items, int page, int pageSize, long total) {
        int totalPages = pageSize <= 0 ? 0 : (int) ((total + pageSize - 1) / pageSize);
        return new PageResponse<>(items, page, pageSize, total, totalPages);
    }
}
