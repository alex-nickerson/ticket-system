package com.alexnickerson.ticketsystem.ticket;

import com.alexnickerson.ticketsystem.ticket.dto.TicketFilter;
import org.springframework.data.jpa.domain.Specification;

/**
 * Builds the WHERE clause for GET /api/tickets from the supplied filters.
 *
 * <p>Specifications are used rather than a hand-written query per combination of
 * filters: there are seven optional parameters, which would be well over a
 * hundred queries. Everything is expressed through the Criteria API, so every
 * value the client supplies is a bound parameter and none of it is concatenated
 * into SQL.
 */
public final class TicketSpecifications {

	private TicketSpecifications() {
		// Utility class.
	}

	public static Specification<Ticket> matching(TicketFilter filter, Long actingUserId) {
		Specification<Ticket> spec = Specification.unrestricted();

		if (filter.status() != null) {
			spec = spec.and(hasStatus(filter.status()));
		}
		if (filter.priority() != null) {
			spec = spec.and(hasPriority(filter.priority()));
		}
		if (filter.assignee() != null) {
			spec = spec.and(hasAssignee(filter.assignee()));
		}
		if (filter.category() != null) {
			spec = spec.and(hasCategory(filter.category()));
		}
		if (filter.slaState() != null) {
			spec = spec.and(hasSlaState(filter.slaState()));
		}
		if (Boolean.TRUE.equals(filter.mine())) {
			spec = spec.and(requestedBy(actingUserId));
		}
		if (filter.q() != null && !filter.q().isBlank()) {
			spec = spec.and(matchesText(filter.q()));
		}
		return spec;
	}

	private static Specification<Ticket> hasStatus(TicketStatus status) {
		return (root, query, cb) -> cb.equal(root.get("status"), status);
	}

	private static Specification<Ticket> hasPriority(Priority priority) {
		return (root, query, cb) -> cb.equal(root.get("priority"), priority);
	}

	private static Specification<Ticket> hasAssignee(Long assigneeId) {
		return (root, query, cb) -> cb.equal(root.get("assignee").get("id"), assigneeId);
	}

	private static Specification<Ticket> hasCategory(Long categoryId) {
		return (root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId);
	}

	private static Specification<Ticket> hasSlaState(SlaState slaState) {
		return (root, query, cb) -> cb.equal(root.get("slaState"), slaState);
	}

	private static Specification<Ticket> requestedBy(Long requesterId) {
		return (root, query, cb) -> cb.equal(root.get("requester").get("id"), requesterId);
	}

	/**
	 * Free-text search across number, title and description, case-insensitively.
	 *
	 * <p>This is a LIKE scan, which is fine at portfolio scale but would not be at
	 * a real service desk's volume; PostgreSQL full-text search is the next step if
	 * it ever matters.
	 */
	private static Specification<Ticket> matchesText(String text) {
		String pattern = "%" + text.toLowerCase() + "%";
		return (root, query, cb) -> cb.or(
				cb.like(cb.lower(root.get("number")), pattern),
				cb.like(cb.lower(root.get("title")), pattern),
				cb.like(cb.lower(root.get("description")), pattern));
	}
}
