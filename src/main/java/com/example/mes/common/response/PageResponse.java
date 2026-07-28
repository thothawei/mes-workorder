package com.example.mes.common.response;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * 分頁回應。
 *
 * <p>不直接把 Spring 的 {@link Page} 序列化出去——它的 JSON 結構會隨 Spring 版本改變，
 * 前端跟著壞。自己定一層是穩定契約。
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
