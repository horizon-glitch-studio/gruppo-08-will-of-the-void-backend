package com.horizonglitch.wotv.payload.response;

import java.util.List;

public record ApiPage<T>(
		List<T> items,
		int page,
		int size,
		long totalItems,
		long totalPages) {
}
