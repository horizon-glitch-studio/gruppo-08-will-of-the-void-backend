package com.horizonglitch.wotv.common;

import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/** Checks the {@code page} and {@code size} query parameters: page from 0, size from 1 to 100 (default 20). */
public final class Paging {

	public static final int DEFAULT_SIZE = 20;
	public static final int MAX_SIZE = 100;

	private Paging() {
	}

	public static PageRequest of(Integer page, Integer size, Sort sort) {
		int p = page == null ? 0 : page;
		int s = size == null ? DEFAULT_SIZE : size;
		List<ApiError.FieldErrorItem> errors = new ArrayList<>();
		if (p < 0) {
			errors.add(new ApiError.FieldErrorItem("page", "PAGE_INVALID"));
		}
		if (s < 1 || s > MAX_SIZE) {
			errors.add(new ApiError.FieldErrorItem("size", "SIZE_INVALID"));
		}
		if (!errors.isEmpty()) {
			throw ApiException.validation(errors);
		}
		return PageRequest.of(p, s, sort);
	}
}
