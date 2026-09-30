package com.alexnickerson.ticketsystem.ticket;

/**
 * Where a ticket stands against its SLA. Populated by the scheduled job added in
 * milestone 3, so it is null on tickets created in this milestone.
 */
public enum SlaState {
	ON_TRACK,
	AT_RISK,
	BREACHED
}
