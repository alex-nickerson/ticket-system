package com.alexnickerson.ticketsystem.user;

/** Thrown when the acting user is missing, unparseable or unknown. */
public class ActingUserException extends RuntimeException {

	public ActingUserException(String message) {
		super(message);
	}
}
