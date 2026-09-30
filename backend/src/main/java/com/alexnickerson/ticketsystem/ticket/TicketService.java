package com.alexnickerson.ticketsystem.ticket;

import com.alexnickerson.ticketsystem.category.Category;
import com.alexnickerson.ticketsystem.category.CategoryRepository;
import com.alexnickerson.ticketsystem.common.BadRequestException;
import com.alexnickerson.ticketsystem.common.NotFoundException;
import com.alexnickerson.ticketsystem.common.PageResponse;
import com.alexnickerson.ticketsystem.ticket.dto.CreateTicketRequest;
import com.alexnickerson.ticketsystem.ticket.dto.TicketFilter;
import com.alexnickerson.ticketsystem.ticket.dto.TicketResponse;
import com.alexnickerson.ticketsystem.ticket.dto.TicketSummaryResponse;
import com.alexnickerson.ticketsystem.ticket.dto.UpdateTicketRequest;
import com.alexnickerson.ticketsystem.user.ActingUserProvider;
import com.alexnickerson.ticketsystem.user.User;
import java.time.Clock;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ticket use cases. The controller does nothing but translate HTTP to these
 * calls, and the repository does nothing but persistence, so this is the only
 * place ticket rules live.
 *
 * <p>Note what is <em>not</em> here yet: no authorization (milestone 2), no
 * status transitions or SLA due dates (milestone 3). Tickets are created NEW and
 * stay there.
 */
@Service
public class TicketService {

	private final TicketRepository tickets;
	private final CategoryRepository categories;
	private final TicketNumberGenerator numberGenerator;
	private final ActingUserProvider actingUserProvider;
	private final Clock clock;

	public TicketService(
			TicketRepository tickets,
			CategoryRepository categories,
			TicketNumberGenerator numberGenerator,
			ActingUserProvider actingUserProvider,
			Clock clock) {
		this.tickets = tickets;
		this.categories = categories;
		this.numberGenerator = numberGenerator;
		this.actingUserProvider = actingUserProvider;
		this.clock = clock;
	}

	@Transactional
	public TicketResponse create(CreateTicketRequest request) {
		User requester = actingUserProvider.current();
		Category category = requireSelectableCategory(request.categoryId());

		Ticket ticket = new Ticket(
				numberGenerator.next(request.type()),
				request.type(),
				request.title().trim(),
				request.description().trim(),
				category,
				request.priority(),
				requester,
				clock.instant());

		return TicketResponse.from(tickets.save(ticket));
	}

	@Transactional(readOnly = true)
	public TicketResponse get(Long id) {
		Ticket ticket = tickets.findByIdWithAssociations(id)
				.orElseThrow(() -> new NotFoundException("Ticket", id));
		return TicketResponse.from(ticket);
	}

	@Transactional(readOnly = true)
	public PageResponse<TicketSummaryResponse> list(TicketFilter filter, Pageable pageable) {
		// The acting user is only needed for ?mine=true, but resolving it here keeps
		// the specification builder free of any notion of who is asking.
		Long actingUserId = Boolean.TRUE.equals(filter.mine())
				? actingUserProvider.current().getId()
				: null;

		return PageResponse.from(
				tickets.findAll(TicketSpecifications.matching(filter, actingUserId), pageable),
				TicketSummaryResponse::from);
	}

	@Transactional
	public TicketResponse update(Long id, UpdateTicketRequest request) {
		Ticket ticket = tickets.findByIdWithAssociations(id)
				.orElseThrow(() -> new NotFoundException("Ticket", id));

		// A null field means "not supplied", so each change is applied only when
		// the client actually sent it.
		if (request.title() != null) {
			ticket.changeTitle(request.title().trim());
		}
		if (request.description() != null) {
			ticket.changeDescription(request.description().trim());
		}
		if (request.categoryId() != null) {
			ticket.changeCategory(requireSelectableCategory(request.categoryId()));
		}
		if (request.priority() != null) {
			ticket.changePriority(request.priority());
		}

		// The ticket is a managed entity, so the changes above need no explicit
		// save(). The flush is deliberate though: @Version is only incremented when
		// the UPDATE is issued, so without it the response would carry the version
		// the ticket had *before* this edit. A client using that value as its
		// optimistic-locking token would then be one behind on its next write.
		tickets.flush();

		return TicketResponse.from(ticket);
	}

	/**
	 * A deactivated category still exists and is still referenced by old tickets,
	 * but must not be chosen for new ones — so this is a bad request rather than a
	 * 404.
	 */
	private Category requireSelectableCategory(Long categoryId) {
		Category category = categories.findById(categoryId)
				.orElseThrow(() -> new BadRequestException(
						"categoryId %d does not refer to an existing category.".formatted(categoryId)));

		if (!category.isActive()) {
			throw new BadRequestException(
					"Category '%s' is not active and cannot be used.".formatted(category.getName()));
		}
		return category;
	}
}
