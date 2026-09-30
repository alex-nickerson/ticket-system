package com.alexnickerson.ticketsystem.common;

/**
 * Thrown when a requested resource does not exist. Mapped to a 404 problem
 * detail by {@link ApiExceptionHandler}.
 *
 * <p>From milestone 2 this is also thrown when a user asks for a resource they
 * are not allowed to see: answering 403 would confirm that the ticket exists,
 * which leaks information to someone guessing ids.
 */
public class NotFoundException extends RuntimeException {

	public NotFoundException(String resource, Object id) {
		super("%s %s was not found".formatted(resource, id));
	}
}
