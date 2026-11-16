package com.horizonglitch.wotv.common;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/** A page of results, in the format of api-contract-draft/README.md. */
public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

	public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
		return new PageResponse<>(page.getContent().stream().map(mapper).toList(), page.getNumber(), page.getSize(),
				page.getTotalElements(), page.getTotalPages());
	}
}
