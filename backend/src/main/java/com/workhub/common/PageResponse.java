package com.workhub.common;

import java.util.List;

import org.springframework.data.domain.Page;

public record PageResponse<T>(List<T> items, int page, int size, long total, int totalPages) {

	public static <S, T> PageResponse<T> of(Page<S> page, List<T> items) {
		return new PageResponse<>(items, page.getNumber(), page.getSize(), page.getTotalElements(),
				page.getTotalPages());
	}

}
