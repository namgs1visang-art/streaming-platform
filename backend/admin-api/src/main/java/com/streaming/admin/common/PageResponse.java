package com.streaming.admin.common;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/** 목록 응답 (Spring Data Page 를 그대로 직렬화하지 않도록 단순화) */
public record PageResponse<T>(List<T> items, long total, int page, int size) {

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
                page.getTotalElements(), page.getNumber(), page.getSize());
    }
}
