package com.alexnickerson.ticketsystem.ticket.dto;

import com.alexnickerson.ticketsystem.ticket.Priority;
import com.alexnickerson.ticketsystem.ticket.SlaState;
import com.alexnickerson.ticketsystem.ticket.TicketStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Query parameters for GET /api/tickets. Every field is optional; a null field
 * simply does not narrow the results.
 */
public record TicketFilter(
		TicketStatus status,
		Priority priority,

		@Schema(description = "Id of the assigned agent")
		Long assignee,

		@Schema(description = "Id of the category")
		Long category,

		SlaState slaState,

		@Schema(description = "Restrict to tickets raised by the acting user")
		Boolean mine,

		@Schema(description = "Free-text search across ticket number, title and description")
		String q) {

	/**
	 * The same filter, forced to the acting user's own tickets.
	 *
	 * <p>Used for requesters, who may only ever list what they raised. Every other
	 * filter is kept, so a requester can still narrow by status or search text —
	 * they just cannot widen the set.
	 */
	public TicketFilter restrictedToOwnTickets() {
		return new TicketFilter(status, priority, assignee, category, slaState, true, q);
	}
}
