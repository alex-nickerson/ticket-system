package com.alexnickerson.ticketsystem.ticket;

import com.alexnickerson.ticketsystem.common.ForbiddenException;
import com.alexnickerson.ticketsystem.common.NotFoundException;
import com.alexnickerson.ticketsystem.ticket.dto.UpdateTicketRequest;
import com.alexnickerson.ticketsystem.user.User;
import com.alexnickerson.ticketsystem.user.UserRole;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Who may see and change which tickets.
 *
 * <p>These rules live in one class, away from both the controller and the
 * persistence code, so that they can be unit tested exhaustively and so there is
 * a single place to read when asking "can a requester do this?". The filter
 * chain handles coarse role checks; everything that depends on *which* ticket is
 * involved is decided here.
 */
@Component
public class TicketAccessPolicy {

	/** True for agents and admins, who work on everyone's tickets. */
	public boolean canSeeAllTickets(User actingUser) {
		return actingUser.getRole() == UserRole.AGENT || actingUser.getRole() == UserRole.ADMIN;
	}

	/**
	 * Checks that the acting user is allowed to read this ticket.
	 *
	 * <p>Failure is a {@link NotFoundException}, not a 403. Answering "forbidden"
	 * would confirm that the ticket exists, which lets someone enumerate ids to
	 * learn how much traffic the service desk handles and whose tickets exist.
	 * From the caller's point of view a ticket they may not see simply is not
	 * there.
	 */
	public void requireCanRead(User actingUser, Ticket ticket) {
		if (canSeeAllTickets(actingUser)) {
			return;
		}
		if (!isRequesterOf(actingUser, ticket)) {
			throw new NotFoundException("Ticket", ticket.getId());
		}
	}

	/**
	 * Checks that the acting user is allowed to make this particular amendment.
	 *
	 * <p>A requester may correct the wording of their own ticket. Category and
	 * priority are triage decisions: letting the person who reported a problem
	 * also declare it a P1 would make the SLA numbers meaningless, so those need
	 * an agent.
	 */
	public void requireCanUpdate(User actingUser, Ticket ticket, UpdateTicketRequest request) {
		// Read access first, so someone else's ticket is 404 rather than 403 —
		// otherwise the error itself would reveal that the ticket exists.
		requireCanRead(actingUser, ticket);

		if (canSeeAllTickets(actingUser)) {
			return;
		}

		if (request.priority() != null) {
			throw new ForbiddenException("Only an agent can change a ticket's priority.");
		}
		if (request.categoryId() != null) {
			throw new ForbiddenException("Only an agent can change a ticket's category.");
		}
	}

	private boolean isRequesterOf(User actingUser, Ticket ticket) {
		return ticket.getRequester() != null
				&& Objects.equals(ticket.getRequester().getId(), actingUser.getId());
	}
}
