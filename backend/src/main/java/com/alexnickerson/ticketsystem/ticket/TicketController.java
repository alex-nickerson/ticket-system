package com.alexnickerson.ticketsystem.ticket;

import com.alexnickerson.ticketsystem.common.PageResponse;
import com.alexnickerson.ticketsystem.ticket.dto.CreateTicketRequest;
import com.alexnickerson.ticketsystem.ticket.dto.TicketFilter;
import com.alexnickerson.ticketsystem.ticket.dto.TicketResponse;
import com.alexnickerson.ticketsystem.ticket.dto.TicketSummaryResponse;
import com.alexnickerson.ticketsystem.ticket.dto.UpdateTicketRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Thin by design: each method validates, delegates to {@link TicketService} and
 * maps the result onto HTTP. No rules live here.
 */
@RestController
@RequestMapping("/api/tickets")
@Tag(name = "Tickets", description = "Raise, browse and amend tickets")
public class TicketController {

	private final TicketService ticketService;

	public TicketController(TicketService ticketService) {
		this.ticketService = ticketService;
	}

	@PostMapping
	@Operation(
			summary = "Raise a ticket",
			description = "The requester is the acting user, not a field in the body.")
	@ApiResponse(responseCode = "201", description = "Created")
	@ApiResponse(responseCode = "400", description = "Validation failed or unusable category", content = {})
	public ResponseEntity<TicketResponse> create(@Valid @RequestBody CreateTicketRequest request) {
		TicketResponse created = ticketService.create(request);
		return ResponseEntity.created(URI.create("/api/tickets/" + created.id())).body(created);
	}

	@GetMapping
	@Operation(summary = "List tickets", description = "All filters are optional and combine with AND.")
	public PageResponse<TicketSummaryResponse> list(
			@ParameterObject @ModelAttribute TicketFilter filter,
			@ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
			Pageable pageable) {
		return ticketService.list(filter, pageable);
	}

	@GetMapping("/{id}")
	@Operation(summary = "Fetch one ticket")
	@ApiResponse(responseCode = "404", description = "No such ticket", content = {})
	public TicketResponse get(@PathVariable Long id) {
		return ticketService.get(id);
	}

	@PatchMapping("/{id}")
	@Operation(
			summary = "Amend a ticket",
			description = "Only the supplied fields change. Status and assignee are not editable here; "
					+ "they get their own endpoints in a later milestone.")
	@ApiResponse(responseCode = "404", description = "No such ticket", content = {})
	@ApiResponse(responseCode = "409", description = "Someone else changed the ticket first", content = {})
	public TicketResponse update(@PathVariable Long id, @Valid @RequestBody UpdateTicketRequest request) {
		return ticketService.update(id, request);
	}
}
