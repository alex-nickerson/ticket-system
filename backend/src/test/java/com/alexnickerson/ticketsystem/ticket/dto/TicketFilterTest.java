package com.alexnickerson.ticketsystem.ticket.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.alexnickerson.ticketsystem.ticket.Priority;
import com.alexnickerson.ticketsystem.ticket.SlaState;
import com.alexnickerson.ticketsystem.ticket.TicketStatus;
import org.junit.jupiter.api.Test;

class TicketFilterTest {

	@Test
	void restrictingToOwnTicketsKeepsEveryOtherFilter() {
		TicketFilter asked = new TicketFilter(
				TicketStatus.NEW, Priority.P2, 7L, 3L, SlaState.AT_RISK, null, "printer");

		TicketFilter restricted = asked.restrictedToOwnTickets();

		assertThat(restricted.mine()).isTrue();
		assertThat(restricted.status()).isEqualTo(TicketStatus.NEW);
		assertThat(restricted.priority()).isEqualTo(Priority.P2);
		assertThat(restricted.assignee()).isEqualTo(7L);
		assertThat(restricted.category()).isEqualTo(3L);
		assertThat(restricted.slaState()).isEqualTo(SlaState.AT_RISK);
		assertThat(restricted.q()).isEqualTo("printer");
	}

	@Test
	void restrictingOverridesAnExplicitMineFalse() {
		// A requester cannot widen their own list by asking for mine=false.
		TicketFilter asked = new TicketFilter(null, null, null, null, null, false, null);

		assertThat(asked.restrictedToOwnTickets().mine()).isTrue();
	}
}
