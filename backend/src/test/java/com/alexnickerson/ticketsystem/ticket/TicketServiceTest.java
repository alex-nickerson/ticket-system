package com.alexnickerson.ticketsystem.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alexnickerson.ticketsystem.category.Category;
import com.alexnickerson.ticketsystem.category.CategoryRepository;
import com.alexnickerson.ticketsystem.common.BadRequestException;
import com.alexnickerson.ticketsystem.common.NotFoundException;
import com.alexnickerson.ticketsystem.ticket.dto.CreateTicketRequest;
import com.alexnickerson.ticketsystem.ticket.dto.TicketResponse;
import com.alexnickerson.ticketsystem.ticket.dto.UpdateTicketRequest;
import com.alexnickerson.ticketsystem.user.ActingUserProvider;
import com.alexnickerson.ticketsystem.user.User;
import com.alexnickerson.ticketsystem.user.UserRole;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the ticket rules. The clock is fixed so that created timestamps
 * are assertable rather than "whatever now happened to be".
 */
@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

	private static final Instant NOW = Instant.parse("2026-03-01T12:00:00Z");

	@Mock private TicketRepository tickets;
	@Mock private CategoryRepository categories;
	@Mock private TicketNumberGenerator numberGenerator;
	@Mock private ActingUserProvider actingUserProvider;

	private TicketService service;
	private User requester;
	private Category hardware;

	@BeforeEach
	void setUp() {
		Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);
		service = new TicketService(tickets, categories, numberGenerator, actingUserProvider, fixedClock);
		requester = userWithId(1L, "Dev Requester", UserRole.REQUESTER);
		hardware = categoryWithId(10L, "Hardware", true);
	}

	@Test
	void createStampsTheFixedClockAndStartsInNewStatus() {
		when(actingUserProvider.current()).thenReturn(requester);
		when(categories.findById(10L)).thenReturn(Optional.of(hardware));
		when(numberGenerator.next(TicketType.INCIDENT)).thenReturn("INC-000001");
		when(tickets.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

		TicketResponse response = service.create(new CreateTicketRequest(
				TicketType.INCIDENT, "Laptop will not boot", "Nothing appears on screen", 10L, Priority.P2));

		assertThat(response.number()).isEqualTo("INC-000001");
		assertThat(response.status()).isEqualTo(TicketStatus.NEW);
		assertThat(response.createdAt()).isEqualTo(NOW);
		assertThat(response.requester().id()).isEqualTo(1L);
		assertThat(response.category().name()).isEqualTo("Hardware");
	}

	@Test
	void createUsesTheActingUserAsRequester() {
		User someoneElse = userWithId(99L, "Dev Agent", UserRole.AGENT);
		when(actingUserProvider.current()).thenReturn(someoneElse);
		when(categories.findById(10L)).thenReturn(Optional.of(hardware));
		when(numberGenerator.next(any())).thenReturn("REQ-000001");
		when(tickets.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

		TicketResponse response = service.create(new CreateTicketRequest(
				TicketType.SERVICE_REQUEST, "New mouse", "The old one has died", 10L, Priority.P4));

		assertThat(response.requester().id()).isEqualTo(99L);
	}

	@Test
	void createTrimsSurroundingWhitespace() {
		when(actingUserProvider.current()).thenReturn(requester);
		when(categories.findById(10L)).thenReturn(Optional.of(hardware));
		when(numberGenerator.next(any())).thenReturn("INC-000002");
		when(tickets.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

		service.create(new CreateTicketRequest(
				TicketType.INCIDENT, "  Padded title  ", "  Padded body  ", 10L, Priority.P3));

		ArgumentCaptor<Ticket> saved = ArgumentCaptor.forClass(Ticket.class);
		verify(tickets).save(saved.capture());
		assertThat(saved.getValue().getTitle()).isEqualTo("Padded title");
		assertThat(saved.getValue().getDescription()).isEqualTo("Padded body");
	}

	@Test
	void createRejectsAnUnknownCategory() {
		when(actingUserProvider.current()).thenReturn(requester);
		when(categories.findById(404L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.create(new CreateTicketRequest(
						TicketType.INCIDENT, "Title", "Body", 404L, Priority.P3)))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("404");

		verify(tickets, never()).save(any());
	}

	@Test
	void createRejectsADeactivatedCategory() {
		Category retired = categoryWithId(11L, "Fax machines", false);
		when(actingUserProvider.current()).thenReturn(requester);
		when(categories.findById(11L)).thenReturn(Optional.of(retired));

		assertThatThrownBy(() -> service.create(new CreateTicketRequest(
						TicketType.INCIDENT, "Title", "Body", 11L, Priority.P3)))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("Fax machines");

		verify(tickets, never()).save(any());
	}

	@Test
	void getThrowsNotFoundForAnUnknownId() {
		when(tickets.findByIdWithAssociations(404L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.get(404L)).isInstanceOf(NotFoundException.class);
	}

	@Test
	void updateFlushesSoTheReturnedVersionIsTheStoredOne() {
		when(tickets.findByIdWithAssociations(5L)).thenReturn(Optional.of(existingTicket()));

		service.update(5L, new UpdateTicketRequest("Rewritten title", null, null, null));

		verify(tickets).flush();
	}

	@Test
	void updateChangesOnlyTheFieldsThatWereSupplied() {
		when(tickets.findByIdWithAssociations(5L)).thenReturn(Optional.of(existingTicket()));

		TicketResponse response =
				service.update(5L, new UpdateTicketRequest("Rewritten title", null, null, null));

		assertThat(response.title()).isEqualTo("Rewritten title");
		assertThat(response.description()).isEqualTo("Original body");
		assertThat(response.priority()).isEqualTo(Priority.P3);
		assertThat(response.category().name()).isEqualTo("Hardware");
	}

	@Test
	void updateCanChangeCategoryAndPriorityTogether() {
		Category software = categoryWithId(12L, "Software", true);
		when(tickets.findByIdWithAssociations(5L)).thenReturn(Optional.of(existingTicket()));
		when(categories.findById(12L)).thenReturn(Optional.of(software));

		TicketResponse response = service.update(5L, new UpdateTicketRequest(null, null, 12L, Priority.P1));

		assertThat(response.category().name()).isEqualTo("Software");
		assertThat(response.priority()).isEqualTo(Priority.P1);
	}

	@Test
	void updateRejectsADeactivatedCategory() {
		Category retired = categoryWithId(13L, "Fax machines", false);
		when(tickets.findByIdWithAssociations(5L)).thenReturn(Optional.of(existingTicket()));
		when(categories.findById(13L)).thenReturn(Optional.of(retired));

		assertThatThrownBy(() -> service.update(5L, new UpdateTicketRequest(null, null, 13L, null)))
				.isInstanceOf(BadRequestException.class);
	}

	@Test
	void updateThrowsNotFoundForAnUnknownId() {
		when(tickets.findByIdWithAssociations(404L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.update(404L, new UpdateTicketRequest("x", null, null, null)))
				.isInstanceOf(NotFoundException.class);
	}

	private Ticket existingTicket() {
		return new Ticket(
				"INC-000005",
				TicketType.INCIDENT,
				"Original title",
				"Original body",
				hardware,
				Priority.P3,
				requester,
				NOW);
	}

	/*
	 * Ids are database-generated, so there is no public way to set one. Reflection
	 * is confined to these helpers rather than adding setters that production code
	 * has no reason to expose.
	 */
	private static User userWithId(Long id, String displayName, UserRole role) {
		User user = new User(displayName, displayName.toLowerCase().replace(' ', '.') + "@example.test", role);
		setField(user, "id", id);
		return user;
	}

	private static Category categoryWithId(Long id, String name, boolean active) {
		Category category = new Category(name);
		setField(category, "id", id);
		setField(category, "active", active);
		return category;
	}

	private static void setField(Object target, String fieldName, Object value) {
		try {
			Field field = target.getClass().getDeclaredField(fieldName);
			field.setAccessible(true);
			field.set(target, value);
		} catch (ReflectiveOperationException ex) {
			throw new IllegalStateException("Could not set " + fieldName, ex);
		}
	}
}
