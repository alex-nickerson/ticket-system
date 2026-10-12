package com.alexnickerson.ticketsystem.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alexnickerson.ticketsystem.TestcontainersConfiguration;
import com.alexnickerson.ticketsystem.category.CategoryRepository;
import com.alexnickerson.ticketsystem.support.DevTokens;
import com.alexnickerson.ticketsystem.support.ForeignTokens;
import com.alexnickerson.ticketsystem.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/**
 * Every endpoint, against every role, including the roles that should be turned
 * away.
 *
 * <p>This is the test that actually proves the authorization story, so it runs
 * the whole stack: real tokens, the real filter chain, a real database. A test
 * that mocked any of those could pass while the deployed application was wide
 * open.
 *
 * <p>Two conventions are asserted repeatedly and are worth stating once:
 * a caller with no usable token gets 401; a caller who is authenticated but not
 * permitted gets 403, except where even confirming the resource exists would
 * leak information, which gets 404.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
class AuthorizationMatrixTest {

	@Autowired private MockMvc mockMvc;
	@Autowired private ObjectMapper objectMapper;
	@Autowired private UserRepository users;
	@Autowired private CategoryRepository categories;

	private DevTokens devTokens;
	private String requesterAuth;
	private String agentAuth;
	private String adminAuth;
	private long hardwareId;

	@BeforeEach
	void setUp() throws Exception {
		devTokens = new DevTokens(mockMvc, objectMapper, users, categories);
		requesterAuth = devTokens.bearerForRequester();
		agentAuth = devTokens.bearerForAgent();
		adminAuth = devTokens.bearerForAdmin();
		hardwareId = devTokens.categoryId("Hardware");
	}

	@Nested
	class WithoutAToken {

		@Test
		void listingTicketsIsUnauthorised() throws Exception {
			mockMvc.perform(get("/api/tickets"))
					.andExpect(status().isUnauthorized())
					.andExpect(content().contentTypeCompatibleWith("application/problem+json"))
					// UTF-8 and not the servlet default, so non-ASCII detail text
					// survives. The handler has to set this explicitly because it
					// writes the response itself.
					.andExpect(result -> org.assertj.core.api.Assertions
							.assertThat(result.getResponse().getCharacterEncoding())
							.isEqualToIgnoringCase("UTF-8"))
					.andExpect(jsonPath("$.title").value("Not authenticated"));
		}

		@Test
		void creatingATicketIsUnauthorised() throws Exception {
			mockMvc.perform(post("/api/tickets")
							.contentType(MediaType.APPLICATION_JSON)
							.content(ticketBody("Anonymous attempt")))
					.andExpect(status().isUnauthorized());
		}

		@Test
		void fetchingATicketIsUnauthorised() throws Exception {
			mockMvc.perform(get("/api/tickets/1")).andExpect(status().isUnauthorized());
		}

		@Test
		void patchingATicketIsUnauthorised() throws Exception {
			mockMvc.perform(patch("/api/tickets/1")
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"title\": \"nope\"}"))
					.andExpect(status().isUnauthorized());
		}

		@Test
		void categoriesAreUnauthorised() throws Exception {
			mockMvc.perform(get("/api/categories")).andExpect(status().isUnauthorized());
		}

		@Test
		void theCurrentUserIsUnauthorised() throws Exception {
			mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
		}

		@Test
		void listingAgentsIsUnauthorised() throws Exception {
			mockMvc.perform(get("/api/users").param("role", "AGENT"))
					.andExpect(status().isUnauthorized());
		}

		@Test
		void aGarbageTokenIsUnauthorisedRatherThanAServerError() throws Exception {
			mockMvc.perform(get("/api/tickets").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
					.andExpect(status().isUnauthorized());
		}

		@Test
		void aTokenSignedWithTheWrongKeyIsUnauthorised() throws Exception {
			// A properly formed HS256 token, signed with a key this application has
			// never seen, claiming the ADMIN role. Being well formed and ambitious
			// is not enough: the signature has to verify.
			String foreignToken = ForeignTokens.signedWithAnUnknownKey();

			mockMvc.perform(get("/api/tickets").header(HttpHeaders.AUTHORIZATION, "Bearer " + foreignToken))
					.andExpect(status().isUnauthorized());
		}

		@Test
		void healthAndDocumentationStayOpen() throws Exception {
			mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
			mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
		}

		@Test
		void theDevTokenEndpointIsReachableBecauseItHasToBe() throws Exception {
			mockMvc.perform(post("/api/dev/token")
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"userId\": %d}".formatted(devTokens.userId("agent@example.test"))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.token").isNotEmpty())
					.andExpect(jsonPath("$.role").value("AGENT"));
		}

		@Test
		void theDevTokenEndpointRejectsAnUnknownUser() throws Exception {
			mockMvc.perform(post("/api/dev/token")
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"userId\": 999999}"))
					.andExpect(status().isBadRequest());
		}
	}

	@Nested
	class AsARequester {

		@Test
		void canRaiseATicket() throws Exception {
			mockMvc.perform(post("/api/tickets")
							.header(HttpHeaders.AUTHORIZATION, requesterAuth)
							.contentType(MediaType.APPLICATION_JSON)
							.content(ticketBody("Requester can raise")))
					.andExpect(status().isCreated());
		}

		@Test
		void canSeeTheirOwnTicket() throws Exception {
			long id = createTicketAs(requesterAuth, "Mine to read");

			mockMvc.perform(get("/api/tickets/{id}", id).header(HttpHeaders.AUTHORIZATION, requesterAuth))
					.andExpect(status().isOk());
		}

		@Test
		void getsNotFoundForSomeoneElsesTicket() throws Exception {
			long agentsTicket = createTicketAs(agentAuth, "Not the requesters ticket");

			// 404, not 403: a 403 would confirm the ticket exists.
			mockMvc.perform(get("/api/tickets/{id}", agentsTicket)
							.header(HttpHeaders.AUTHORIZATION, requesterAuth))
					.andExpect(status().isNotFound());
		}

		@Test
		void listsOnlyTheirOwnTickets() throws Exception {
			createTicketAs(requesterAuth, "Requester list entry");
			createTicketAs(agentAuth, "Agent list entry");

			mockMvc.perform(get("/api/tickets")
							.header(HttpHeaders.AUTHORIZATION, requesterAuth)
							.param("size", "100"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.content[*].requester.email")
							.value(org.hamcrest.Matchers.everyItem(
									org.hamcrest.Matchers.is("requester@example.test"))));
		}

		@Test
		void cannotWidenTheListByAskingForMineFalse() throws Exception {
			createTicketAs(agentAuth, "Should stay hidden");

			// The scoping is applied regardless of what the client asks for.
			mockMvc.perform(get("/api/tickets")
							.header(HttpHeaders.AUTHORIZATION, requesterAuth)
							.param("mine", "false")
							.param("size", "100"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.content[*].requester.email")
							.value(org.hamcrest.Matchers.everyItem(
									org.hamcrest.Matchers.is("requester@example.test"))));
		}

		@Test
		void canRewordTheirOwnTicket() throws Exception {
			long id = createTicketAs(requesterAuth, "Before rewording");

			mockMvc.perform(patch("/api/tickets/{id}", id)
							.header(HttpHeaders.AUTHORIZATION, requesterAuth)
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"title\": \"After rewording\"}"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.title").value("After rewording"));
		}

		@Test
		void cannotChangePriorityOnTheirOwnTicket() throws Exception {
			long id = createTicketAs(requesterAuth, "Cannot escalate this");

			mockMvc.perform(patch("/api/tickets/{id}", id)
							.header(HttpHeaders.AUTHORIZATION, requesterAuth)
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"priority\": \"P1\"}"))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.title").value("Not permitted"));
		}

		@Test
		void cannotChangeCategoryOnTheirOwnTicket() throws Exception {
			long id = createTicketAs(requesterAuth, "Cannot recategorise this");
			long softwareId = devTokens.categoryId("Software");

			mockMvc.perform(patch("/api/tickets/{id}", id)
							.header(HttpHeaders.AUTHORIZATION, requesterAuth)
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"categoryId\": %d}".formatted(softwareId)))
					.andExpect(status().isForbidden());
		}

		@Test
		void canReadTheirOwnDetails() throws Exception {
			mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, requesterAuth))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.email").value("requester@example.test"))
					.andExpect(jsonPath("$.role").value("REQUESTER"));
		}

		@Test
		void cannotListAgents() throws Exception {
			mockMvc.perform(get("/api/users")
							.param("role", "AGENT")
							.header(HttpHeaders.AUTHORIZATION, requesterAuth))
					.andExpect(status().isForbidden());
		}

		@Test
		void canReadCategoriesBecauseRaisingATicketNeedsThem() throws Exception {
			mockMvc.perform(get("/api/categories").header(HttpHeaders.AUTHORIZATION, requesterAuth))
					.andExpect(status().isOk());
		}
	}

	@Nested
	class AsAnAgent {

		@Test
		void canSeeAnyonesTicket() throws Exception {
			long requestersTicket = createTicketAs(requesterAuth, "Agent may read this");

			mockMvc.perform(get("/api/tickets/{id}", requestersTicket)
							.header(HttpHeaders.AUTHORIZATION, agentAuth))
					.andExpect(status().isOk());
		}

		@Test
		void canChangePriorityOnSomeoneElsesTicket() throws Exception {
			long id = createTicketAs(requesterAuth, "Agent escalates this");

			mockMvc.perform(patch("/api/tickets/{id}", id)
							.header(HttpHeaders.AUTHORIZATION, agentAuth)
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"priority\": \"P1\"}"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.priority").value("P1"));
		}

		@Test
		void canListAgents() throws Exception {
			mockMvc.perform(get("/api/users")
							.param("role", "AGENT")
							.header(HttpHeaders.AUTHORIZATION, agentAuth))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$[*].email")
							.value(org.hamcrest.Matchers.hasItem("agent@example.test")));
		}

		@Test
		void stillGetsNotFoundForATicketThatDoesNotExist() throws Exception {
			mockMvc.perform(get("/api/tickets/999999").header(HttpHeaders.AUTHORIZATION, agentAuth))
					.andExpect(status().isNotFound());
		}
	}

	@Nested
	class AsAnAdmin {

		@Test
		void canSeeAnyonesTicket() throws Exception {
			long requestersTicket = createTicketAs(requesterAuth, "Admin may read this");

			mockMvc.perform(get("/api/tickets/{id}", requestersTicket)
							.header(HttpHeaders.AUTHORIZATION, adminAuth))
					.andExpect(status().isOk());
		}

		@Test
		void canListAgents() throws Exception {
			mockMvc.perform(get("/api/users")
							.param("role", "AGENT")
							.header(HttpHeaders.AUTHORIZATION, adminAuth))
					.andExpect(status().isOk());
		}

		@Test
		void seesTheirOwnRole() throws Exception {
			mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, adminAuth))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.role").value("ADMIN"));
		}
	}

	// --- helpers ---

	private long createTicketAs(String authorization, String title) throws Exception {
		String response = mockMvc.perform(post("/api/tickets")
						.header(HttpHeaders.AUTHORIZATION, authorization)
						.contentType(MediaType.APPLICATION_JSON)
						.content(ticketBody(title)))
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getContentAsString();
		return objectMapper.readTree(response).get("id").asLong();
	}

	private String ticketBody(String title) {
		return """
				{
				  "type": "INCIDENT",
				  "title": "%s",
				  "description": "Raised by an authorization test.",
				  "categoryId": %d,
				  "priority": "P3"
				}
				""".formatted(title, hardwareId);
	}
}
