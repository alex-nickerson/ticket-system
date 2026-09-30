package com.alexnickerson.ticketsystem.ticket.dto;

import com.alexnickerson.ticketsystem.ticket.Priority;
import com.alexnickerson.ticketsystem.ticket.SlaState;
import com.alexnickerson.ticketsystem.ticket.Ticket;
import com.alexnickerson.ticketsystem.ticket.TicketStatus;
import com.alexnickerson.ticketsystem.ticket.TicketType;
import java.time.Instant;

/**
 * A ticket as it appears in a list. Deliberately omits the description, which
 * can be 10,000 characters and is never shown in a list view.
 */
public record TicketSummaryResponse(
		Long id,
		String number,
		TicketType type,
		String title,
		CategorySummary category,
		Priority priority,
		TicketStatus status,
		UserSummary requester,
		UserSummary assignee,
		Instant createdAt,
		SlaState slaState) {

	public static TicketSummaryResponse from(Ticket ticket) {
		return new TicketSummaryResponse(
				ticket.getId(),
				ticket.getNumber(),
				ticket.getType(),
				ticket.getTitle(),
				CategorySummary.from(ticket.getCategory()),
				ticket.getPriority(),
				ticket.getStatus(),
				UserSummary.from(ticket.getRequester()),
				UserSummary.from(ticket.getAssignee()),
				ticket.getCreatedAt(),
				ticket.getSlaState());
	}
}
