package com.alexnickerson.ticketsystem.common;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * The paginated list shape used by every list endpoint.
 *
 * <p>Spring Data's {@code Page} is deliberately not returned directly: its JSON
 * shape is an implementation detail that Spring itself warns is unstable, so
 * serializing it would make the API contract depend on a library's internals.
 * This record is the contract instead.
 */
public record PageResponse<T>(
		List<T> content, int page, int size, long totalElements, int totalPages) {

	public static <E, T> PageResponse<T> from(Page<E> page, java.util.function.Function<E, T> mapper) {
		return new PageResponse<>(
				page.getContent().stream().map(mapper).toList(),
				page.getNumber(),
				page.getSize(),
				page.getTotalElements(),
				page.getTotalPages());
	}
}
