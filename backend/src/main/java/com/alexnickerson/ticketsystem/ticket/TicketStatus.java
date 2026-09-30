package com.alexnickerson.ticketsystem.ticket;

/**
 * Lifecycle states. The rules about which transitions are legal live in the
 * state machine added in milestone 3; this milestone only ever sets NEW.
 */
public enum TicketStatus {
	NEW,
	ASSIGNED,
	IN_PROGRESS,
	ON_HOLD,
	RESOLVED,
	CLOSED,
	REOPENED
}
