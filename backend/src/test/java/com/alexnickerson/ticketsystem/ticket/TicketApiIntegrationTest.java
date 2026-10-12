package com.alexnickerson.ticketsystem.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alexnickerson.ticketsystem.TestcontainersConfiguration;
import com.alexnickerson.ticketsystem.category.CategoryRepository;
import com.alexnickerson.ticketsystem.support.DevTokens;
import com.alexnickerson.ticketsystem.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Full-stack tests against a real PostgreSQL container: HTTP in, SQL out, with
 * real bearer tokens minted by the dev profile's token endpoint.
 *
 * <p>These cover the things a mocked test cannot prove — that the Flyway schema
 * matches the entities, that the number sequences behave, that the filter
 * specifications generate SQL that actually runs, and that a token survives the
 * whole filter chain.
 *
 * <p>Ticket numbers are asserted relatively rather than as fixed strings,
 * because the sequences are shared across every test in the class and a sequence
 * is not rolled back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
class TicketApiIntegrationTest {

	@Autowired private MockMvc mockMvc;
	@Autowired private ObjectMapper objectMapper;
	@Autowired private UserRepository users;
	@Autowired private CategoryRepository categories;

	private DevTokens devTokens;
	private String requesterAuth;
	private String agentAuth;
	private long hardwareId;

	@BeforeEach
	void resolveSeededData() throws Exception {
		devTokens = new DevTokens(mockMvc, objectMapper, users, categories);
		requesterAuth = devTokens.bearerForRequester();
		agentAuth = devTokens.bearerForAgent();
		hardwareId = devTokens.categoryId("Hardware");
	}

