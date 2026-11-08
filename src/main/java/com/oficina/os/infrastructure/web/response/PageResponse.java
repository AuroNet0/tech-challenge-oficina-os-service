package com.oficina.os.infrastructure.web.response;

import com.oficina.os.application.port.out.PageResult;

import java.util.List;
import java.util.function.Function;

public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

	public static <S, T> PageResponse<T> from(PageResult<S> result, Function<S, T> mapper) {
		return new PageResponse<>(result.content().stream().map(mapper).toList(),
				result.page(), result.size(), result.totalElements(), result.totalPages());
	}
}
