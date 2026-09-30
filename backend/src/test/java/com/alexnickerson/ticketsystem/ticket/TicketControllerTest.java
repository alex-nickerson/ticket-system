package com.alexnickerson.ticketsystem.ticket;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alexnickerson.ticketsystem.common.BadRequestException;
import com.alexnickerson.ticketsystem.common.NotFoundException;
import com.alexnickerson.ticketsystem.common.PageResponse;
import com.alexnickerson.ticketsystem.ticket.dto.CategorySummary;
import com.alexnickerson.ticketsystem.ticket.dto.TicketResponse;
import com.alexnickerson.ticketsystem.ticket.dto.TicketSummaryResponse;
import com.alexnickerson.ticketsystem.ticket.dto.UserSummary;
import com.alexnickerson.ticketsystem.user.ActingUserException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-layer tests: request binding, Bean Validation and the RFC 7807 error
 * shape. The service is mocked, so nothing here touches a database — these tests
 * are about the HTTP contract only.
 */
@WebMvcTest(TicketController.class)
class TicketControllerTest {

	private static final String PROBLEM_JSON = "application/problem+json";

	@Autowired private MockMvc mockMvc;

	@MockitoBean private TicketService ticketService;

	private static TicketResponse sampleTicket() {
		return new TicketResponse(
				42L,
				"INC-000042",
				TicketType.INCIDENT,
				"Laptop will not boot",
				"Nothing appears on screen",
				new CategorySummary(1L, "Hardware"),
				Priority.P2,
				TicketStatus.NEW,
				new UserSummary(1L, "Dev Requester", "requester@example.test"),
				null,
				Instant.parse("2026-03-01T12:00:00Z"),
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				0,
				0L);
	}

	@Test
	void createReturns201WithALocationHeader() throws Exception {
		when(ticketService.create(any())).thenReturn(sampleTicket());

		mockMvc.perform(post("/api/tickets")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "type": "INCIDENT",
								  "title": "Laptop will not boot",
								  "description": "Nothing appears on screen",
								  "categoryId": 1,
								  "priority": "P2"
								}
								"""))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/api/tickets/42"))
				.andExpect(jsonPath("$.number").value("INC-000042"))
				.andExpect(jsonPath("$.status").value("NEW"));
	}

	@Test
	void createRejectsAMissingTitleWithFieldLevelProblemDetails() throws Exception {
		mockMvc.perform(post("/api/tickets")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "type": "INCIDENT",
								  "description": "Nothing appears on screen",
								  "categoryId": 1,
								  "priority": "P2"
								}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.title").value("Validation failed"))
				.andExpect(jsonPath("$.errors[*].field").value(org.hamcrest.Matchers.hasItem("title")));

		verify(ticketService, never()).create(any());
	}

	@Test
	void createRejectsABlankTitle() throws Exception {
		mockMvc.perform(post("/api/tickets")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "type": "INCIDENT",
								  "title": "   ",
								  "description": "Nothing appears on screen",
								  "categoryId": 1,
								  "priority": "P2"
								}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[*].field").value(org.hamcrest.Matchers.hasItem("title")));
	}

	@Test
	void createRejectsATitleOverTheLengthLimit() throws Exception {
		String tooLong = "x".repeat(201);

		mockMvc.perform(post("/api/tickets")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "type": "INCIDENT",
								  "title": "%s",
								  "description": "Body",
								  "categoryId": 1,
								  "priority": "P2"
								}
								""".formatted(tooLong)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[*].field").value(org.hamcrest.Matchers.hasItem("title")));
	}

	@Test
	void createRejectsAnUnknownPriorityAsAProblemDetail() throws Exception {
		mockMvc.perform(post("/api/tickets")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "type": "INCIDENT",
								  "title": "Laptop will not boot",
								  "description": "Body",
								  "categoryId": 1,
								  "priority": "URGENT"
								}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
	}

	@Test
	void createSurfacesAnUnusableCategoryAsA400ProblemDetail() throws Exception {
		when(ticketService.create(any()))
				.thenThrow(new BadRequestException("categoryId 99 does not refer to an existing category."));

		mockMvc.perform(post("/api/tickets")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "type": "INCIDENT",
								  "title": "Laptop will not boot",
								  "description": "Body",
								  "categoryId": 99,
								  "priority": "P2"
								}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.title").value("Invalid request"))
				.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("99")));
	}

	@Test
	void createWithoutAnActingUserIsA400ProblemDetail() throws Exception {
		when(ticketService.create(any()))
				.thenThrow(new ActingUserException("The X-Acting-User header is required."));

		mockMvc.perform(post("/api/tickets")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "type": "INCIDENT",
								  "title": "Laptop will not boot",
								  "description": "Body",
								  "categoryId": 1,
								  "priority": "P2"
								}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.title").value("Acting user could not be determined"));
	}

	@Test
	void getReturnsTheTicket() throws Exception {
		when(ticketService.get(42L)).thenReturn(sampleTicket());

		mockMvc.perform(get("/api/tickets/42"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.number").value("INC-000042"))
				.andExpect(jsonPath("$.requester.displayName").value("Dev Requester"))
				.andExpect(jsonPath("$.assignee").doesNotExist());
	}

	@Test
	void getReturns404ProblemDetailForAnUnknownTicket() throws Exception {
		when(ticketService.get(404L)).thenThrow(new NotFoundException("Ticket", 404L));

		mockMvc.perform(get("/api/tickets/404"))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.title").value("Not found"))
				.andExpect(jsonPath("$.status").value(404));
	}

	@Test
	void listReturnsThePagedShape() throws Exception {
		TicketSummaryResponse summary = new TicketSummaryResponse(
				42L,
				"INC-000042",
				TicketType.INCIDENT,
				"Laptop will not boot",
				new CategorySummary(1L, "Hardware"),
				Priority.P2,
				TicketStatus.NEW,
				new UserSummary(1L, "Dev Requester", "requester@example.test"),
				null,
				Instant.parse("2026-03-01T12:00:00Z"),
				null);
		when(ticketService.list(any(), any()))
				.thenReturn(new PageResponse<>(List.of(summary), 0, 20, 1, 1));

		mockMvc.perform(get("/api/tickets"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].number").value("INC-000042"))
				.andExpect(jsonPath("$.content[0].description").doesNotExist())
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(20))
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.totalPages").value(1));
	}

	@Test
	void patchRejectsAnEmptyBody() throws Exception {
		mockMvc.perform(patch("/api/tickets/42")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[*].message")
						.value(org.hamcrest.Matchers.hasItem("at least one field must be provided")));

		verify(ticketService, never()).update(any(), any());
	}

	@Test
	void patchRejectsABlankSuppliedTitle() throws Exception {
		mockMvc.perform(patch("/api/tickets/42")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "title": "   " }
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[*].message")
						.value(org.hamcrest.Matchers.hasItem("provided text fields must not be blank")));
	}

	@Test
	void patchAppliesASupportedChange() throws Exception {
		when(ticketService.update(eq(42L), any())).thenReturn(sampleTicket());

		mockMvc.perform(patch("/api/tickets/42")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "priority": "P1" }
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(42));
	}

	@Test
	void patchReturns404ForAnUnknownTicket() throws Exception {
		when(ticketService.update(eq(404L), any())).thenThrow(new NotFoundException("Ticket", 404L));

		mockMvc.perform(patch("/api/tickets/404")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "priority": "P1" }
								"""))
				.andExpect(status().isNotFound());
	}
}
