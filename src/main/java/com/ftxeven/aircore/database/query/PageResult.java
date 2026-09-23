package com.ftxeven.aircore.database.query;

import java.util.List;

// one page of results plus pagination/header metadata (%current%, %total%, %page%, %pages%)
public record PageResult<T>(List<T> items, int page, int totalPages, long totalResults) {

    public PageResult {
        items = List.copyOf(items);
    }

    public static <T> PageResult<T> empty(int page) {
        return new PageResult<>(List.of(), Math.max(1, page), 1, 0);
    }

    public static <T> PageResult<T> of(List<T> all, int page, int pageSize) {
        int totalResults = all.size();
        if (totalResults == 0) {
            return empty(page);
        }
        int totalPages = Math.max(1, (int) Math.ceil(totalResults / (double) pageSize));
        int safePage = Math.max(1, Math.min(page, totalPages));
        int from = (safePage - 1) * pageSize;
        int to = Math.min(from + pageSize, totalResults);
        return new PageResult<>(all.subList(from, to), safePage, totalPages, totalResults);
    }
}