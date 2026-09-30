package com.alexnickerson.ticketsystem.ticket;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

/**
 * Allocates human-readable ticket numbers such as {@code INC-000123}.
 *
 * <p>Numbers come from one database sequence per ticket type. A sequence is used
 * rather than {@code MAX(number) + 1} because two simultaneous inserts would
 * otherwise read the same maximum and collide.
 */
@Component
public class TicketNumberGenerator {

	private static final String INCIDENT_SEQUENCE = "ticket_number_incident_seq";
	private static final String SERVICE_REQUEST_SEQUENCE = "ticket_number_service_request_seq";

	private final EntityManager entityManager;

	public TicketNumberGenerator(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	public String next(TicketType type) {
		String sequence = switch (type) {
			case INCIDENT -> INCIDENT_SEQUENCE;
			case SERVICE_REQUEST -> SERVICE_REQUEST_SEQUENCE;
		};

		// The sequence name is a constant chosen above, never client input, and it
		// is still passed as a bound parameter rather than concatenated in.
		Number value = (Number) entityManager
				.createNativeQuery("select nextval(cast(:sequence as regclass))")
				.setParameter("sequence", sequence)
				.getSingleResult();

		return "%s-%06d".formatted(type.getNumberPrefix(), value.longValue());
	}
}
