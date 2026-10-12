package com.alexnickerson.ticketsystem.common;

/**
 * Thrown when the caller is authenticated and may see the resource, but is not
 * allowed to perform this particular action on it. Mapped to a 403.
 *
 * <p>Contrast {@link NotFoundException}, which is used where even revealing that
 * the resource exists would leak information — a requester asking for someone
 * else's ticket gets 404, not 403.
 */
public class ForbiddenException extends RuntimeException {

	public ForbiddenException(String message) {
		super(message);
	}
}