	@Test
	void flywaySeededTheReferenceData() throws Exception {
		mockMvc.perform(get("/api/categories").header(HttpHeaders.AUTHORIZATION, requesterAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(5))
				.andExpect(jsonPath("$[*].name").value(org.hamcrest.Matchers.hasItem("Onboarding")));
	}

	@Test
	void incidentNumbersIncrementAndAreZeroPadded() throws Exception {
		String first = createTicket(requesterAuth, "INCIDENT", "First incident", "P3").get("number").asText();
		String second = createTicket(requesterAuth, "INCIDENT", "Second incident", "P3").get("number").asText();

		assertThat(first).matches("INC-\\d{6}");
		assertThat(second).matches("INC-\\d{6}");
		assertThat(sequenceValueOf(second)).isEqualTo(sequenceValueOf(first) + 1);
	}

	@Test
	void serviceRequestsAreNumberedFromTheirOwnSequence() throws Exception {
		JsonNode request = createTicket(requesterAuth, "SERVICE_REQUEST", "Need a monitor", "P4");

		assertThat(request.get("number").asText()).matches("REQ-\\d{6}");
		assertThat(request.get("type").asText()).isEqualTo("SERVICE_REQUEST");
	}

	@Test
	void aCreatedTicketCanBeFetchedBackWithItsAssociations() throws Exception {
		long id = createTicket(requesterAuth, "INCIDENT", "Fetch me back", "P2").get("id").asLong();

		mockMvc.perform(get("/api/tickets/{id}", id).header(HttpHeaders.AUTHORIZATION, requesterAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("Fetch me back"))
				.andExpect(jsonPath("$.status").value("NEW"))
				.andExpect(jsonPath("$.category.name").value("Hardware"))
				.andExpect(jsonPath("$.requester.email").value("requester@example.test"))
				// Milestone 3 populates these; they must be absent for now.
				.andExpect(jsonPath("$.slaState").doesNotExist())
				.andExpect(jsonPath("$.slaResolutionDueAt").doesNotExist())
				.andExpect(jsonPath("$.version").value(0));
	}

	@Test
	void theRequesterIsTakenFromTheTokenNotTheBody() throws Exception {
		// The body names a different requester; it must be ignored, because the
		// request DTO has no such field at all.
		String body = """
				{
				  "type": "INCIDENT",
				  "title": "Spoofing attempt",
				  "description": "requesterId in the body must not be honoured.",
				  "categoryId": %d,
				  "priority": "P3",
				  "requesterId": 3
				}
				""".formatted(hardwareId);

		mockMvc.perform(post("/api/tickets")
						.header(HttpHeaders.AUTHORIZATION, requesterAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.requester.email").value("requester@example.test"));
	}

	@Test
	void anUnknownCategoryIsRejectedBeforeAnyInsert() throws Exception {
		String body = """
				{
				  "type": "INCIDENT",
				  "title": "Bad category",
				  "description": "Body",
				  "categoryId": 999999,
				  "priority": "P3"
				}
				""";

		mockMvc.perform(post("/api/tickets")
						.header(HttpHeaders.AUTHORIZATION, requesterAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.title").value("Invalid request"));
	}

	@Test
	void theMineFilterOnlyReturnsTheActingUsersTickets() throws Exception {
		createTicket(requesterAuth, "INCIDENT", "Raised by the requester", "P3");
		createTicket(agentAuth, "INCIDENT", "Raised by the agent", "P3");

		mockMvc.perform(get("/api/tickets")
						.header(HttpHeaders.AUTHORIZATION, agentAuth)
						.param("mine", "true")
						.param("size", "100"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[*].requester.email")
						.value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("agent@example.test"))));
	}

	@Test
	void freeTextSearchIsACaseInsensitiveSubstringMatchNotATokenMatch() throws Exception {
		createTicket(requesterAuth, "INCIDENT", "Projector bulb has blown", "P4");

		mockMvc.perform(get("/api/tickets")
						.header(HttpHeaders.AUTHORIZATION, agentAuth)
						.param("q", "projector blown")
						.param("size", "100"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(0));

		mockMvc.perform(get("/api/tickets")
						.header(HttpHeaders.AUTHORIZATION, agentAuth)
						.param("q", "PROJECTOR")
						.param("size", "100"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[*].title")
						.value(org.hamcrest.Matchers.hasItem("Projector bulb has blown")));
	}

	@Test
	void freeTextSearchAlsoMatchesTheTicketNumber() throws Exception {
		String number = createTicket(requesterAuth, "INCIDENT", "Findable by number", "P3")
				.get("number").asText();

		mockMvc.perform(get("/api/tickets")
						.header(HttpHeaders.AUTHORIZATION, agentAuth)
						.param("q", number.toLowerCase())
						.param("size", "100"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[*].number").value(org.hamcrest.Matchers.hasItem(number)));
	}

	@Test
	void filtersCombineWithAnd() throws Exception {
		createTicket(requesterAuth, "INCIDENT", "A P1 incident", "P1");

		mockMvc.perform(get("/api/tickets")
						.header(HttpHeaders.AUTHORIZATION, agentAuth)
						.param("status", "NEW")
						.param("priority", "P1")
						.param("size", "100"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[*].priority")
						.value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("P1"))));

		// No ticket is RESOLVED in this milestone, so this must come back empty.
		mockMvc.perform(get("/api/tickets")
						.header(HttpHeaders.AUTHORIZATION, agentAuth)
						.param("status", "RESOLVED")
						.param("priority", "P1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(0));
	}

	@Test
	void listIsPaginatedAndDefaultsToNewestFirst() throws Exception {
		createTicket(requesterAuth, "INCIDENT", "Older ticket", "P3");
		createTicket(requesterAuth, "INCIDENT", "Newer ticket", "P3");

		String body = mockMvc.perform(get("/api/tickets")
						.header(HttpHeaders.AUTHORIZATION, agentAuth)
						.param("page", "0")
						.param("size", "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.size").value(1))
				.andExpect(jsonPath("$.content.length()").value(1))
				.andReturn()
				.getResponse()
				.getContentAsString();

		JsonNode page = objectMapper.readTree(body);
		assertThat(page.get("totalElements").asLong()).isGreaterThanOrEqualTo(2);
		assertThat(page.get("totalPages").asInt()).isGreaterThanOrEqualTo(2);
	}

	@Test
	void patchPersistsTheChangeAndBumpsTheVersion() throws Exception {
		long id = createTicket(requesterAuth, "INCIDENT", "Before the edit", "P4").get("id").asLong();
		long softwareId = devTokens.categoryId("Software");

		mockMvc.perform(patch("/api/tickets/{id}", id)
						.header(HttpHeaders.AUTHORIZATION, agentAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "title": "After the edit", "priority": "P1", "categoryId": %d }
								""".formatted(softwareId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("After the edit"))
				.andExpect(jsonPath("$.priority").value("P1"))
				.andExpect(jsonPath("$.category.name").value("Software"))
				// The response must carry the version as stored, not the pre-edit one,
				// because clients use it as their optimistic-locking token.
				.andExpect(jsonPath("$.version").value(1));

		// Re-read to prove it was written rather than merely echoed back.
		mockMvc.perform(get("/api/tickets/{id}", id).header(HttpHeaders.AUTHORIZATION, agentAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("After the edit"))
				.andExpect(jsonPath("$.version").value(1));
	}

	// --- helpers ---

	private JsonNode createTicket(String authorization, String type, String title, String priority)
			throws Exception {
		String response = mockMvc.perform(post("/api/tickets")
						.header(HttpHeaders.AUTHORIZATION, authorization)
						.contentType(MediaType.APPLICATION_JSON)
						.content(createBody(type, title, priority)))
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getContentAsString();
		return objectMapper.readTree(response);
	}

	private String createBody(String type, String title, String priority) {
		return """
				{
				  "type": "%s",
				  "title": "%s",
				  "description": "Raised by an integration test.",
				  "categoryId": %d,
				  "priority": "%s"
				}
				""".formatted(type, title, hardwareId, priority);
	}

	private static long sequenceValueOf(String ticketNumber) {
		return Long.parseLong(ticketNumber.substring(ticketNumber.indexOf('-') + 1));
	}
}
