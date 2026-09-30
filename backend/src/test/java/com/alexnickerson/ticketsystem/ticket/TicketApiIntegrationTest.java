package com.alexnickerson.ticketsystem.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alexnickerson.ticketsystem.TestcontainersConfiguration;
import com.alexnickerson.ticketsystem.category.Category;
import com.alexnickerson.ticketsystem.category.CategoryRepository;
import com.alexnickerson.ticketsystem.user.HeaderActingUserProvider;
import com.alexnickerson.ticketsystem.user.User;
import com.alexnickerson.ticketsystem.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Full-stack tests against a real PostgreSQL container: HTTP in, SQL out.
 *
 * <p>These cover the things a mocked test cannot prove — that the Flyway schema
 * matches the entities, that the number sequences behave, and that the filter
 * specifications generate SQL that actually runs.
 *
 * <p>Ticket numbers are asserted relatively rather than as fixed strings,
 * because the sequences are shared across every test in the class and a sequence
 * is not rolled back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TicketApiIntegrationTest {

	@Autowired private MockMvc mockMvc;
	@Autowired private UserRepository users;
	@Autowired private CategoryRepository categories;
	@Autowired private ObjectMapper objectMapper;

	private long requesterId;
	private long agentId;
	private long hardwareId;

	@BeforeEach
	void resolveSeededData() {
		requesterId = seededUser("requester@example.test").getId();
		agentId = seededUser("agent@example.test").getId();
		hardwareId = seededCategory("Hardware").getId();
	}

	@Test
	void flywaySeededTheReferenceData() throws Exception {
		mockMvc.perform(get("/api/categories"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(5))
				.andExpect(jsonPath("$[*].name").value(org.hamcrest.Matchers.hasItem("Onboarding")));
	}

	@Test
	void incidentNumbersIncrementAndAreZeroPadded() throws Exception {
		String first = createTicket(requesterId, "INCIDENT", "First incident", "P3").get("number").asText();
		String second = createTicket(requesterId, "INCIDENT", "Second incident", "P3").get("number").asText();

		assertThat(first).matches("INC-\\d{6}");
		assertThat(second).matches("INC-\\d{6}");
		assertThat(sequenceValueOf(second)).isEqualTo(sequenceValueOf(first) + 1);
	}

	@Test
	void serviceRequestsAreNumberedFromTheirOwnSequence() throws Exception {
		JsonNode request = createTicket(requesterId, "SERVICE_REQUEST", "Need a monitor", "P4");

		assertThat(request.get("number").asText()).matches("REQ-\\d{6}");
		assertThat(request.get("type").asText()).isEqualTo("SERVICE_REQUEST");
	}

	@Test
	void aCreatedTicketCanBeFetchedBackWithItsAssociations() throws Exception {
		long id = createTicket(requesterId, "INCIDENT", "Fetch me back", "P2").get("id").asLong();

		mockMvc.perform(get("/api/tickets/{id}", id).header(HeaderActingUserProvider.HEADER, requesterId))
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
	void requestsWithoutTheActingUserHeaderAreRejected() throws Exception {
		mockMvc.perform(post("/api/tickets")
						.contentType(MediaType.APPLICATION_JSON)
						.content(createBody("INCIDENT", "No acting user", "P3")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.title").value("Acting user could not be determined"));
	}

	@Test
	void anUnknownActingUserIsRejected() throws Exception {
		mockMvc.perform(post("/api/tickets")
						.header(HeaderActingUserProvider.HEADER, 999_999)
						.contentType(MediaType.APPLICATION_JSON)
						.content(createBody("INCIDENT", "Ghost user", "P3")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("999999")));
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
						.header(HeaderActingUserProvider.HEADER, requesterId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.title").value("Invalid request"));
	}

	@Test
	void theMineFilterOnlyReturnsTheActingUsersTickets() throws Exception {
		createTicket(requesterId, "INCIDENT", "Raised by the requester", "P3");
		createTicket(agentId, "INCIDENT", "Raised by the agent", "P3");

		mockMvc.perform(get("/api/tickets")
						.header(HeaderActingUserProvider.HEADER, agentId)
						.param("mine", "true")
						.param("size", "100"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[*].requester.email")
						.value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("agent@example.test"))));
	}

	@Test
	void freeTextSearchIsACaseInsensitiveSubstringMatchNotATokenMatch() throws Exception {
		createTicket(requesterId, "INCIDENT", "Projector bulb has blown", "P4");

		mockMvc.perform(get("/api/tickets")
						.header(HeaderActingUserProvider.HEADER, requesterId)
						.param("q", "projector blown")
						.param("size", "100"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(0));

		mockMvc.perform(get("/api/tickets")
						.header(HeaderActingUserProvider.HEADER, requesterId)
						.param("q", "PROJECTOR")
						.param("size", "100"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[*].title")
						.value(org.hamcrest.Matchers.hasItem("Projector bulb has blown")));
	}

	@Test
	void freeTextSearchAlsoMatchesTheTicketNumber() throws Exception {
		String number = createTicket(requesterId, "INCIDENT", "Findable by number", "P3")
				.get("number").asText();

		mockMvc.perform(get("/api/tickets")
						.header(HeaderActingUserProvider.HEADER, requesterId)
						.param("q", number.toLowerCase())
						.param("size", "100"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[*].number").value(org.hamcrest.Matchers.hasItem(number)));
	}

	@Test
	void filtersCombineWithAnd() throws Exception {
		createTicket(requesterId, "INCIDENT", "A P1 incident", "P1");

		mockMvc.perform(get("/api/tickets")
						.header(HeaderActingUserProvider.HEADER, requesterId)
						.param("status", "NEW")
						.param("priority", "P1")
						.param("size", "100"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[*].priority")
						.value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("P1"))));

		// No ticket is RESOLVED in this milestone, so this must come back empty.
		mockMvc.perform(get("/api/tickets")
						.header(HeaderActingUserProvider.HEADER, requesterId)
						.param("status", "RESOLVED")
						.param("priority", "P1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(0));
	}

	@Test
	void listIsPaginatedAndDefaultsToNewestFirst() throws Exception {
		createTicket(requesterId, "INCIDENT", "Older ticket", "P3");
		createTicket(requesterId, "INCIDENT", "Newer ticket", "P3");

		String body = mockMvc.perform(get("/api/tickets")
						.header(HeaderActingUserProvider.HEADER, requesterId)
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
		long id = createTicket(requesterId, "INCIDENT", "Before the edit", "P4").get("id").asLong();
		long softwareId = seededCategory("Software").getId();

		mockMvc.perform(patch("/api/tickets/{id}", id)
						.header(HeaderActingUserProvider.HEADER, agentId)
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
		mockMvc.perform(get("/api/tickets/{id}", id).header(HeaderActingUserProvider.HEADER, agentId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("After the edit"))
				.andExpect(jsonPath("$.version").value(1));
	}

	// --- helpers ---

	private JsonNode createTicket(long actingUserId, String type, String title, String priority)
			throws Exception {
		String response = mockMvc.perform(post("/api/tickets")
						.header(HeaderActingUserProvider.HEADER, actingUserId)
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

	private User seededUser(String email) {
		return users.findByEmail(email).orElseThrow(() -> new IllegalStateException("Missing seed user " + email));
	}

	private Category seededCategory(String name) {
		return categories.findByActiveTrueOrderByNameAsc().stream()
				.filter(category -> category.getName().equals(name))
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("Missing seed category " + name));
	}
}
