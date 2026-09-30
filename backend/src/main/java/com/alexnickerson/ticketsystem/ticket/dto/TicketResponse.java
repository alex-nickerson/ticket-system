package com.alexnickerson.ticketsystem.ticket.dto;

import com.alexnickerson.ticketsystem.ticket.Priority;
import com.alexnickerson.ticketsystem.ticket.SlaState;
import com.alexnickerson.ticketsystem.ticket.Ticket;
import com.alexnickerson.ticketsystem.ticket.TicketStatus;
import com.alexnickerson.ticketsystem.ticket.TicketType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * A single ticket in full.
 *
 * <p>The SLA and lifecycle timestamps are part of the contract from now on but
 * stay null until milestone 3 populates them.
 */
public record TicketResponse(
		Long id,
		String number,
		TicketType type,
		String title,
		String description,
		CategorySummary category,
		Priority priority,
		TicketStatus status,
		UserSummary requester,
		UserSummary assignee,
		Instant createdAt,
		Instant firstRespondedAt,
		Instant resolvedAt,
		Instant closedAt,
		Instant slaResponseDueAt,
		Instant slaResolutionDueAt,
		SlaState slaState,
		Instant slaPausedAt,
		int totalPausedMinutes,

		@Schema(description = "Optimistic locking version; increments on every change")
		long version) {

	public static TicketResponse from(Ticket ticket) {
		return new TicketResponse(
				ticket.getId(),
				ticket.getNumber(),
				ticket.getType(),
				ticket.getTitle(),
				ticket.getDescription(),
				CategorySummary.from(ticket.getCategory()),
				ticket.getPriority(),
				ticket.getStatus(),
				UserSummary.from(ticket.getRequester()),
				UserSummary.from(ticket.getAssignee()),
				ticket.getCreatedAt(),
				ticket.getFirstRespondedAt(),
				ticket.getResolvedAt(),
				ticket.getClosedAt(),
				ticket.getSlaResponseDueAt(),
				ticket.getSlaResolutionDueAt(),
				ticket.getSlaState(),
				ticket.getSlaPausedAt(),
				ticket.getTotalPausedMinutes(),
				ticket.getVersion());
	}
}
