package com.alexnickerson.ticketsystem.ticket.dto;

import com.alexnickerson.ticketsystem.ticket.Priority;
import com.alexnickerson.ticketsystem.ticket.TicketType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of POST /api/tickets.
 *
 * <p>The requester is not a field: it is the acting user. Letting a client name
 * the requester would mean anyone could file a ticket as someone else.
 */
public record CreateTicketRequest(
		@Schema(description = "Whether something is broken or something is being requested")
		@NotNull TicketType type,

		@Schema(example = "Laptop will not power on")
		@NotBlank @Size(max = 200) String title,

		@NotBlank @Size(max = 10_000) String description,

		@Schema(description = "Id of an active category")
		@NotNull Long categoryId,

		@Schema(description = "P1 is most urgent")
		@NotNull Priority priority) {}
