package com.alexnickerson.ticketsystem.common;

/**
 * Thrown when a request is well-formed but refers to something it may not, such
 * as a category that has been deactivated. Mapped to a 400 problem detail.
 */
public class BadRequestException extends RuntimeException {

	public BadRequestException(String message) {
		super(message);
	}
}
