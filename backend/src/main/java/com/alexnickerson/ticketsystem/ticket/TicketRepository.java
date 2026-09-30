package com.alexnickerson.ticketsystem.ticket;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface TicketRepository extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {

	/**
	 * Overridden purely to attach an entity graph. Without it, rendering a page of
	 * 20 tickets issues a separate query for each ticket's category, requester and
	 * assignee — the classic N+1. All three are to-one associations, so fetching
	 * them in the same query does not interfere with pagination.
	 */
	@Override
	@EntityGraph(attributePaths = {"category", "requester", "assignee"})
	Page<Ticket> findAll(org.springframework.data.jpa.domain.Specification<Ticket> spec, Pageable pageable);

	@Query("select t from Ticket t left join fetch t.category left join fetch t.requester "
			+ "left join fetch t.assignee where t.id = :id")
	Optional<Ticket> findByIdWithAssociations(Long id);

	Optional<Ticket> findByNumber(String number);
}
