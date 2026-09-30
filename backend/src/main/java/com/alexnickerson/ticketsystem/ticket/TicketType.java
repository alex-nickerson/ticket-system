package com.alexnickerson.ticketsystem.ticket;

/**
 * Something is broken (INCIDENT) versus something is being asked for
 * (SERVICE_REQUEST). The distinction drives the ticket number prefix and, later,
 * which workflows apply — JML requests are service requests.
 */
public enum TicketType {
	INCIDENT("INC"),
	SERVICE_REQUEST("REQ");

	private final String numberPrefix;

	TicketType(String numberPrefix) {
		this.numberPrefix = numberPrefix;
	}

	public String getNumberPrefix() {
		return numberPrefix;
	}
}
